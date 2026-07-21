package br.com.pld.customeranalysis.casemanagement

import br.com.pld.customeranalysis.common.PrefixedUlid
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
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class CaseService(
    private val caseRepository: CaseJpaRepository,
    private val caseSourceRepository: CaseSourceJpaRepository,
    private val caseCommentRepository: CaseCommentJpaRepository,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val partyService: PartyService,
    private val timelineService: TimelineService,
    private val outboxService: OutboxService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun recordTransactionSignal(command: RecordTransactionSignalCaseCommand): CaseView? {
        if (caseSourceRepository.existsBySourceSystemAndSourceIdAndGroupingPolicyVersion(
                command.sourceSystem,
                command.signalId,
                GROUPING_POLICY_VERSION,
            )
        ) {
            return null
        }

        val now = Instant.now(clock)
        val case = caseRepository
            .findTopByPartyIdAndOriginAndStatusAndGroupingPolicyVersionOrderByCreatedAtAsc(
                partyId = command.partyId,
                origin = CaseOrigin.TRANSACTION_ALERT,
                status = CaseStatus.OPEN,
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
                sourceId = command.signalId,
                sourceType = "TransactionSignal",
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
        cases = caseRepository.findByStatusOrderByCreatedAtAsc(CaseStatus.OPEN).map(CaseView::from),
    )

    @Transactional(readOnly = true)
    fun get(caseId: String): CaseDetailView {
        val case = caseRepository.findById(caseId).orElseThrow { CaseNotFoundException(caseId) }

        return CaseDetailView(
            case = CaseView.from(case),
            party = partyService.get(case.partyId),
            sources = caseSourceRepository.findByCaseIdOrderByAttachedAtAsc(case.id)
                .map { CaseSourceView.from(it, objectMapper) },
            comments = caseCommentRepository.findByCaseIdOrderByCreatedAtAsc(case.id).map(CaseCommentView::from),
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

        recordCaseStatusChanged(case, previousStatus, command, now, "CASE_ASSIGNED")

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

        recordCaseStatusChanged(case, previousStatus, command, now, "CASE_IN_ANALYSIS")

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

        recordCaseStatusChanged(case, previousStatus, command, now, "CASE_RETURNED_TO_QUEUE")

        return CaseCommandResultView.from(case, availableActions(case))
    }

    private fun createCase(command: RecordTransactionSignalCaseCommand, now: Instant): CaseEntity {
        val case = caseRepository.save(
            CaseEntity(
                id = PrefixedUlid.next("cas_"),
                partyId = command.partyId,
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
        command: ChangeCaseStatusCommand,
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
                actorType = command.actor.role.name,
                actorId = command.actor.id,
                summaryCode = summaryCode,
                objectType = "Case",
                objectId = case.id,
                objectVersion = case.version.toString(),
                correlationId = command.correlationId,
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
                "correlationId" to command.correlationId,
            ),
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
        CaseStatus.IN_ANALYSIS -> listOf(CaseAction.RETURN_TO_QUEUE)
        else -> emptyList()
    }

    private fun priorityFrom(severity: String): CasePriority = when (severity) {
        "CRITICAL" -> CasePriority.CRITICAL
        "HIGH" -> CasePriority.HIGH
        "LOW" -> CasePriority.LOW
        else -> CasePriority.MEDIUM
    }

    companion object {
        const val GROUPING_POLICY_VERSION = "transaction-alert-grouping-1"
    }
}

data class RecordTransactionSignalCaseCommand(
    val partyId: String,
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
)

data class CaseQueueView(
    val cases: List<CaseView>,
)

data class CaseDetailView(
    val case: CaseView,
    val party: PartyView,
    val sources: List<CaseSourceView>,
    val comments: List<CaseCommentView>,
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
