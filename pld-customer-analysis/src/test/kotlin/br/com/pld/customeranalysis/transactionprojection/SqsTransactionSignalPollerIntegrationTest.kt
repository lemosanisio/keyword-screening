package br.com.pld.customeranalysis.transactionprojection

import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import br.com.pld.customeranalysis.party.CreatePartyCommand
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
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
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = [
        "pld.integration.sqs.enabled=true",
        "pld.integration.sqs.inbound-enabled=true",
        "pld.integration.sqs.inbound-initial-delay=PT1H",
        "pld.integration.sqs.inbound-fixed-delay=PT1H",
        "pld.integration.sqs.inbound-max-messages=10",
    ],
)
class SqsTransactionSignalPollerIntegrationTest {

    @Autowired
    private lateinit var partyService: PartyService

    @Autowired
    private lateinit var poller: SqsTransactionSignalPoller

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table case_source, pld_case, inbox_event, outbox_event, timeline_entry, analysis_cycle, party_snapshot, party restart identity cascade",
        )
        purgeQueue()
    }

    @Test
    fun `polls transaction signal from sqs and deletes message after processing`() {
        val partyId = createParty()
        sqsClient.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(transactionSignalDetectedEvent(partyId))
                .build(),
        )

        poller.poll()

        assertThat(timelineEntryTypes(partyId)).containsExactly(
            "PARTY_CREATED",
            "TRANSACTION_SIGNAL_DETECTED",
            "CASE_CREATED",
        )
        assertThat(inboxStatuses()).containsExactly("PROCESSED")
        assertThat(visibleMessageCount()).isEqualTo(0)
    }

    @Test
    fun `deletes duplicated transaction signal after inbox detects duplicate`() {
        val partyId = createParty()
        val event = transactionSignalDetectedEvent(partyId)

        sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(event).build())
        poller.poll()
        sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(event).build())
        poller.poll()

        assertThat(timelineEntryTypes(partyId)).containsExactly(
            "PARTY_CREATED",
            "TRANSACTION_SIGNAL_DETECTED",
            "CASE_CREATED",
        )
        assertThat(inboxStatuses()).containsExactly("PROCESSED")
        assertThat(visibleMessageCount()).isEqualTo(0)
    }

    private fun createParty(): String = partyService.create(
        CreatePartyCommand(
            partyType = PartyType.PERSON,
            officialName = "Maria Exemplo da Silva",
            sourceSystem = "manual",
            actor = Actor(id = "analyst-1", role = ActorRole.ANALYST),
            correlationId = "corr-party-create",
        ),
    ).partyId

    private fun timelineEntryTypes(partyId: String): List<String> = jdbcTemplate.queryForList(
        "select entry_type from timeline_entry where party_id = ? order by recorded_at, id",
        String::class.java,
        partyId,
    )

    private fun inboxStatuses(): List<String> = jdbcTemplate.queryForList(
        "select status from inbox_event order by received_at",
        String::class.java,
    )

    private fun visibleMessageCount(): Int {
        val response = sqsClient.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build(),
        )

        return response.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES]?.toInt() ?: 0
    }

    private fun purgeQueue() {
        repeat(10) {
            val messages = sqsClient.receiveMessage { builder ->
                builder.queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(0)
            }.messages()

            messages.forEach { message ->
                sqsClient.deleteMessage { builder ->
                    builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle())
                }
            }
        }
    }

    private fun transactionSignalDetectedEvent(partyId: String): String = """
        {
          "eventId": "01J6ZK7Q3W8K0M2N4P6R8T0V2A",
          "eventType": "TransactionSignalDetected",
          "eventVersion": 1,
          "occurredAt": "2026-07-20T15:30:00Z",
          "publishedAt": "2026-07-20T15:30:01Z",
          "producer": "pld-transaction-screening",
          "correlationId": "01J6ZK7Q3W8K0M2N4P6R8T0V2B",
          "causationId": "01J6ZK7Q3W8K0M2N4P6R8T0V2C",
          "actor": {
            "type": "SYSTEM",
            "id": "rule-engine"
          },
          "subject": {
            "partyId": "$partyId",
            "accountId": "acc_01J6ZK7Q3W8K0M2N4P6R8T0V2E",
            "analysisCycleId": null,
            "caseId": null
          },
          "dataClassification": "CONFIDENTIAL",
          "payload": {
            "signalId": "sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F",
            "evaluationId": "evl_01J6ZK7Q3W8K0M2N4P6R8T0V2G",
            "transactionId": "txn_01J6ZK7Q3W8K0M2N4P6R8T0V2H",
            "signalType": "RULE_MATCH",
            "severity": "HIGH",
            "ruleMatches": [
              {
                "ruleCode": "PIX-009",
                "ruleVersion": 4,
                "explanationCode": "AMOUNT_OUTSIDE_PROFILE"
              }
            ],
            "riskProfileVersion": 7,
            "recommendedRoute": "DERIVED_TO_ANALYST"
          }
        }
    """.trimIndent()

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
            queueUrl = sqsClient.createQueue { it.queueName("customer-analysis-transaction-signals-test") }.queueUrl()

            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("pld.integration.sqs.region", localstack::getRegion)
            registry.add("pld.integration.sqs.endpoint") {
                localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString()
            }
            registry.add("pld.integration.sqs.access-key-id", localstack::getAccessKey)
            registry.add("pld.integration.sqs.secret-access-key", localstack::getSecretKey)
            registry.add("pld.integration.sqs.outbox-queue-url") { queueUrl }
            registry.add("pld.integration.sqs.transaction-signals-queue-url") { queueUrl }
        }
    }
}
