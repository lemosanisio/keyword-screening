package br.com.integration

import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.model.EvaluationOutcome
import br.com.decision.domain.model.FactQuality
import br.com.shared.domain.valueobject.PrefixedUlid
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TransactionSignalOutboxListener(
    private val repository: IntegrationOutboxRepository,
    private val objectMapper: ObjectMapper,
    private val properties: TransactionSignalProperties,
) {
    @EventListener
    fun handle(event: DecisionMadeEvent) {
        val evaluation = event.evaluation
        if (!properties.operationalEventsEnabled || evaluation == null || evaluation.purpose != "LIVE") return

        val signalId = if (evaluation.partyId != null && evaluation.evaluationOutcome == EvaluationOutcome.SIGNAL_RAISED) {
            PrefixedUlid.next("sig_")
        } else {
            null
        }
        val records = mutableListOf<IntegrationOutboxEntity>()
        records += outboxRecord(
            event = event,
            eventType = "TransactionEvaluationCompleted",
            eventVersion = 2,
            logicalId = evaluation.evaluationId,
            payload = mapOf(
                "evaluationId" to evaluation.evaluationId,
                "transactionId" to evaluation.transactionId,
                "purpose" to evaluation.purpose,
                "inputEventId" to evaluation.inputEventId,
                "inputEventSchemaVersion" to evaluation.inputEventSchemaVersion,
                "transactionVersion" to evaluation.transactionVersion,
                "evaluatedAt" to evaluation.evaluatedAt,
                "transactionSnapshotRef" to evaluation.snapshotRef,
                "snapshotFormatVersion" to evaluation.snapshotFormatVersion,
                "snapshotHash" to evaluation.snapshotHash,
                "rulesetVersion" to evaluation.rulesetVersion,
                "riskContext" to mapOf(
                    "source" to evaluation.riskContext.source,
                    "quality" to evaluation.riskContext.quality,
                    "riskProfileVersion" to evaluation.riskContext.riskProfileVersion,
                    "reasonCode" to evaluation.riskContext.reasonCode,
                ).filterValues { it != null },
                "rulesExecuted" to evaluation.rulesExecuted.map(::ruleReference),
                "rulesTriggered" to evaluation.rulesTriggered.map(::ruleReference),
                "factsConsidered" to evaluation.facts.map { fact ->
                    mapOf(
                        "code" to fact.name.value,
                        "quality" to fact.quality.name,
                        "reasonCode" to fact.reasonCode,
                    ).filterValues { it != null }
                },
                "indeterminateFacts" to evaluation.facts
                    .filter { it.quality != FactQuality.PRESENT }
                    .map { it.name.value },
                "evaluationOutcome" to evaluation.evaluationOutcome?.name,
                "reviewRequired" to evaluation.reviewRequired,
                "recommendedRoute" to evaluation.recommendedRoute?.name,
                "explanation" to evaluation.explanation,
                "latencyMs" to event.executionTimeMs,
                "executionStatus" to evaluation.executionStatus.name,
            ).filterValues { it != null },
        )

        if (signalId != null) {
            records += outboxRecord(
                event = event,
                eventType = "TransactionSignalDetected",
                eventVersion = 1,
                logicalId = signalId,
                payload = mapOf(
                    "signalId" to signalId,
                    "evaluationId" to evaluation.evaluationId,
                    "transactionId" to evaluation.transactionId,
                    "signalType" to "RULE_MATCH",
                    "severity" to "HIGH",
                    "ruleMatches" to evaluation.rulesTriggered.map(::ruleReference),
                    "recommendedRoute" to "DERIVED_TO_ANALYST",
                ),
            )
        }

        if (evaluation.partyId != null && evaluation.reviewRequired == true) {
            val reviewRequestId = PrefixedUlid.next("rrq_")
            records += outboxRecord(
                event = event,
                eventType = "ManualReviewRequested",
                eventVersion = 2,
                logicalId = reviewRequestId,
                payload = mapOf(
                    "reviewRequestId" to reviewRequestId,
                    "evaluationId" to evaluation.evaluationId,
                    "transactionId" to evaluation.transactionId,
                    "signalIds" to listOfNotNull(signalId),
                    "reasonCodes" to if (signalId == null) listOf("INSUFFICIENT_EVIDENCE") else listOf("POLICY_REQUIRES_REVIEW"),
                    "recommendedRoute" to (evaluation.recommendedRoute?.name ?: "DERIVED_TO_ANALYST"),
                ),
            )
        }

        repository.saveAll(records)
    }

    private fun outboxRecord(
        event: DecisionMadeEvent,
        eventType: String,
        eventVersion: Int,
        logicalId: String,
        payload: Map<String, Any?>,
    ): IntegrationOutboxEntity {
        val evaluation = requireNotNull(event.evaluation)
        val eventId = PrefixedUlid.ulid()
        val envelope = mutableMapOf<String, Any?>(
            "eventId" to eventId,
            "eventType" to eventType,
            "eventVersion" to eventVersion,
            "occurredAt" to evaluation.evaluatedAt,
            "producer" to "pld-transaction-screening",
            "correlationId" to evaluation.correlationId,
            "causationId" to (evaluation.causationId ?: event.eventId.value),
            "actor" to mapOf("type" to "SYSTEM", "id" to "rule-engine"),
            "dataClassification" to "CONFIDENTIAL",
            "payload" to payload,
        )
        evaluation.partyId?.let { envelope["subject"] = mapOf("partyId" to it) }
        return IntegrationOutboxEntity(
            eventId = eventId,
            eventType = eventType,
            eventVersion = eventVersion,
            aggregateType = "TransactionEvaluation",
            aggregateId = evaluation.evaluationId,
            logicalId = logicalId,
            envelope = objectMapper.writeValueAsString(envelope),
            occurredAt = evaluation.evaluatedAt,
            nextAttemptAt = Instant.now(),
        )
    }

    private fun ruleReference(reference: br.com.evaluation.domain.RuleEvaluationReference): Map<String, Any?> =
        mapOf(
            "ruleCode" to reference.ruleCode,
            "ruleVersion" to reference.ruleVersion,
            "explanationCode" to reference.explanationCode,
        ).filterValues { it != null }
}
