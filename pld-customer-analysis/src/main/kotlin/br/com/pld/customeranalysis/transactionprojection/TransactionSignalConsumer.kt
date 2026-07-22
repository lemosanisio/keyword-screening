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
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.Instant
import java.sql.Timestamp

@Service
class TransactionSignalConsumer(
    private val inboxService: InboxService,
    private val partyRepository: PartyJpaRepository,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val caseService: CaseService,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    private val properties: TransactionSignalProperties,
    private val jdbcTemplate: JdbcTemplate,
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

        val inserted = jdbcTemplate.update(
            """
                INSERT INTO transaction_signal_projection (
                    source_system, signal_id, event_id, evaluation_id, party_id, transaction_id,
                    payload, occurred_at, received_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (source_system, signal_id) DO NOTHING
            """.trimIndent(),
            properties.acceptedProducer,
            event.signalId,
            event.eventId,
            event.evaluationId,
            event.partyId,
            event.transactionId,
            objectMapper.writeValueAsString(event),
            Timestamp.from(event.occurredAt),
            Timestamp.from(Instant.now(clock)),
        )
        if (inserted == 1) timelineRepository.save(
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
        if (inserted == 1) meterRegistry.counter("pld.transaction.signals.consumed", "severity", event.severity).increment()

        if (properties.caseTriggerMode == CaseTriggerMode.MANUAL_REVIEW_LIVE) {
            acquireAssociationLock(properties.acceptedProducer, event.signalId)
        }

        if (
            inserted == 1 && properties.caseCreationEnabled &&
            properties.caseTriggerMode != CaseTriggerMode.MANUAL_REVIEW_LIVE &&
            (event.recommendedRoute == "DERIVED_TO_ANALYST" || event.recommendedRoute == "MANDATORY_SECOND_APPROVAL")
        ) {
            recordCaseSource(event)
        } else if (properties.caseCreationEnabled && properties.caseTriggerMode == CaseTriggerMode.MANUAL_REVIEW_LIVE) {
            val request = jdbcTemplate.queryForList(
                """
                    SELECT case_id, party_id, evaluation_id, transaction_id FROM manual_review_request
                    WHERE source_system = ? AND signal_ids @> ?::jsonb
                      AND effect_status = 'CASE_EFFECT_APPLIED' AND case_id IS NOT NULL
                    ORDER BY received_at
                    LIMIT 1
                """.trimIndent(),
                properties.acceptedProducer,
                objectMapper.writeValueAsString(listOf(event.signalId)),
            ).firstOrNull()
            if (request != null) {
                require(request["party_id"] == event.partyId) { "review signal party mismatch" }
                require(request["evaluation_id"] == event.evaluationId) { "review signal evaluation mismatch" }
                require(request["transaction_id"] == event.transactionId) { "review signal transaction mismatch" }
                recordCaseSource(event, request["case_id"] as String)
                jdbcTemplate.update(
                    """
                        UPDATE manual_review_request r
                        SET association_complete = TRUE
                        WHERE source_system = ? AND signal_ids @> ?::jsonb
                          AND effect_status = 'CASE_EFFECT_APPLIED'
                          AND NOT EXISTS (
                              SELECT 1 FROM jsonb_array_elements_text(r.signal_ids) signal
                              WHERE NOT EXISTS (
                                  SELECT 1 FROM case_source source
                                    WHERE source.source_system = r.source_system
                                      AND source.source_id = signal.value
                                      AND source.case_id = r.case_id
                              )
                          )
                    """.trimIndent(),
                    properties.acceptedProducer,
                    objectMapper.writeValueAsString(listOf(event.signalId)),
                )
            } else if (inserted == 1) {
                meterRegistry.counter(
                    "pld.transaction.signals.unmatched",
                    "signalType",
                    event.signalType,
                ).increment()
            }
        }
    }

    private fun recordCaseSource(event: TransactionSignalEvent, targetCaseId: String? = null) {
        caseService.recordTransactionSignal(
            RecordTransactionSignalCaseCommand(
                partyId = event.partyId,
                analysisCycleId = event.analysisCycleId,
                signalId = event.signalId,
                eventId = event.eventId,
                sourceSystem = properties.acceptedProducer,
                severity = event.severity,
                recommendedRoute = event.recommendedRoute,
                evaluationId = event.evaluationId,
                transactionId = event.transactionId,
                signalType = event.signalType,
                riskProfileVersion = event.riskProfileVersion,
                ruleMatches = event.ruleMatches.map {
                    br.com.pld.customeranalysis.casemanagement.RuleMatchView(
                        ruleCode = it.ruleCode,
                        ruleVersion = it.ruleVersion,
                        explanationCode = it.explanationCode,
                    )
                },
                reasonCode = "TRANSACTION_SIGNAL_${event.severity}",
                occurredAt = event.occurredAt,
                correlationId = event.correlationId,
                targetCaseId = targetCaseId,
            ),
        )
    }

    private fun acquireAssociationLock(sourceSystem: String, signalId: String) {
        jdbcTemplate.queryForObject(
            "SELECT pg_advisory_xact_lock(hashtext(?)) IS NULL",
            Boolean::class.java,
            "$sourceSystem:$signalId",
        )
    }

    private fun JsonNode.toTransactionSignalEvent(): TransactionSignalEvent {
        val eventType = requiredText("eventType")
        require(eventType == "TransactionSignalDetected") { "Unsupported eventType: $eventType" }

        val eventVersion = requiredInt("eventVersion")
        require(eventVersion == 1) { "Unsupported TransactionSignalDetected version: $eventVersion" }

        val producer = requiredText("producer")
        require(producer == properties.acceptedProducer) { "Unsupported producer: $producer" }
        require(requiredText("dataClassification") in DATA_CLASSIFICATIONS) { "Unsupported dataClassification" }
        Instant.parse(requiredText("publishedAt"))

        val subject = requiredObject("subject")
        val actor = requiredObject("actor")
        val payload = requiredObject("payload")

        return TransactionSignalEvent(
            eventId = requiredText("eventId").requireMatches(EVENT_ID_REGEX, "eventId"),
            eventType = eventType,
            eventVersion = eventVersion,
            occurredAt = Instant.parse(requiredText("occurredAt")),
            correlationId = requiredText("correlationId"),
            actorType = actor.requiredText("type"),
            actorId = actor.requiredText("id"),
            partyId = subject.requiredText("partyId").requireMatches(PARTY_ID_REGEX, "partyId"),
            analysisCycleId = subject.optionalText("analysisCycleId"),
            signalId = payload.requiredText("signalId").requireMatches(SIGNAL_ID_REGEX, "signalId"),
            evaluationId = payload.requiredText("evaluationId").requireMatches(EVALUATION_ID_REGEX, "evaluationId"),
            transactionId = payload.requiredText("transactionId").requireMatches(TRANSACTION_ID_REGEX, "transactionId"),
            signalType = payload.requiredText("signalType").takeIf { it in SIGNAL_TYPES } ?: "UNKNOWN",
            severity = payload.requiredText("severity").takeIf { it in SEVERITIES } ?: "UNKNOWN",
            recommendedRoute = payload.optionalText("recommendedRoute")?.takeIf { it in ROUTES } ?: "UNKNOWN",
            riskProfileVersion = payload.optionalInt("riskProfileVersion"),
            ruleMatches = payload.requiredArray("ruleMatches").map {
                TransactionRuleMatch(
                    ruleCode = it.requiredText("ruleCode"),
                    ruleVersion = it.requiredInt("ruleVersion"),
                    explanationCode = it.optionalText("explanationCode"),
                )
            },
        )
    }

    private fun JsonNode.requiredObject(fieldName: String): JsonNode {
        val value = get(fieldName)
        require(value != null && value.isObject) { "$fieldName is required" }
        return value
    }

    private fun JsonNode.requiredArray(fieldName: String): Iterable<JsonNode> {
        val value = get(fieldName)
        require(value != null && value.isArray) { "$fieldName is required" }
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

    private fun String.requireMatches(regex: Regex, fieldName: String): String {
        require(regex.matches(this)) { "$fieldName has invalid format" }
        return this
    }

    companion object {
        const val CONSUMER_NAME = "pld-customer-analysis.transaction-signal-detected"
        private val EVENT_ID_REGEX = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
        private val PARTY_ID_REGEX = Regex("^pty_[0-9A-HJKMNP-TV-Z]{26}$")
        private val SIGNAL_ID_REGEX = Regex("^sig_[0-9A-HJKMNP-TV-Z]{26}$")
        private val EVALUATION_ID_REGEX = Regex("^evl_[0-9A-HJKMNP-TV-Z]{26}$")
        private val TRANSACTION_ID_REGEX = Regex("^txn_[0-9A-HJKMNP-TV-Z]{26}$")
        private val DATA_CLASSIFICATIONS = setOf("INTERNAL", "CONFIDENTIAL", "RESTRICTED")
        private val SIGNAL_TYPES = setOf("RULE_MATCH")
        private val SEVERITIES = setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
        private val ROUTES = setOf("AUTOMATIC", "DERIVED_TO_ANALYST", "MANDATORY_SECOND_APPROVAL", "TECHNICAL_RETRY")
    }
}

internal data class TransactionSignalEvent(
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
    val evaluationId: String,
    val transactionId: String,
    val signalType: String,
    val severity: String,
    val recommendedRoute: String?,
    val riskProfileVersion: Int?,
    val ruleMatches: List<TransactionRuleMatch>,
)

internal data class TransactionRuleMatch(
    val ruleCode: String,
    val ruleVersion: Int,
    val explanationCode: String?,
)
