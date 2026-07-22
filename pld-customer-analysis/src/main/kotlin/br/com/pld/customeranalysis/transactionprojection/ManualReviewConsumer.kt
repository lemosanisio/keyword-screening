package br.com.pld.customeranalysis.transactionprojection

import br.com.pld.customeranalysis.casemanagement.CaseService
import br.com.pld.customeranalysis.casemanagement.RecordTransactionSignalCaseCommand
import br.com.pld.customeranalysis.integration.InboxMessage
import br.com.pld.customeranalysis.integration.InboxProcessingResult
import br.com.pld.customeranalysis.integration.InboxService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.sql.Timestamp
import org.springframework.transaction.annotation.Transactional

@Service
class ManualReviewConsumer(
    private val inboxService: InboxService,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: TransactionSignalProperties,
    private val caseService: CaseService,
) {
    fun consume(eventJson: String): InboxProcessingResult {
        val root = objectMapper.readTree(eventJson)
        val eventId = root.requiredText("eventId")
        val eventType = root.requiredText("eventType")
        val eventVersion = root.requiredInt("eventVersion")
        require(eventType == "ManualReviewRequested" && eventVersion == 2)
        val producer = root.requiredText("producer")
        require(producer == properties.acceptedProducer)
        val payload = root.requiredObject("payload")
        val subject = root.requiredObject("subject")
        val reviewRequestId = payload.requiredText("reviewRequestId")

        return inboxService.processOnce(
            InboxMessage(CONSUMER_NAME, eventId, eventType, eventVersion, eventJson),
        ) {
            val inserted = jdbcTemplate.update(
                """
                    INSERT INTO manual_review_request (
                        source_system, source_request_id, event_id, evaluation_id, party_id, transaction_id,
                        signal_ids, reason_codes, recommended_route, grouping_policy_version_applied,
                        trigger_mode, effect_status, payload, occurred_at, received_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (source_system, source_request_id) DO NOTHING
                """.trimIndent(),
                producer,
                reviewRequestId,
                eventId,
                payload.requiredText("evaluationId"),
                subject.requiredText("partyId"),
                payload.requiredText("transactionId"),
                payload.requiredNode("signalIds").toString(),
                payload.requiredNode("reasonCodes").toString(),
                payload.requiredText("recommendedRoute"),
                CaseService.GROUPING_POLICY_VERSION,
                properties.caseTriggerMode.name,
                if (properties.caseTriggerMode == CaseTriggerMode.MANUAL_REVIEW_LIVE) "CASE_EFFECT_PENDING" else "PROJECTED_SHADOW",
                eventJson,
                Timestamp.from(Instant.parse(root.requiredText("occurredAt"))),
                Timestamp.from(Instant.now()),
            )
            if (inserted == 0) {
                requireSameBusinessRequest(producer, reviewRequestId, root)
            } else if (properties.caseCreationEnabled && properties.caseTriggerMode == CaseTriggerMode.MANUAL_REVIEW_LIVE) {
                applyCaseEffect(eventJson)
            }
        }
    }

    @Transactional
    fun reconcilePending(limit: Int = 100): Int {
        if (!properties.caseCreationEnabled || properties.caseTriggerMode != CaseTriggerMode.MANUAL_REVIEW_LIVE) return 0
        val payloads = jdbcTemplate.queryForList(
            """
                SELECT payload::text FROM manual_review_request
                WHERE (
                    (effect_status = 'PROJECTED_SHADOW' AND trigger_mode = 'SHADOW' AND received_at >= ?)
                    OR (effect_status = 'CASE_EFFECT_PENDING' AND trigger_mode = 'MANUAL_REVIEW_LIVE')
                    OR (
                        effect_status = 'CASE_EFFECT_APPLIED'
                        AND association_complete = FALSE
                        AND trigger_mode = 'MANUAL_REVIEW_LIVE'
                    )
                )
                ORDER BY received_at
                LIMIT ?
            """.trimIndent(),
            String::class.java,
            Timestamp.from(properties.manualReviewCutoverAt),
            limit,
        )
        payloads.forEach(::applyCaseEffect)
        return payloads.size
    }

    private fun applyCaseEffect(eventJson: String) {
        val root = objectMapper.readTree(eventJson)
        val producer = root.requiredText("producer")
        val eventId = root.requiredText("eventId")
        val payload = root.requiredObject("payload")
        val subject = root.requiredObject("subject")
        val reviewRequestId = payload.requiredText("reviewRequestId")
        val request = jdbcTemplate.queryForMap(
            """
                SELECT effect_status, case_id FROM manual_review_request
                WHERE source_system = ? AND source_request_id = ?
            """.trimIndent(),
            producer,
            reviewRequestId,
        )
        val currentStatus = request["effect_status"] as String
        if (currentStatus == "CASE_EFFECT_APPLIED") {
            val caseId = request["case_id"] as? String ?: return
            payload.requiredNode("signalIds").forEach {
                attachProjectedSignal(producer, it.asText(), caseId, subject.requiredText("partyId"), payload)
            }
            markAssociationComplete(producer, reviewRequestId)
            return
        }

        val case = caseService.recordTransactionSignal(
            RecordTransactionSignalCaseCommand(
                partyId = subject.requiredText("partyId"),
                analysisCycleId = subject.optionalText("analysisCycleId"),
                signalId = reviewRequestId,
                eventId = eventId,
                sourceSystem = producer,
                severity = "HIGH",
                recommendedRoute = payload.requiredText("recommendedRoute"),
                evaluationId = payload.requiredText("evaluationId"),
                transactionId = payload.requiredText("transactionId"),
                signalType = null,
                riskProfileVersion = null,
                ruleMatches = emptyList(),
                reasonCode = payload.requiredNode("reasonCodes").first().asText(),
                occurredAt = Instant.parse(root.requiredText("occurredAt")),
                correlationId = root.requiredText("correlationId"),
                sourceId = reviewRequestId,
                sourceType = "ManualReviewRequest",
            ),
        )
        jdbcTemplate.update(
            """
                UPDATE manual_review_request
                SET effect_status = 'CASE_EFFECT_APPLIED', case_id = ?
                WHERE source_system = ? AND source_request_id = ?
            """.trimIndent(),
            case?.caseId,
            producer,
            reviewRequestId,
        )
        val caseId = case?.caseId ?: return
        payload.requiredNode("signalIds").forEach { signalIdNode ->
            attachProjectedSignal(producer, signalIdNode.asText(), caseId, subject.requiredText("partyId"), payload)
        }
        markAssociationComplete(producer, reviewRequestId)
    }

    private fun markAssociationComplete(sourceSystem: String, reviewRequestId: String) {
        jdbcTemplate.update(
            """
                UPDATE manual_review_request r
                SET association_complete = TRUE
                WHERE source_system = ? AND source_request_id = ?
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
            sourceSystem,
            reviewRequestId,
        )
    }

    private fun attachProjectedSignal(
        sourceSystem: String,
        signalId: String,
        caseId: String,
        partyId: String,
        reviewPayload: com.fasterxml.jackson.databind.JsonNode,
    ) {
        acquireAssociationLock(sourceSystem, signalId)
        val projection = jdbcTemplate.queryForList(
            """
                SELECT payload::text FROM transaction_signal_projection
                WHERE source_system = ? AND signal_id = ?
            """.trimIndent(),
            String::class.java,
            sourceSystem,
            signalId,
        ).firstOrNull() ?: return
        val signal = objectMapper.readTree(projection)
        require(signal.requiredText("partyId") == partyId) { "review signal party mismatch" }
        require(signal.requiredText("evaluationId") == reviewPayload.requiredText("evaluationId")) {
            "review signal evaluation mismatch"
        }
        require(signal.requiredText("transactionId") == reviewPayload.requiredText("transactionId")) {
            "review signal transaction mismatch"
        }
        caseService.recordTransactionSignal(
            RecordTransactionSignalCaseCommand(
                partyId = signal.requiredText("partyId"),
                analysisCycleId = signal.optionalText("analysisCycleId"),
                signalId = signal.requiredText("signalId"),
                eventId = signal.requiredText("eventId"),
                sourceSystem = sourceSystem,
                severity = signal.requiredText("severity"),
                recommendedRoute = signal.optionalText("recommendedRoute"),
                evaluationId = signal.requiredText("evaluationId"),
                transactionId = signal.requiredText("transactionId"),
                signalType = signal.requiredText("signalType"),
                riskProfileVersion = signal.get("riskProfileVersion")?.takeIf { it.isInt }?.asInt(),
                ruleMatches = signal.requiredNode("ruleMatches").map { rule ->
                    br.com.pld.customeranalysis.casemanagement.RuleMatchView(
                        ruleCode = rule.requiredText("ruleCode"),
                        ruleVersion = rule.requiredInt("ruleVersion"),
                        explanationCode = rule.optionalText("explanationCode"),
                    )
                },
                reasonCode = "TRANSACTION_SIGNAL_${signal.requiredText("severity")}",
                occurredAt = Instant.parse(signal.requiredText("occurredAt")),
                correlationId = signal.requiredText("correlationId"),
                targetCaseId = caseId,
            ),
        )
    }

    private fun requireSameBusinessRequest(
        sourceSystem: String,
        reviewRequestId: String,
        incoming: com.fasterxml.jackson.databind.JsonNode,
    ) {
        val persisted = objectMapper.readTree(jdbcTemplate.queryForObject(
            """
                SELECT payload::text FROM manual_review_request
                WHERE source_system = ? AND source_request_id = ?
            """.trimIndent(),
            String::class.java,
            sourceSystem,
            reviewRequestId,
        ))
        require(persisted.requiredObject("subject") == incoming.requiredObject("subject")) {
            "review request identity conflicts with subject"
        }
        require(persisted.requiredObject("payload") == incoming.requiredObject("payload")) {
            "review request identity conflicts with payload"
        }
    }

    private fun acquireAssociationLock(sourceSystem: String, signalId: String) {
        jdbcTemplate.queryForObject(
            "SELECT pg_advisory_xact_lock(hashtext(?)) IS NULL",
            Boolean::class.java,
            "$sourceSystem:$signalId",
        )
    }

    companion object { const val CONSUMER_NAME = "pld-customer-analysis.manual-review-requested-v2" }
}
