package br.com.pld.customeranalysis.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.time.Instant

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class SqsOutboxPublisherIntegrationTest {

    @Autowired
    private lateinit var outboxPublisher: OutboxPublisher

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `sends outbox message to configured sqs queue`() {
        val message = OutboxMessage(
            id = "evt_01J6ZK7Q3W8K0M2N4P6R8T0X1A",
            eventType = "PartyCreated",
            eventVersion = 1,
            aggregateType = "Party",
            aggregateId = "pty_01J6ZK7Q3W8K0M2N4P6R8T0X1B",
            payload = "{\"partyId\":\"pty_01J6ZK7Q3W8K0M2N4P6R8T0X1B\"}",
            occurredAt = Instant.parse("2026-07-21T13:00:00Z"),
        )

        outboxPublisher.publish(message)

        val received = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributeNames("All")
                .maxNumberOfMessages(1)
                .waitTimeSeconds(2)
                .build(),
        ).messages().single()

        val body = objectMapper.readTree(received.body())
        assertThat(body["id"].asText()).isEqualTo(message.id)
        assertThat(body["eventType"].asText()).isEqualTo("PartyCreated")
        assertThat(received.messageAttributes()["eventId"]?.stringValue()).isEqualTo(message.id)
        assertThat(received.messageAttributes()["aggregateId"]?.stringValue()).isEqualTo(message.aggregateId)
    }

    companion object {
        @Container
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        private val localstack = LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8.1"),
        ).withServices(LocalStackContainer.Service.SQS)

        private lateinit var sqsClient: SqsClient
        private lateinit var queueUrl: String

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            sqsClient = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .region(Region.of(localstack.region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey),
                    ),
                )
                .build()
            queueUrl = sqsClient.createQueue { it.queueName("customer-analysis-outbox-test") }.queueUrl()

            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("pld.integration.sqs.enabled") { "true" }
            registry.add("pld.integration.sqs.region", localstack::getRegion)
            registry.add("pld.integration.sqs.endpoint") {
                localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString()
            }
            registry.add("pld.integration.sqs.access-key-id", localstack::getAccessKey)
            registry.add("pld.integration.sqs.secret-access-key", localstack::getSecretKey)
            registry.add("pld.integration.sqs.outbox-queue-url") { queueUrl }
        }
    }
}
