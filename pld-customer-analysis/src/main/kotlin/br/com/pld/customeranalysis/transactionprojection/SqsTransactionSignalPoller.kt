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

@Component
@ConditionalOnProperty(prefix = "pld.integration.sqs", name = ["inbound-enabled"], havingValue = "true")
class SqsTransactionSignalPoller(
    private val sqsClient: SqsClient,
    private val properties: SqsIntegrationProperties,
    private val objectMapper: ObjectMapper,
    private val transactionSignalConsumer: TransactionSignalConsumer,
    private val meterRegistry: MeterRegistry,
) {
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

        messages.forEach(::dispatch)
    }

    private fun dispatch(message: Message) {
        val body = message.body()
        val eventType = objectMapper.readTree(body).path("eventType").asText()

        val result = when (eventType) {
            "TransactionSignalDetected" -> transactionSignalConsumer.consume(body)
            else -> {
                meterRegistry.counter("pld.sqs.inbound.messages.unsupported", "eventType", "UNKNOWN").increment()
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
