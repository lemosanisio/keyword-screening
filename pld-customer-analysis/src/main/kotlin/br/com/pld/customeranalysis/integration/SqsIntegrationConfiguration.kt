package br.com.pld.customeranalysis.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
@ConditionalOnProperty(prefix = "pld.integration.sqs", name = ["enabled"], havingValue = "true")
class SqsIntegrationConfiguration {

    @Bean
    fun sqsClient(properties: SqsIntegrationProperties): SqsClient {
        val builder = SqsClient.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKeyId, properties.secretAccessKey),
                ),
            )

        if (properties.endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint))
        }

        return builder.build()
    }

    @Bean
    fun sqsOutboxPublisher(
        sqsClient: SqsClient,
        properties: SqsIntegrationProperties,
        objectMapper: ObjectMapper,
    ): OutboxPublisher = SqsOutboxPublisher(sqsClient, properties, objectMapper)
}
