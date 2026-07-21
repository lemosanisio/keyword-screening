package br.com.pld.customeranalysis.integration

import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import br.com.pld.customeranalysis.party.CreatePartyCommand
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartyType
import br.com.pld.customeranalysis.transactionprojection.TransactionSignalConsumer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class TransactionSignalConsumerIntegrationTest {

    @Autowired
    private lateinit var partyService: PartyService

    @Autowired
    private lateinit var transactionSignalConsumer: TransactionSignalConsumer

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table case_source, pld_case, inbox_event, outbox_event, timeline_entry, analysis_cycle, party_snapshot, party restart identity cascade",
        )
    }

    @Test
    fun `records transaction signal once when duplicated event is consumed`() {
        val partyId = partyService.create(
            CreatePartyCommand(
                partyType = PartyType.PERSON,
                officialName = "Maria Exemplo da Silva",
                sourceSystem = "manual",
                actor = Actor(id = "analyst-1", role = ActorRole.ANALYST),
                correlationId = "corr-party-create",
            ),
        ).partyId
        val event = transactionSignalDetectedEvent(partyId)

        val first = transactionSignalConsumer.consume(event)
        val duplicate = transactionSignalConsumer.consume(event)

        assertThat(first).isEqualTo(InboxProcessingResult.PROCESSED)
        assertThat(duplicate).isEqualTo(InboxProcessingResult.DUPLICATE)
        assertThat(timelineEntries(partyId)).containsExactly(
            TimelineRow("PARTY_CREATED", "Party", partyId),
            TimelineRow("TRANSACTION_SIGNAL_DETECTED", "TransactionSignal", "sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F"),
            TimelineRow("CASE_CREATED", "Case", caseId()),
        )
        assertThat(inboxStatuses()).containsExactly("PROCESSED")
    }

    private fun caseId(): String = jdbcTemplate.queryForObject(
        "select id from pld_case",
        String::class.java,
    ) ?: error("case not found")

    private fun timelineEntries(partyId: String): List<TimelineRow> = jdbcTemplate.query(
        "select entry_type, object_type, object_id from timeline_entry where party_id = ? order by recorded_at, id",
        { rs, _ ->
            TimelineRow(
                entryType = rs.getString("entry_type"),
                objectType = rs.getString("object_type"),
                objectId = rs.getString("object_id"),
            )
        },
        partyId,
    )

    private fun inboxStatuses(): List<String> = jdbcTemplate.queryForList(
        "select status from inbox_event order by received_at",
        String::class.java,
    )

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

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}

data class TimelineRow(
    val entryType: String,
    val objectType: String,
    val objectId: String,
)
