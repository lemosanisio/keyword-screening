package br.com.integration

import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.model.enums.Action
import br.com.shared.domain.valueobject.PrefixedUlid
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TransactionSignalOutboxListener(
    private val repository: IntegrationOutboxRepository,
    private val objectMapper: ObjectMapper,
    private val properties: TransactionSignalProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handle(event: DecisionMadeEvent) {
        if (!properties.enabled || Action.GENERATE_ALERT !in event.actions) return

        val partyId = event.customerId.value
        val evaluationId = event.evaluationId
        if (!PARTY_ID_REGEX.matches(partyId) || !TRANSACTION_ID_REGEX.matches(event.transactionId.value) || evaluationId == null) {
            logger.info(
                "Sinal externo ignorado por identificadores legados: transactionId={}, customerId={}",
                event.transactionId.value,
                partyId,
            )
            return
        }

        val occurredAt = event.timestamp
        val eventId = PrefixedUlid.ulid()
        val envelope = mapOf(
            "eventId" to eventId,
            "eventType" to "TransactionSignalDetected",
            "eventVersion" to 1,
            "occurredAt" to occurredAt,
            "producer" to "pld-transaction-screening",
            "correlationId" to (event.correlationId ?: event.traceId.value),
            "causationId" to (event.causationId ?: event.eventId.value),
            "actor" to mapOf("type" to "SYSTEM", "id" to "rule-engine"),
            "subject" to mapOf("partyId" to partyId),
            "dataClassification" to "CONFIDENTIAL",
            "payload" to mapOf(
                "signalId" to PrefixedUlid.next("sig_"),
                "evaluationId" to evaluationId,
                "transactionId" to event.transactionId.value,
                "signalType" to "RULE_MATCH",
                "severity" to "HIGH",
                "ruleMatches" to listOf(
                    mapOf(
                        "ruleCode" to event.ruleCode.value,
                        "ruleVersion" to event.configurationVersion.value,
                        "explanationCode" to "KEYWORD_MATCH",
                    ),
                ),
                "recommendedRoute" to "DERIVED_TO_ANALYST",
            ),
        )

        repository.save(
            IntegrationOutboxEntity(
                eventId = eventId,
                eventType = "TransactionSignalDetected",
                eventVersion = 1,
                aggregateType = "TransactionEvaluation",
                aggregateId = evaluationId,
                envelope = objectMapper.writeValueAsString(envelope),
                occurredAt = occurredAt,
                nextAttemptAt = Instant.now(),
            ),
        )
    }

    companion object {
        private val PARTY_ID_REGEX = Regex("^pty_[0-9A-HJKMNP-TV-Z]{26}$")
        private val TRANSACTION_ID_REGEX = Regex("^txn_[0-9A-HJKMNP-TV-Z]{26}$")
    }
}
