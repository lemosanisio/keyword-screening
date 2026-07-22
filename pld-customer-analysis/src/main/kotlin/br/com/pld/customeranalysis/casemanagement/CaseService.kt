package br.com.pld.customeranalysis.casemanagement

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.evidence.EvidenceMatrixView
import br.com.pld.customeranalysis.evidence.EvidenceRequirementsBlockedException
import br.com.pld.customeranalysis.evidence.EvidenceService
import br.com.pld.customeranalysis.evidence.ReadinessView
import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.integration.OutboxService
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartyView
import br.com.pld.customeranalysis.timeline.TimelineEntryEntity
import br.com.pld.customeranalysis.timeline.TimelineEntryJpaRepository
import br.com.pld.customeranalysis.timeline.TimelineService
import br.com.pld.customeranalysis.timeline.TimelineView
import br.com.pld.customeranalysis.timeline.VisibilityClassification
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class CaseService(
    private val caseRepository: CaseJpaRepository,
    private val caseSourceRepository: CaseSourceJpaRepository,
    private val caseCommentRepository: CaseCommentJpaRepository,
    private val suspicionDecisionRepository: SuspicionDecisionJpaRepository,
    private val accountDecisionRepository: AccountDecisionJpaRepository,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val partyService: PartyService,
    private val timelineService: TimelineService,
    private val evidenceService: EvidenceService,
    private val outboxService: OutboxService,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun recordTransactionSignal(command: RecordTransactionSignalCaseCommand): CaseView? {
        jdbcTemplate.queryForObject(
            "SELECT pg_advisory_xact_lock(hashtext(?)) IS NULL",
            Boolean::class.java,
            "${command.partyId}:${CaseOrigin.TRANSACTION_ALERT}:$GROUPING_POLICY_VERSION",
        )
        if (caseSourceRepository.existsBySourceSystemAndSourceIdAndGroupingPolicyVersion(
                command.sourceSystem,
                command.sourceId,
                GROUPING_POLICY_VERSION,
            )
        ) {
            val source = caseSourceRepository.findBySourceSystemAndSourceIdAndGroupingPolicyVersion(
                command.sourceSystem,
                command.sourceId,
                GROUPING_POLICY_VERSION,
            ) ?: return null
            return caseRepository.findById(source.caseId).orElse(null)?.let(CaseView::from)
        }

        val now = Instant.now(clock)
        val case = command.targetCaseId?.let { caseId ->
            caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }.also {
                require(it.partyId == command.partyId) { "case party conflicts with transaction signal" }
                require(it.origin == CaseOrigin.TRANSACTION_ALERT) { "case origin conflicts with transaction signal" }
                require(it.groupingPolicyVersion == GROUPING_POLICY_VERSION) {
                    "case grouping policy conflicts with transaction signal"
                }
                it.sourceCount += 1
                it.updatedAt = now
            }
        } ?: caseRepository
            .findTopByPartyIdAndOriginAndStatusInAndGroupingPolicyVersionOrderByCreatedAtAsc(
                partyId = command.partyId,
                origin = CaseOrigin.TRANSACTION_ALERT,
                statuses = GROUPABLE_CASE_STATUSES,
                groupingPolicyVersion = GROUPING_POLICY_VERSION,
            )
            ?.also {
                it.sourceCount += 1
                it.updatedAt = now
            }
            ?: createCase(command, now)

        caseSourceRepository.save(
            CaseSourceEntity(
                id = PrefixedUlid.next("src_"),
                caseId = case.id,
                sourceSystem = command.sourceSystem,
                sourceId = command.sourceId,
                sourceType = command.sourceType,
                severity = command.severity,
                reasonCode = command.reasonCode,
                evaluationId = command.evaluationId,
                transactionId = command.transactionId,
                signalType = command.signalType,
                recommendedRoute = command.recommendedRoute,
                riskProfileVersion = command.riskProfileVersion,
                ruleMatches = objectMapper.writeValueAsString(command.ruleMatches),
                groupingPolicyVersion = GROUPING_POLICY_VERSION,
                correlationId = command.correlationId,
                causationId = command.eventId,
                attachedAt = now,
            ),
        )

        return CaseView.from(case)
    }

    @Transactional(readOnly = true)
    fun queue(): CaseQueueView = CaseQueueView(
        cases = caseRepository.findByStatusInOrderByCreatedAtAsc(ACTIVE_CASE_STATUSES).map(CaseView::from),
    )

    @Transactional(readOnly = true)
    fun get(caseId: String): CaseDetailView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        val evidenceMatrix = evidenceService.matrixForCase(case.id)
        val decisionReadiness = evidenceService.readiness(evidenceMatrix)

        return CaseDetailView(
            case = CaseView.from(case),
            party = partyService.get(case.partyId),
            sources = caseSourceRepository.findByCaseIdOrderByAttachedAtAsc(case.id)
                .map { CaseSourceView.from(it, objectMapper) },
            comments = caseCommentRepository.findByCaseIdOrderByCreatedAtAsc(case.id).map(CaseCommentView::from),
            suspicionDecisions = suspicionDecisionRepository.findByCaseIdOrderByDecisionVersionAsc(case.id)
                .map { SuspicionDecisionView.from(it, objectMapper) },
            accountDecisions = accountDecisionRepository.findByCaseIdOrderByDecisionVersionAsc(case.id)
                .map { AccountDecisionView.from(it, objectMapper) },
            evidenceMatrix = evidenceMatrix,
            decisionReadiness = decisionReadiness,
            completionReadiness = completionReadiness(case, decisionReadiness),
            timeline = timelineService.getByPartyId(case.partyId),
            availableActions = availableActions(case),
        )
    }

    @Transactional
    fun addComment(caseId: String, command: AddCaseCommentCommand): CaseCommentView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        val now = Instant.now(clock)
        val comment = caseCommentRepository.save(
            CaseCommentEntity(
                id = PrefixedUlid.next("cmt_"),
                caseId = case.id,
                partyId = case.partyId,
                body = command.body.trim(),
                createdByActorId = command.actor.id,
                createdByActorRole = command.actor.role.name,
                correlationId = command.correlationId,
                createdAt = now,
            ),
        )

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = case.partyId,
                entryType = "CASE_COMMENT_ADDED",
                businessOccurredAt = now,
                recordedAt = now,
                actorType = command.actor.role.name,
                actorId = command.actor.id,
                summaryCode = "CASE_COMMENT_ADDED",
                objectType = "CaseComment",
                objectId = comment.id,
                objectVersion = "1",
                correlationId = command.correlationId,
                causationId = case.id,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        return CaseCommentView.from(comment)
    }

    @Transactional
    fun issueSuspicionDecision(caseId: String, command: IssueSuspicionDecisionCommand): SuspicionDecisionView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        ensureVersion(case, command.expectedVersion)
        if (case.status != CaseStatus.IN_ANALYSIS) {
            throw InvalidCaseTransitionException(case.id, case.status)
        }
        ensureEvidenceReady(case)

        val previousDecision = suspicionDecisionRepository.findTopByCaseIdOrderByDecisionVersionDesc(case.id)
        val previousStatus = case.status
        val requiresApproval = requiresSecondApproval(command.decision)
        val route = decisionRoute(requiresApproval)
        val now = Instant.now(clock)
        val decision = suspicionDecisionRepository.save(
            SuspicionDecisionEntity(
                id = PrefixedUlid.next("dec_"),
                caseId = case.id,
                partyId = case.partyId,
                decision = command.decision,
                decisionVersion = (previousDecision?.decisionVersion ?: 0) + 1,
                reasonCodes = objectMapper.writeValueAsString(command.reasonCodes),
                narrative = command.narrative.trim(),
                policyVersion = command.policyVersion,
                decidedByActorId = command.actor.id,
                decidedByActorRole = command.actor.role.name,
                decidedAt = now,
                correlationId = command.correlationId,
                previousDecisionId = previousDecision?.id,
                approvalStatus = approvalStatus(requiresApproval),
            ),
        )

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = case.partyId,
                entryType = if (requiresApproval) "SUSPICION_DECISION_PENDING_APPROVAL" else "SUSPICION_DECISION_ISSUED",
                businessOccurredAt = now,
                recordedAt = now,
                actorType = command.actor.role.name,
                actorId = command.actor.id,
                summaryCode = if (requiresApproval) {
                    "SUSPICION_DECISION_${command.decision.name}_PENDING_APPROVAL"
                } else {
                    "SUSPICION_DECISION_${command.decision.name}"
                },
                objectType = "SuspicionDecision",
                objectId = decision.id,
                objectVersion = decision.decisionVersion.toString(),
                correlationId = command.correlationId,
                causationId = case.id,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        if (!requiresApproval) {
            emitSuspicionDecisionIssued(case, decision, route, command.correlationId)
        }

        case.status = if (requiresApproval) CaseStatus.PENDING_APPROVAL else CaseStatus.IN_ANALYSIS
        case.version += 1
        case.updatedAt = now
        recordCaseStatusChanged(
            case = case,
            previousStatus = previousStatus,
            actor = command.actor,
            correlationId = command.correlationId,
            now = now,
            summaryCode = if (requiresApproval) "CASE_PENDING_APPROVAL" else "SUSPICION_DECISION_REGISTERED",
        )

        return SuspicionDecisionView.from(decision, objectMapper)
    }

    @Transactional
    fun issueAccountDecision(caseId: String, command: IssueAccountDecisionCommand): AccountDecisionView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        ensureVersion(case, command.expectedVersion)
        if (case.status != CaseStatus.IN_ANALYSIS) {
            throw InvalidCaseTransitionException(case.id, case.status)
        }
        ensureEvidenceReady(case)

        val previousDecision = accountDecisionRepository.findTopByCaseIdOrderByDecisionVersionDesc(case.id)
        val previousStatus = case.status
        val requiresApproval = requiresSecondApproval(command.decision)
        val route = decisionRoute(requiresApproval)
        val now = Instant.now(clock)
        val decision = accountDecisionRepository.save(
            AccountDecisionEntity(
                id = PrefixedUlid.next("dec_"),
                caseId = case.id,
                partyId = case.partyId,
                decision = command.decision,
                decisionVersion = (previousDecision?.decisionVersion ?: 0) + 1,
                reasonCodes = objectMapper.writeValueAsString(command.reasonCodes),
                narrative = command.narrative.trim(),
                policyVersion = command.policyVersion,
                decidedByActorId = command.actor.id,
                decidedByActorRole = command.actor.role.name,
                decidedAt = now,
                correlationId = command.correlationId,
                previousDecisionId = previousDecision?.id,
                approvalStatus = approvalStatus(requiresApproval),
            ),
        )

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = case.partyId,
                entryType = if (requiresApproval) "ACCOUNT_DECISION_PENDING_APPROVAL" else "ACCOUNT_DECISION_ISSUED",
                businessOccurredAt = now,
                recordedAt = now,
                actorType = command.actor.role.name,
                actorId = command.actor.id,
                summaryCode = if (requiresApproval) {
                    "ACCOUNT_DECISION_${command.decision.name}_PENDING_APPROVAL"
                } else {
                    "ACCOUNT_DECISION_${command.decision.name}"
                },
                objectType = "AccountDecision",
                objectId = decision.id,
                objectVersion = decision.decisionVersion.toString(),
                correlationId = command.correlationId,
                causationId = case.id,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        if (!requiresApproval) {
            emitAccountDecisionIssued(case, decision, route, command.correlationId)
        }

        case.status = if (requiresApproval) CaseStatus.PENDING_APPROVAL else CaseStatus.IN_ANALYSIS
        case.version += 1
        case.updatedAt = now
        recordCaseStatusChanged(
            case = case,
            previousStatus = previousStatus,
            actor = command.actor,
            correlationId = command.correlationId,
            now = now,
            summaryCode = if (requiresApproval) "CASE_PENDING_APPROVAL" else "ACCOUNT_DECISION_REGISTERED",
        )

        return AccountDecisionView.from(decision, objectMapper)
    }

    @Transactional
    fun approvePendingDecision(caseId: String, command: ChangeCaseStatusCommand): CaseCommandResultView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        ensureVersion(case, command.expectedVersion)
        ensureStatus(case, CaseStatus.PENDING_APPROVAL)

        val previousStatus = case.status
        val now = Instant.now(clock)
        val suspicionDecision = suspicionDecisionRepository.findTopByCaseIdOrderByDecisionVersionDesc(case.id)
            ?.takeIf { it.approvalStatus == DecisionApprovalStatus.PENDING_APPROVAL }
        val accountDecision = accountDecisionRepository.findTopByCaseIdOrderByDecisionVersionDesc(case.id)
            ?.takeIf { it.approvalStatus == DecisionApprovalStatus.PENDING_APPROVAL }

        when {
            suspicionDecision != null -> {
                approveDecisionActor(suspicionDecision.decidedByActorId, command.actor.id)
                suspicionDecision.approvalStatus = DecisionApprovalStatus.APPROVED
                suspicionDecision.approvedByActorId = command.actor.id
                suspicionDecision.approvedByActorRole = command.actor.role.name
                suspicionDecision.approvedAt = now
                recordDecisionApproval(
                    case,
                    objectType = "SuspicionDecision",
                    objectId = suspicionDecision.id,
                    objectVersion = suspicionDecision.decisionVersion,
                    entryType = "SUSPICION_DECISION_APPROVED",
                    summaryCode = "SUSPICION_DECISION_${suspicionDecision.decision.name}_APPROVED",
                    actor = command.actor,
                    correlationId = command.correlationId,
                    now = now,
                )
                emitSuspicionDecisionIssued(
                    case,
                    suspicionDecision,
                    DecisionRoute.MANDATORY_SECOND_APPROVAL,
                    command.correlationId,
                )
            }
            accountDecision != null -> {
                approveDecisionActor(accountDecision.decidedByActorId, command.actor.id)
                accountDecision.approvalStatus = DecisionApprovalStatus.APPROVED
                accountDecision.approvedByActorId = command.actor.id
                accountDecision.approvedByActorRole = command.actor.role.name
                accountDecision.approvedAt = now
                recordDecisionApproval(
                    case,
                    objectType = "AccountDecision",
                    objectId = accountDecision.id,
                    objectVersion = accountDecision.decisionVersion,
                    entryType = "ACCOUNT_DECISION_APPROVED",
                    summaryCode = "ACCOUNT_DECISION_${accountDecision.decision.name}_APPROVED",
                    actor = command.actor,
                    correlationId = command.correlationId,
                    now = now,
                )
                emitAccountDecisionIssued(
                    case,
                    accountDecision,
                    DecisionRoute.MANDATORY_SECOND_APPROVAL,
                    command.correlationId,
                )
            }
            else -> throw InvalidCaseTransitionException(case.id, case.status)
        }

        case.status = CaseStatus.IN_ANALYSIS
        case.version += 1
        case.updatedAt = now
        recordCaseStatusChanged(
            case = case,
            previousStatus = previousStatus,
            actor = command.actor,
            correlationId = command.correlationId,
            now = now,
            summaryCode = "CASE_RETURNED_TO_ANALYSIS_AFTER_SECOND_APPROVAL",
        )

        return CaseCommandResultView.from(case, availableActions(case))
    }

    @Transactional
    fun complete(caseId: String, command: ChangeCaseStatusCommand): CaseCommandResultView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        ensureVersion(case, command.expectedVersion)
        ensureStatus(case, CaseStatus.IN_ANALYSIS)
        ensureEvidenceReady(case)
        ensureNoPendingDecisionApproval(case)
        ensureMinimumDecisions(case)

        val previousStatus = case.status
        val now = Instant.now(clock)
        case.status = CaseStatus.DECIDED
        case.version += 1
        case.updatedAt = now

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = case.partyId,
                entryType = "CASE_COMPLETED",
                businessOccurredAt = now,
                recordedAt = now,
                actorType = command.actor.role.name,
                actorId = command.actor.id,
                summaryCode = "CASE_COMPLETED_EXPLICITLY",
                objectType = "Case",
                objectId = case.id,
                objectVersion = case.version.toString(),
                correlationId = command.correlationId,
                causationId = null,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )
        recordCaseStatusChanged(case, previousStatus, command.actor, command.correlationId, now, "CASE_DECIDED")

        return CaseCommandResultView.from(case, availableActions(case))
    }

    @Transactional
    fun retryRequirement(caseId: String, command: RetryRequirementCommand): EvidenceMatrixView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        return evidenceService.retryRequirement(
            caseId = case.id,
            requirementId = command.requirementId,
            expectedRevision = command.expectedEvidenceRevision,
            correlationId = command.correlationId,
        )
    }

    @Transactional
    fun assign(caseId: String, command: ChangeCaseStatusCommand): CaseCommandResultView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        ensureVersion(case, command.expectedVersion)
        ensureStatus(case, CaseStatus.OPEN)
        val previousStatus = case.status
        val now = Instant.now(clock)

        case.status = CaseStatus.ASSIGNED
        case.assignedActorId = command.actor.id
        case.version += 1
        case.updatedAt = now

        recordCaseStatusChanged(case, previousStatus, command.actor, command.correlationId, now, "CASE_ASSIGNED")

        return CaseCommandResultView.from(case, availableActions(case))
    }

    @Transactional
    fun startAnalysis(caseId: String, command: ChangeCaseStatusCommand): CaseCommandResultView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        ensureVersion(case, command.expectedVersion)
        ensureStatus(case, CaseStatus.ASSIGNED)
        val previousStatus = case.status
        val now = Instant.now(clock)

        case.status = CaseStatus.IN_ANALYSIS
        case.version += 1
        case.updatedAt = now

        recordCaseStatusChanged(case, previousStatus, command.actor, command.correlationId, now, "CASE_IN_ANALYSIS")

        return CaseCommandResultView.from(case, availableActions(case))
    }

    @Transactional
    fun returnToQueue(caseId: String, command: ChangeCaseStatusCommand): CaseCommandResultView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }
        ensureVersion(case, command.expectedVersion)
        if (case.status != CaseStatus.ASSIGNED && case.status != CaseStatus.IN_ANALYSIS) {
            throw InvalidCaseTransitionException(case.id, case.status)
        }
        val previousStatus = case.status
        val now = Instant.now(clock)

        case.status = CaseStatus.OPEN
        case.assignedActorId = null
        case.version += 1
        case.updatedAt = now

        recordCaseStatusChanged(case, previousStatus, command.actor, command.correlationId, now, "CASE_RETURNED_TO_QUEUE")

        return CaseCommandResultView.from(case, availableActions(case))
    }

    private fun createCase(command: RecordTransactionSignalCaseCommand, now: Instant): CaseEntity {
        val case = caseRepository.save(
            CaseEntity(
                id = PrefixedUlid.next("cse_"),
                partyId = command.partyId,
                analysisCycleId = command.analysisCycleId,
                origin = CaseOrigin.TRANSACTION_ALERT,
                status = CaseStatus.OPEN,
                priority = priorityFrom(command.severity),
                reasonCode = command.reasonCode,
                groupingPolicyVersion = GROUPING_POLICY_VERSION,
                sourceCount = 1,
                version = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = command.partyId,
                entryType = "CASE_CREATED",
                businessOccurredAt = command.occurredAt,
                recordedAt = now,
                actorType = "SYSTEM",
                actorId = "case-management",
                summaryCode = "CASE_CREATED_FROM_TRANSACTION_SIGNAL",
                objectType = "Case",
                objectId = case.id,
                objectVersion = "1",
                correlationId = command.correlationId,
                causationId = command.eventId,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        outboxService.append(
            eventType = "CaseStatusChanged",
            aggregateType = "Case",
            aggregateId = case.id,
            payload = mapOf(
                "caseId" to case.id,
                "partyId" to case.partyId,
                "origin" to case.origin.name,
                "previousStatus" to null,
                "newStatus" to case.status.name,
                "reasonCode" to case.reasonCode,
                "correlationId" to command.correlationId,
            ),
        )

        return case
    }

    private fun recordCaseStatusChanged(
        case: CaseEntity,
        previousStatus: CaseStatus,
        actor: Actor,
        correlationId: String,
        now: Instant,
        summaryCode: String,
    ) {
        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = case.partyId,
                entryType = "CASE_STATUS_CHANGED",
                businessOccurredAt = now,
                recordedAt = now,
                actorType = actor.role.name,
                actorId = actor.id,
                summaryCode = summaryCode,
                objectType = "Case",
                objectId = case.id,
                objectVersion = case.version.toString(),
                correlationId = correlationId,
                causationId = null,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        outboxService.append(
            eventType = "CaseStatusChanged",
            aggregateType = "Case",
            aggregateId = case.id,
            payload = mapOf(
                "caseId" to case.id,
                "partyId" to case.partyId,
                "origin" to case.origin.name,
                "previousStatus" to previousStatus.name,
                "newStatus" to case.status.name,
                "assignedActorId" to case.assignedActorId,
                "reasonCode" to case.reasonCode,
                "version" to case.version,
                "correlationId" to correlationId,
            ),
        )
    }

    private fun recordDecisionApproval(
        case: CaseEntity,
        objectType: String,
        objectId: String,
        objectVersion: Int,
        entryType: String,
        summaryCode: String,
        actor: Actor,
        correlationId: String,
        now: Instant,
    ) {
        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = case.partyId,
                entryType = entryType,
                businessOccurredAt = now,
                recordedAt = now,
                actorType = actor.role.name,
                actorId = actor.id,
                summaryCode = summaryCode,
                objectType = objectType,
                objectId = objectId,
                objectVersion = objectVersion.toString(),
                correlationId = correlationId,
                causationId = case.id,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )
    }

    private fun emitSuspicionDecisionIssued(
        case: CaseEntity,
        decision: SuspicionDecisionEntity,
        route: DecisionRoute,
        correlationId: String,
    ) {
        val decisionPayload = mutableMapOf<String, Any?>(
            "decisionId" to decision.id,
            "caseId" to case.id,
            "partyId" to case.partyId,
            "decision" to decision.decision.name,
            "decisionVersion" to decision.decisionVersion,
            "route" to route.name,
            "reasonCodes" to objectMapper.readTree(decision.reasonCodes),
            "policyVersion" to decision.policyVersion,
            "analysisCycleId" to requireAnalysisCycleId(case),
            "correlationId" to correlationId,
        )
        decision.previousDecisionId?.let { decisionPayload["supersedesDecisionId"] = it }

        outboxService.append(
            eventType = "SuspicionDecisionIssued",
            aggregateType = "SuspicionDecision",
            aggregateId = decision.id,
            payload = decisionPayload,
        )
    }

    private fun emitAccountDecisionIssued(
        case: CaseEntity,
        decision: AccountDecisionEntity,
        route: DecisionRoute,
        correlationId: String,
    ) {
        val decisionPayload = mutableMapOf<String, Any?>(
            "decisionId" to decision.id,
            "caseId" to case.id,
            "partyId" to case.partyId,
            "decision" to decision.decision.name,
            "decisionVersion" to decision.decisionVersion,
            "context" to accountDecisionContext(decision.decision),
            "route" to route.name,
            "reasonCodes" to objectMapper.readTree(decision.reasonCodes),
            "policyVersion" to decision.policyVersion,
            "analysisCycleId" to requireAnalysisCycleId(case),
            "correlationId" to correlationId,
        )
        decision.previousDecisionId?.let { decisionPayload["supersedesDecisionId"] = it }

        outboxService.append(
            eventType = "AccountDecisionIssued",
            aggregateType = "AccountDecision",
            aggregateId = decision.id,
            payload = decisionPayload,
        )
    }

    private fun ensureVersion(case: CaseEntity, expectedVersion: Int) {
        if (case.version != expectedVersion) {
            throw CaseVersionConflictException(case.id, expectedVersion, case.version)
        }
    }

    private fun ensureStatus(case: CaseEntity, expectedStatus: CaseStatus) {
        if (case.status != expectedStatus) {
            throw InvalidCaseTransitionException(case.id, case.status)
        }
    }

    private fun availableActions(case: CaseEntity): List<CaseAction> = when (case.status) {
        CaseStatus.OPEN -> listOf(CaseAction.ASSIGN)
        CaseStatus.ASSIGNED -> listOf(CaseAction.START_ANALYSIS, CaseAction.RETURN_TO_QUEUE)
        CaseStatus.IN_ANALYSIS -> listOf(CaseAction.RETURN_TO_QUEUE, CaseAction.COMPLETE_CASE)
        CaseStatus.PENDING_APPROVAL -> listOf(CaseAction.APPROVE_DECISION)
        else -> emptyList()
    }

    private fun ensureNoPendingDecisionApproval(case: CaseEntity) {
        val pendingSuspicion = suspicionDecisionRepository.findTopByCaseIdOrderByDecisionVersionDesc(case.id)
            ?.approvalStatus == DecisionApprovalStatus.PENDING_APPROVAL
        val pendingAccount = accountDecisionRepository.findTopByCaseIdOrderByDecisionVersionDesc(case.id)
            ?.approvalStatus == DecisionApprovalStatus.PENDING_APPROVAL
        if (pendingSuspicion || pendingAccount) {
            throw InvalidCaseTransitionException(case.id, case.status)
        }
    }

    private fun ensureMinimumDecisions(case: CaseEntity) {
        val suspicionDecision = suspicionDecisionRepository.findTopByCaseIdOrderByDecisionVersionDesc(case.id)
        if (suspicionDecision == null || suspicionDecision.approvalStatus != DecisionApprovalStatus.APPROVED) {
            throw CaseCompletionBlockedException(case.id, listOf("SUSPICION_DECISION_REQUIRED"))
        }
    }

    private fun ensureEvidenceReady(case: CaseEntity) {
        val readiness = evidenceService.decisionReadiness(case.id)
        if (!readiness.allowed) {
            throw EvidenceRequirementsBlockedException(readiness.blockingReasons)
        }
    }

    private fun completionReadiness(case: CaseEntity, decisionReadiness: ReadinessView): ReadinessView {
        val reasons = decisionReadiness.blockingReasons.toMutableList()
        val suspicionDecision = suspicionDecisionRepository.findTopByCaseIdOrderByDecisionVersionDesc(case.id)
        if (suspicionDecision == null || suspicionDecision.approvalStatus != DecisionApprovalStatus.APPROVED) {
            reasons += "SUSPICION_DECISION_REQUIRED"
        }
        if (case.status == CaseStatus.PENDING_APPROVAL) {
            reasons += "DECISION_APPROVAL_PENDING"
        }
        return ReadinessView(allowed = reasons.isEmpty(), blockingReasons = reasons)
    }

    private fun priorityFrom(severity: String): CasePriority = when (severity) {
        "CRITICAL" -> CasePriority.CRITICAL
        "HIGH" -> CasePriority.HIGH
        "LOW" -> CasePriority.LOW
        else -> CasePriority.MEDIUM
    }

    private fun requireAnalysisCycleId(case: CaseEntity): String = requireNotNull(case.analysisCycleId) {
        "Case ${case.id} does not have analysisCycleId"
    }

    private fun requiresSecondApproval(decision: SuspicionDecisionValue): Boolean =
        decision == SuspicionDecisionValue.COMMUNICATE_TO_COAF

    private fun requiresSecondApproval(decision: AccountDecisionValue): Boolean = when (decision) {
        AccountDecisionValue.REJECT,
        AccountDecisionValue.RESTRICT,
        AccountDecisionValue.SUSPEND,
        AccountDecisionValue.TERMINATE_RELATIONSHIP -> true
        AccountDecisionValue.APPROVE,
        AccountDecisionValue.APPROVE_WITH_CONDITIONS,
        AccountDecisionValue.REQUEST_INFORMATION,
        AccountDecisionValue.MAINTAIN -> false
    }

    private fun decisionRoute(requiresApproval: Boolean): DecisionRoute =
        if (requiresApproval) DecisionRoute.MANDATORY_SECOND_APPROVAL else DecisionRoute.DERIVED_TO_ANALYST

    private fun approvalStatus(requiresApproval: Boolean): DecisionApprovalStatus =
        if (requiresApproval) DecisionApprovalStatus.PENDING_APPROVAL else DecisionApprovalStatus.APPROVED

    private fun approveDecisionActor(decidedByActorId: String, approvingActorId: String) {
        if (decidedByActorId == approvingActorId) {
            throw DecisionApprovalConflictException(decidedByActorId)
        }
    }

    private fun accountDecisionContext(decision: AccountDecisionValue): String = when (decision) {
        AccountDecisionValue.APPROVE,
        AccountDecisionValue.APPROVE_WITH_CONDITIONS,
        AccountDecisionValue.REJECT -> "ONBOARDING"
        AccountDecisionValue.MAINTAIN,
        AccountDecisionValue.RESTRICT,
        AccountDecisionValue.SUSPEND,
        AccountDecisionValue.TERMINATE_RELATIONSHIP,
        AccountDecisionValue.REQUEST_INFORMATION -> "ONGOING"
    }

    companion object {
        const val GROUPING_POLICY_VERSION = "transaction-alert-grouping-1"

        private val ACTIVE_CASE_STATUSES = listOf(
            CaseStatus.OPEN,
            CaseStatus.ASSIGNED,
            CaseStatus.IN_ANALYSIS,
            CaseStatus.WAITING_INFORMATION,
            CaseStatus.WAITING_TECHNICAL,
            CaseStatus.PENDING_APPROVAL,
        )

        private val GROUPABLE_CASE_STATUSES = ACTIVE_CASE_STATUSES - CaseStatus.PENDING_APPROVAL
    }
}

data class RecordTransactionSignalCaseCommand(
    val partyId: String,
    val analysisCycleId: String?,
    val signalId: String,
    val eventId: String,
    val sourceSystem: String,
    val severity: String,
    val recommendedRoute: String?,
    val evaluationId: String?,
    val transactionId: String?,
    val signalType: String?,
    val riskProfileVersion: Int?,
    val ruleMatches: List<RuleMatchView>,
    val reasonCode: String,
    val occurredAt: Instant,
    val correlationId: String,
    val sourceId: String = signalId,
    val sourceType: String = "TransactionSignal",
    val targetCaseId: String? = null,
)

data class CaseQueueView(
    val cases: List<CaseView>,
)

data class CaseDetailView(
    val case: CaseView,
    val party: PartyView,
    val sources: List<CaseSourceView>,
    val comments: List<CaseCommentView>,
    val suspicionDecisions: List<SuspicionDecisionView>,
    val accountDecisions: List<AccountDecisionView>,
    val evidenceMatrix: EvidenceMatrixView,
    val decisionReadiness: ReadinessView,
    val completionReadiness: ReadinessView,
    val timeline: TimelineView,
    val availableActions: List<CaseAction>,
)

data class CaseView(
    val caseId: String,
    val partyId: String,
    val origin: CaseOrigin,
    val status: CaseStatus,
    val priority: CasePriority,
    val reasonCode: String,
    val sourceCount: Int,
    val version: Int,
    val assignedActorId: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(entity: CaseEntity): CaseView = CaseView(
            caseId = entity.id,
            partyId = entity.partyId,
            origin = entity.origin,
            status = entity.status,
            priority = entity.priority,
            reasonCode = entity.reasonCode,
            sourceCount = entity.sourceCount,
            version = entity.version,
            assignedActorId = entity.assignedActorId,
            createdAt = entity.createdAt,
        )
    }
}

data class ChangeCaseStatusCommand(
    val actor: Actor,
    val correlationId: String,
    val expectedVersion: Int,
)

data class AddCaseCommentCommand(
    val actor: Actor,
    val correlationId: String,
    val body: String,
)

data class RetryRequirementCommand(
    val actor: Actor,
    val correlationId: String,
    val requirementId: String,
    val expectedEvidenceRevision: Int,
)

data class IssueSuspicionDecisionCommand(
    val actor: Actor,
    val correlationId: String,
    val expectedVersion: Int,
    val decision: SuspicionDecisionValue,
    val reasonCodes: List<String>,
    val narrative: String,
    val policyVersion: String,
)

data class IssueAccountDecisionCommand(
    val actor: Actor,
    val correlationId: String,
    val expectedVersion: Int,
    val decision: AccountDecisionValue,
    val reasonCodes: List<String>,
    val narrative: String,
    val policyVersion: String,
)

data class CaseCommentView(
    val commentId: String,
    val caseId: String,
    val body: String,
    val createdByActorId: String,
    val createdByActorRole: String,
    val correlationId: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(entity: CaseCommentEntity): CaseCommentView = CaseCommentView(
            commentId = entity.id,
            caseId = entity.caseId,
            body = entity.body,
            createdByActorId = entity.createdByActorId,
            createdByActorRole = entity.createdByActorRole,
            correlationId = entity.correlationId,
            createdAt = entity.createdAt,
        )
    }
}

data class CaseCommandResultView(
    val caseId: String,
    val status: CaseStatus,
    val assignedActorId: String?,
    val version: Int,
    val availableActions: List<CaseAction>,
) {
    companion object {
        fun from(entity: CaseEntity, availableActions: List<CaseAction>): CaseCommandResultView = CaseCommandResultView(
            caseId = entity.id,
            status = entity.status,
            assignedActorId = entity.assignedActorId,
            version = entity.version,
            availableActions = availableActions,
        )
    }
}

data class SuspicionDecisionView(
    val decisionId: String,
    val caseId: String,
    val partyId: String,
    val decision: SuspicionDecisionValue,
    val decisionVersion: Int,
    val reasonCodes: JsonNode,
    val narrative: String,
    val policyVersion: String,
    val decidedByActorId: String,
    val decidedByActorRole: String,
    val decidedAt: Instant,
    val correlationId: String,
    val previousDecisionId: String?,
    val approvalStatus: DecisionApprovalStatus,
    val approvedByActorId: String?,
    val approvedAt: Instant?,
) {
    companion object {
        fun from(entity: SuspicionDecisionEntity, objectMapper: ObjectMapper): SuspicionDecisionView =
            SuspicionDecisionView(
                decisionId = entity.id,
                caseId = entity.caseId,
                partyId = entity.partyId,
                decision = entity.decision,
                decisionVersion = entity.decisionVersion,
                reasonCodes = objectMapper.readTree(entity.reasonCodes),
                narrative = entity.narrative,
                policyVersion = entity.policyVersion,
                decidedByActorId = entity.decidedByActorId,
                decidedByActorRole = entity.decidedByActorRole,
                decidedAt = entity.decidedAt,
                correlationId = entity.correlationId,
                previousDecisionId = entity.previousDecisionId,
                approvalStatus = entity.approvalStatus,
                approvedByActorId = entity.approvedByActorId,
                approvedAt = entity.approvedAt,
            )
    }
}

data class AccountDecisionView(
    val decisionId: String,
    val caseId: String,
    val partyId: String,
    val decision: AccountDecisionValue,
    val decisionVersion: Int,
    val reasonCodes: JsonNode,
    val narrative: String,
    val policyVersion: String,
    val decidedByActorId: String,
    val decidedByActorRole: String,
    val decidedAt: Instant,
    val correlationId: String,
    val previousDecisionId: String?,
    val approvalStatus: DecisionApprovalStatus,
    val approvedByActorId: String?,
    val approvedAt: Instant?,
) {
    companion object {
        fun from(entity: AccountDecisionEntity, objectMapper: ObjectMapper): AccountDecisionView =
            AccountDecisionView(
                decisionId = entity.id,
                caseId = entity.caseId,
                partyId = entity.partyId,
                decision = entity.decision,
                decisionVersion = entity.decisionVersion,
                reasonCodes = objectMapper.readTree(entity.reasonCodes),
                narrative = entity.narrative,
                policyVersion = entity.policyVersion,
                decidedByActorId = entity.decidedByActorId,
                decidedByActorRole = entity.decidedByActorRole,
                decidedAt = entity.decidedAt,
                correlationId = entity.correlationId,
                previousDecisionId = entity.previousDecisionId,
                approvalStatus = entity.approvalStatus,
                approvedByActorId = entity.approvedByActorId,
                approvedAt = entity.approvedAt,
            )
    }
}

data class CaseSourceView(
    val sourceId: String,
    val sourceSystem: String,
    val sourceType: String,
    val severity: String,
    val evaluationId: String?,
    val transactionId: String?,
    val signalType: String?,
    val recommendedRoute: String?,
    val riskProfileVersion: Int?,
    val ruleMatches: JsonNode,
    val reasonCode: String,
    val correlationId: String,
    val causationId: String,
    val attachedAt: Instant,
) {
    companion object {
        fun from(entity: CaseSourceEntity, objectMapper: ObjectMapper): CaseSourceView = CaseSourceView(
            sourceId = entity.sourceId,
            sourceSystem = entity.sourceSystem,
            sourceType = entity.sourceType,
            severity = entity.severity,
            evaluationId = entity.evaluationId,
            transactionId = entity.transactionId,
            signalType = entity.signalType,
            recommendedRoute = entity.recommendedRoute,
            riskProfileVersion = entity.riskProfileVersion,
            ruleMatches = objectMapper.readTree(entity.ruleMatches),
            reasonCode = entity.reasonCode,
            correlationId = entity.correlationId,
            causationId = entity.causationId,
            attachedAt = entity.attachedAt,
        )
    }
}

data class RuleMatchView(
    val ruleCode: String,
    val ruleVersion: Int,
    val explanationCode: String?,
)

class CaseNotFoundException(caseId: String) : RuntimeException("Case not found: $caseId")

class CaseVersionConflictException(caseId: String, expectedVersion: Int, actualVersion: Int) :
    RuntimeException("Case $caseId version conflict: expected $expectedVersion but was $actualVersion")

class InvalidCaseTransitionException(caseId: String, status: CaseStatus) :
    RuntimeException("Invalid transition for case $caseId from status $status")

class DecisionApprovalConflictException(actorId: String) :
    RuntimeException("Decision approval requires a different actor than $actorId")

class CaseCompletionBlockedException(caseId: String, reasons: List<String>) :
    RuntimeException("Case $caseId completion blocked: ${reasons.joinToString(",")}")
