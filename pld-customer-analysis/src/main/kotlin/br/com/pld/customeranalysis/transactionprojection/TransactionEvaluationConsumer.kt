package br.com.pld.customeranalysis.transactionprojection

import br.com.pld.customeranalysis.integration.InboxMessage
import br.com.pld.customeranalysis.integration.InboxProcessingResult
import br.com.pld.customeranalysis.integration.InboxService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.sql.Timestamp

@Service
class TransactionEvaluationConsumer(
    private val inboxService: InboxService,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: TransactionSignalProperties,
) {
    fun consume(eventJson: String): InboxProcessingResult {
        val root = objectMapper.readTree(eventJson)
        val eventId = root.requiredText("eventId")
        val eventType = root.requiredText("eventType")
        val eventVersion = root.requiredInt("eventVersion")
        require(eventType == "TransactionEvaluationCompleted" && eventVersion == 2)
        val producer = root.requiredText("producer")
        require(producer == properties.acceptedProducer)
        val payload = root.requiredObject("payload")
        val evaluationId = payload.requiredText("evaluationId")
        val subject = root.get("subject")

        return inboxService.processOnce(
            InboxMessage(CONSUMER_NAME, eventId, eventType, eventVersion, eventJson),
        ) {
            jdbcTemplate.update(
                """
                    INSERT INTO transaction_evaluation_projection (
                        source_system, evaluation_id, event_id, party_id, transaction_id, purpose,
                        execution_status, evaluation_outcome, review_required, recommended_route,
                        snapshot_ref, snapshot_format_version, snapshot_hash, ruleset_version,
                        facts, rules_executed, rules_triggered, explanation, evaluated_at, received_at, payload
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb)
                    ON CONFLICT (source_system, evaluation_id) DO NOTHING
                """.trimIndent(),
                producer,
                evaluationId,
                eventId,
                subject?.get("partyId")?.asText(),
                payload.requiredText("transactionId"),
                payload.requiredText("purpose"),
                payload.requiredText("executionStatus"),
                payload.optionalText("evaluationOutcome"),
                payload.get("reviewRequired")?.takeIf(JsonNode::isBoolean)?.asBoolean(),
                payload.optionalText("recommendedRoute"),
                payload.requiredText("transactionSnapshotRef"),
                payload.requiredText("snapshotFormatVersion"),
                payload.requiredText("snapshotHash"),
                payload.requiredText("rulesetVersion"),
                payload.requiredNode("factsConsidered").toString(),
                payload.requiredNode("rulesExecuted").toString(),
                payload.requiredNode("rulesTriggered").toString(),
                payload.requiredNode("explanation").toString(),
                Timestamp.from(Instant.parse(payload.requiredText("evaluatedAt"))),
                Timestamp.from(Instant.now()),
                eventJson,
            )
        }
    }

    companion object { const val CONSUMER_NAME = "pld-customer-analysis.transaction-evaluation-completed-v2" }
}

internal fun JsonNode.requiredObject(field: String): JsonNode =
    get(field)?.takeIf(JsonNode::isObject) ?: throw IllegalArgumentException("$field is required")

internal fun JsonNode.requiredNode(field: String): JsonNode =
    get(field) ?: throw IllegalArgumentException("$field is required")

internal fun JsonNode.requiredText(field: String): String =
    get(field)?.takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("$field is required")

internal fun JsonNode.optionalText(field: String): String? =
    get(field)?.takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()

internal fun JsonNode.requiredInt(field: String): Int =
    get(field)?.takeIf(JsonNode::isInt)?.asInt() ?: throw IllegalArgumentException("$field is required")
