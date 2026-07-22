package br.com.pld.customeranalysis.transactionprojection

import br.com.pld.customeranalysis.integration.InboxProcessingResult
import br.com.pld.customeranalysis.integration.SqsIntegrationProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
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
                .onFailure { failure ->
                    if (failure.isPoison()) {
                        quarantine(message, failure)
                    } else {
                        logger.warn(
                            "Failed to process inbound SQS message id={}: {}",
                            message.messageId(),
                            failure.message,
                            failure,
                        )
                        meterRegistry.counter("pld.sqs.inbound.messages.failed").increment()
                    }
                }
        }
    }

    private fun dispatch(message: Message) {
        val body = message.body()
        val root = try {
            objectMapper.readTree(body)
        } catch (e: JsonProcessingException) {
            throw PoisonMessageException("malformed JSON", e)
        }
        val eventType = root.path("eventType").asText()
        val eventVersion = root.path("eventVersion").asInt()

        val result = when (eventType to eventVersion) {
            "TransactionSignalDetected" to 1 -> transactionSignalConsumer.consume(body)
            "TransactionEvaluationCompleted" to 2 -> transactionEvaluationConsumer.consume(body)
            "ManualReviewRequested" to 2 -> manualReviewConsumer.consume(body)
            else -> throw PoisonMessageException("unsupported $eventType v$eventVersion")
        }

        if (result == InboxProcessingResult.PROCESSED || result == InboxProcessingResult.DUPLICATE) {
            delete(message, eventType)
        }
    }

    /**
     * Mensagens deterministicamente inválidas (JSON malformado, tipo/versão não suportados
     * ou envelope/payload inválido) nunca serão processadas: move para a DLQ e remove da
     * fila principal. Falhas transitórias (infra, parte ainda não projetada) seguem em retry.
     */
    private fun quarantine(message: Message, failure: Throwable) {
        logger.error(
            "Poison inbound SQS message id={}, moving to DLQ: {}",
            message.messageId(),
            failure.message,
            failure,
        )
        meterRegistry.counter(
            "pld.sqs.inbound.messages.quarantined",
            "reason",
            failure::class.simpleName ?: "UNKNOWN",
        ).increment()
        if (properties.transactionSignalsDlqUrl.isBlank()) {
            logger.error("pld.integration.sqs.transaction-signals-dlq-url not configured; poison message kept for retry")
            return
        }
        sqsClient.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(properties.transactionSignalsDlqUrl)
                .messageBody(message.body())
                .messageAttributes(
                    mapOf(
                        "quarantineReason" to MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue((failure.message ?: "poison").take(256))
                            .build(),
                        "sourceQueueUrl" to MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(properties.transactionSignalsQueueUrl.take(256))
                            .build(),
                    ),
                )
                .build(),
        )
        delete(message, "POISON")
    }

    private fun Throwable.isPoison(): Boolean = when (this) {
        is PoisonMessageException, is IllegalArgumentException -> true
        else -> false
    }

    private class PoisonMessageException(message: String, cause: Throwable? = null) :
        RuntimeException(message, cause)

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
