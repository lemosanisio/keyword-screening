package br.com.pld.customeranalysis.casemanagement

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.integration.OutboxService
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartyView
import br.com.pld.customeranalysis.timeline.TimelineEntryView
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
            timeline = timelineService.getByPartyId(case.partyId),
        )
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
    val timeline: TimelineView,
)

data class CaseView(
    val caseId: String,
    val partyId: String,
    val origin: CaseOrigin,
    val status: CaseStatus,
    val priority: CasePriority,
    val reasonCode: String,
    val sourceCount: Int,
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
            createdAt = entity.createdAt,
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
