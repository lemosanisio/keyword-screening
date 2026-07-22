package br.com.decision.infrastructure.output.projection

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.time.Instant

/**
 * Polls the risk-profile SQS queue for CustomerRiskProfileUpdated.v1 events
 * and updates the local projection. Deduplicates by partyId + profileVersion.
 */
@Component
@ConditionalOnProperty(prefix = "pld.integration.risk-profile", name = ["enabled"], havingValue = "true")
class RiskProfileConsumer(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    private val repository: CustomerRiskProjectionRepository,
    private val properties: RiskProfileConsumerProperties,
) {
    private val logger = LoggerFactory.getLogger(RiskProfileConsumer::class.java)

    @Scheduled(fixedDelayString = "\${pld.integration.risk-profile.poll-interval:5000}")
    fun poll() {
        if (properties.queueUrl.isBlank()) return

        val response = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(properties.queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .build(),
        )

        response.messages().forEach { message ->
            try {
                processMessage(message.body())
                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(properties.queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build(),
                )
            } catch (e: Exception) {
                logger.warn("Failed to process risk profile event: {}", e.message)
            }
        }
    }

    @Transactional
    fun processMessage(body: String) {
        val root = objectMapper.readTree(body)
        val eventType = root.path("eventType").asText("")
        if (eventType != "CustomerRiskProfileUpdated") {
            logger.debug("Ignoring event type: {}", eventType)
            return
        }

        val payload = root.path("payload")
        if (payload.isMissingNode) {
            logger.warn("CustomerRiskProfileUpdated without payload, skipping")
            return
        }

        val partyId = payload.path("partyId").asText("")
        if (partyId.isBlank()) {
            logger.warn("CustomerRiskProfileUpdated without partyId, skipping")
            return
        }

        val entity = CustomerRiskProjectionEntity(
            partyId = partyId,
            riskLevel = payload.path("riskLevel").asText("LOW"),
            segments = payload.path("segments").map { it.asText() },
            transactionFacts = parseTransactionFacts(payload.path("transactionFacts")),
            profileVersion = payload.path("profileVersion").asInt(1),
            riskProfileId = payload.path("riskProfileId").asText(""),
            policyVersion = payload.path("policyVersion").asText(""),
            effectiveFrom = parseInstant(payload.path("effectiveFrom")) ?: Instant.now(),
            validUntil = parseInstant(payload.path("validUntil")) ?: Instant.now().plusSeconds(86400 * 90),
            updatedAt = Instant.now(),
        )

        repository.upsert(entity)
        logger.info(
            "Risk projection updated: partyId={}, riskLevel={}, version={}",
            partyId, entity.riskLevel, entity.profileVersion,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTransactionFacts(node: JsonNode): Map<String, Any?> {
        if (node.isMissingNode || node.isNull) return emptyMap()
        return objectMapper.convertValue(node, Map::class.java) as Map<String, Any?>
    }

    private fun parseInstant(node: JsonNode): Instant? {
        if (node.isMissingNode || node.isNull) return null
        return try {
            Instant.parse(node.asText())
        } catch (_: Exception) {
            null
        }
    }
}
