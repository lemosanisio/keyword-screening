package br.com.pld.customeranalysis.transactionprojection

import br.com.pld.customeranalysis.integration.InboxProcessingResult
import br.com.pld.customeranalysis.integration.SqsIntegrationProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import org.slf4j.LoggerFactory

@Component
@ConditionalOnProperty(prefix = "pld.integration.sqs", name = ["inbound-enabled"], havingValue = "true")
class SqsTransactionSignalPoller(
    private val sqsClient: SqsClient,
    private val properties: SqsIntegrationProperties,
    private val objectMapper: ObjectMapper,
    private val transactionSignalConsumer: TransactionSignalConsumer,
    private val transactionEvaluationConsumer: TransactionEvaluationConsumer,
    private val manualReviewConsumer: ManualReviewConsumer,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "\${pld.integration.sqs.inbound-initial-delay}",
        fixedDelayString = "\${pld.integration.sqs.inbound-fixed-delay}",
    )
    fun poll() {
        require(properties.transactionSignalsQueueUrl.isNotBlank()) {
            "pld.integration.sqs.transaction-signals-queue-url must be configured when inbound SQS is enabled"
        }

        val messages = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(properties.transactionSignalsQueueUrl)
                .maxNumberOfMessages(properties.inboundMaxMessages.coerceIn(1, 10))
                .waitTimeSeconds(0)
                .build(),
        ).messages()

        messages.forEach { message ->
            runCatching { dispatch(message) }
                .onFailure {
                    logger.warn("Failed to process inbound SQS message id={}: {}", message.messageId(), it.message, it)
                    meterRegistry.counter("pld.sqs.inbound.messages.failed").increment()
                }
        }
    }

    private fun dispatch(message: Message) {
        val body = message.body()
        val root = objectMapper.readTree(body)
        val eventType = root.path("eventType").asText()
        val eventVersion = root.path("eventVersion").asInt()

        val result = when (eventType to eventVersion) {
            "TransactionSignalDetected" to 1 -> transactionSignalConsumer.consume(body)
            "TransactionEvaluationCompleted" to 2 -> transactionEvaluationConsumer.consume(body)
            "ManualReviewRequested" to 2 -> manualReviewConsumer.consume(body)
            else -> {
                meterRegistry.counter(
                    "pld.sqs.inbound.messages.unsupported",
                    "eventType",
                    eventType.ifBlank { "UNKNOWN" },
                    "eventVersion",
                    eventVersion.toString(),
                ).increment()
                return
            }
        }

        if (result == InboxProcessingResult.PROCESSED || result == InboxProcessingResult.DUPLICATE) {
            delete(message, eventType)
        }
    }

    private fun delete(message: Message, metricEventType: String) {
        sqsClient.deleteMessage(
            DeleteMessageRequest.builder()
                .queueUrl(properties.transactionSignalsQueueUrl)
                .receiptHandle(message.receiptHandle())
                .build(),
        )
        meterRegistry.counter("pld.sqs.inbound.messages.deleted", "eventType", metricEventType).increment()
    }
}
