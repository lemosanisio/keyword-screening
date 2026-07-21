package br.com.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.net.URI
import java.time.Instant

@Configuration
@ConditionalOnProperty(prefix = "pld.integration.sqs", name = ["enabled"], havingValue = "true")
class SqsIntegrationConfiguration {
    @Bean
    fun sqsClient(properties: SqsProperties): SqsClient {
        val builder = SqsClient.builder()
            .region(Region.of(properties.region))
        if (properties.endpoint.isNotBlank()) {
            builder
                .endpointOverride(URI.create(properties.endpoint))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKeyId, properties.secretAccessKey),
                    ),
                )
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create())
        }
        return builder.build()
    }

    @Bean
    fun integrationEventPublisher(
        client: SqsClient,
        objectMapper: ObjectMapper,
        properties: TransactionSignalProperties,
    ): IntegrationEventPublisher = IntegrationEventPublisher { eventId, eventType, envelope, publishedAt ->
        require(properties.queueUrl.isNotBlank()) {
            "pld.integration.transaction-signals.queue-url must be configured when SQS is enabled"
        }
        val body = objectMapper.readTree(envelope).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            .put("publishedAt", publishedAt.toString())
        client.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(properties.queueUrl)
                .messageBody(objectMapper.writeValueAsString(body))
                .messageAttributes(
                    mapOf(
                        "eventId" to stringAttribute(eventId),
                        "eventType" to stringAttribute(eventType),
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
