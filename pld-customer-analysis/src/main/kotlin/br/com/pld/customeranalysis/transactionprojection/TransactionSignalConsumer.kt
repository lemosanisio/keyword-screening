package br.com.pld.customeranalysis.transactionprojection

import br.com.pld.customeranalysis.casemanagement.CaseService
import br.com.pld.customeranalysis.casemanagement.RecordTransactionSignalCaseCommand
import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.integration.InboxMessage
import br.com.pld.customeranalysis.integration.InboxProcessingResult
import br.com.pld.customeranalysis.integration.InboxService
import br.com.pld.customeranalysis.party.PartyJpaRepository
import br.com.pld.customeranalysis.party.PartyNotFoundException
import br.com.pld.customeranalysis.timeline.TimelineEntryEntity
import br.com.pld.customeranalysis.timeline.TimelineEntryJpaRepository
import br.com.pld.customeranalysis.timeline.VisibilityClassification
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class TransactionSignalConsumer(
    private val inboxService: InboxService,
    private val partyRepository: PartyJpaRepository,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val caseService: CaseService,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun consume(eventJson: String): InboxProcessingResult {
        val event = objectMapper.readTree(eventJson).toTransactionSignalEvent()

        return inboxService.processOnce(
            InboxMessage(
                consumerName = CONSUMER_NAME,
                eventId = event.eventId,
                eventType = event.eventType,
                eventVersion = event.eventVersion,
                payload = eventJson,
            ),
        ) {
            recordTimeline(event)
        }
    }

    private fun recordTimeline(event: TransactionSignalEvent) {
        if (!partyRepository.existsById(event.partyId)) {
            throw PartyNotFoundException(event.partyId)
        }

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = event.partyId,
                analysisCycleId = event.analysisCycleId,
                entryType = "TRANSACTION_SIGNAL_DETECTED",
                businessOccurredAt = event.occurredAt,
                recordedAt = Instant.now(clock),
                actorType = event.actorType,
                actorId = event.actorId,
                summaryCode = "TRANSACTION_SIGNAL_DETECTED_${event.severity}",
                objectType = "TransactionSignal",
                objectId = event.signalId,
                objectVersion = event.riskProfileVersion?.toString(),
                correlationId = event.correlationId,
                causationId = event.eventId,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )
        meterRegistry.counter("pld.transaction.signals.consumed", "severity", event.severity).increment()

        if (event.recommendedRoute == "DERIVED_TO_ANALYST" || event.recommendedRoute == "MANDATORY_SECOND_APPROVAL") {
            caseService.recordTransactionSignal(
                RecordTransactionSignalCaseCommand(
                    partyId = event.partyId,
                    signalId = event.signalId,
                    eventId = event.eventId,
                    sourceSystem = "pld-transaction-screening",
                    severity = event.severity,
                    recommendedRoute = event.recommendedRoute,
                    reasonCode = "TRANSACTION_SIGNAL_${event.severity}",
                    occurredAt = event.occurredAt,
                    correlationId = event.correlationId,
                ),
            )
        }
    }

    private fun JsonNode.toTransactionSignalEvent(): TransactionSignalEvent {
        val eventType = requiredText("eventType")
        require(eventType == "TransactionSignalDetected") { "Unsupported eventType: $eventType" }

        val eventVersion = requiredInt("eventVersion")
        require(eventVersion == 1) { "Unsupported TransactionSignalDetected version: $eventVersion" }

        val subject = requiredObject("subject")
        val actor = requiredObject("actor")
        val payload = requiredObject("payload")

        return TransactionSignalEvent(
            eventId = requiredText("eventId"),
            eventType = eventType,
            eventVersion = eventVersion,
            occurredAt = Instant.parse(requiredText("occurredAt")),
            correlationId = requiredText("correlationId"),
            actorType = actor.requiredText("type"),
            actorId = actor.requiredText("id"),
            partyId = subject.requiredText("partyId"),
            analysisCycleId = subject.optionalText("analysisCycleId"),
            signalId = payload.requiredText("signalId"),
            severity = payload.requiredText("severity"),
            recommendedRoute = payload.optionalText("recommendedRoute"),
            riskProfileVersion = payload.optionalInt("riskProfileVersion"),
        )
    }

    private fun JsonNode.requiredObject(fieldName: String): JsonNode {
        val value = get(fieldName)
        require(value != null && value.isObject) { "$fieldName is required" }
        return value
    }

    private fun JsonNode.requiredText(fieldName: String): String {
        val value = get(fieldName)
        require(value != null && value.isTextual && value.asText().isNotBlank()) { "$fieldName is required" }
        return value.asText()
    }

    private fun JsonNode.optionalText(fieldName: String): String? {
        val value = get(fieldName)
        return value?.takeIf { !it.isNull }?.asText()?.takeIf(String::isNotBlank)
    }

    private fun JsonNode.requiredInt(fieldName: String): Int {
        val value = get(fieldName)
        require(value != null && value.isInt) { "$fieldName is required" }
        return value.asInt()
    }

    private fun JsonNode.optionalInt(fieldName: String): Int? {
        val value = get(fieldName)
        return value?.takeIf { it.isInt }?.asInt()
    }

    companion object {
        const val CONSUMER_NAME = "pld-customer-analysis.transaction-signal-detected"
    }
}

private data class TransactionSignalEvent(
    val eventId: String,
    val eventType: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val correlationId: String,
    val actorType: String,
    val actorId: String,
    val partyId: String,
    val analysisCycleId: String?,
    val signalId: String,
    val severity: String,
    val recommendedRoute: String?,
    val riskProfileVersion: Int?,
)
