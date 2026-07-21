package br.com.pld.customeranalysis.integration

import com.fasterxml.jackson.databind.ObjectMapper
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class SqsOutboxPublisher(
    private val sqsClient: SqsClient,
    private val properties: SqsIntegrationProperties,
    private val objectMapper: ObjectMapper,
) : OutboxPublisher {
    override fun publish(message: OutboxMessage) {
        require(properties.outboxQueueUrl.isNotBlank()) {
            "pld.integration.sqs.outbox-queue-url must be configured when SQS integration is enabled"
        }

        sqsClient.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(properties.outboxQueueUrl)
                .messageBody(objectMapper.writeValueAsString(message))
                .messageAttributes(
                    mapOf(
                        "eventId" to stringAttribute(message.id),
                        "eventType" to stringAttribute(message.eventType),
                        "eventVersion" to stringAttribute(message.eventVersion.toString()),
                        "aggregateType" to stringAttribute(message.aggregateType),
                        "aggregateId" to stringAttribute(message.aggregateId),
                    ),
                )
                .build(),
        )
    }

    private fun stringAttribute(value: String): MessageAttributeValue = MessageAttributeValue.builder()
        .dataType("String")
        .stringValue(value)
        .build()
}
