package br.com.pld.customeranalysis.casemanagement

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TransactionSignalCaseIntegrationTest {

    @Autowired
    private lateinit var partyService: PartyService


    @Autowired
    private lateinit var transactionSignalConsumer: TransactionSignalConsumer

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table case_source, pld_case, inbox_event, outbox_event, timeline_entry, analysis_cycle, party_snapshot, party restart identity cascade",
        )
    }

    @Test
    fun `opens case from human routed transaction signal and exposes it in queue`() {
        val partyId = createParty()

        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))

        val cases = cases()
        assertThat(cases).hasSize(1)
        assertThat(cases.single().partyId).isEqualTo(partyId)
        assertThat(cases.single().origin).isEqualTo("TRANSACTION_ALERT")
        assertThat(cases.single().status).isEqualTo("OPEN")
        assertThat(cases.single().sourceCount).isEqualTo(1)
        assertThat(caseSourceIds()).containsExactly("sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F")
        assertThat(timelineEntryTypes(partyId)).containsExactly(
            "PARTY_CREATED",
            "TRANSACTION_SIGNAL_DETECTED",
            "CASE_CREATED",
        )
        assertThat(outboxEventTypes()).containsExactly("PartyCreated", "CaseStatusChanged")

        mockMvc.get("/v1/cases")
            .andExpect {
                status { isOk() }
                jsonPath("$.cases.length()") { value(1) }
                jsonPath("$.cases[0].caseId") { value(cases.single().caseId) }
                jsonPath("$.cases[0].partyId") { value(partyId) }
                jsonPath("$.cases[0].origin") { value("TRANSACTION_ALERT") }
                jsonPath("$.cases[0].status") { value("OPEN") }
            }

        mockMvc.get("/v1/cases/{caseId}", cases.single().caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.case.caseId") { value(cases.single().caseId) }
                jsonPath("$.case.partyId") { value(partyId) }
                jsonPath("$.party.currentSnapshot.officialName") { value("Maria Exemplo da Silva") }
                jsonPath("$.sources.length()") { value(1) }
                jsonPath("$.sources[0].sourceId") { value("sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F") }
                jsonPath("$.sources[0].sourceType") { value("TransactionSignal") }
                jsonPath("$.sources[0].severity") { value("HIGH") }
                jsonPath("$.timeline.entries.length()") { value(3) }
                jsonPath("$.timeline.entries[2].entryType") { value("CASE_CREATED") }
            }
    }

    @Test
    fun `returns not found for unknown case`() {
        mockMvc.get("/v1/cases/{caseId}", "cas_01J6ZK7Q3W8K0M2N4P6R8T0BAD")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `groups another transaction signal into existing open party case`() {
        val partyId = createParty()

        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))
        transactionSignalConsumer.consume(
            transactionSignalDetectedEvent(
                partyId = partyId,
                eventId = "01J6ZK7Q3W8K0M2N4P6R8T0W3A",
                signalId = "sig_01J6ZK7Q3W8K0M2N4P6R8T0W3B",
            ),
        )

        assertThat(cases()).hasSize(1)
        assertThat(cases().single().sourceCount).isEqualTo(2)
        assertThat(caseSourceIds()).containsExactly(
            "sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F",
            "sig_01J6ZK7Q3W8K0M2N4P6R8T0W3B",
        )
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

    private fun cases(): List<CaseRow> = jdbcTemplate.query(
        "select id, party_id, origin, status, source_count from pld_case order by created_at, id",
        { rs, _ ->
            CaseRow(
                caseId = rs.getString("id"),
                partyId = rs.getString("party_id"),
                origin = rs.getString("origin"),
                status = rs.getString("status"),
                sourceCount = rs.getInt("source_count"),
            )
        },
    )

    private fun caseSourceIds(): List<String> = jdbcTemplate.queryForList(
        "select source_id from case_source order by attached_at, id",
        String::class.java,
    )

    private fun timelineEntryTypes(partyId: String): List<String> = jdbcTemplate.queryForList(
        "select entry_type from timeline_entry where party_id = ? order by recorded_at, id",
        String::class.java,
        partyId,
    )

    private fun outboxEventTypes(): List<String> = jdbcTemplate.queryForList(
        "select event_type from outbox_event order by occurred_at, id",
        String::class.java,
    )

    private fun transactionSignalDetectedEvent(
        partyId: String,
        eventId: String = "01J6ZK7Q3W8K0M2N4P6R8T0V2A",
        signalId: String = "sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F",
    ): String = """
        {
          "eventId": "$eventId",
          "eventType": "TransactionSignalDetected",
          "eventVersion": 1,
          "occurredAt": "2026-07-20T15:30:00Z",
          "publishedAt": "2026-07-20T15:30:01Z",
          "producer": "pld-transaction-screening",
          "correlationId": "01J6ZK7Q3W8K0M2N4P6R8T0V2B",
          "causationId": "01J6ZK7Q3W8K0M2N4P6R8T0V2C",
          "actor": {"type": "SYSTEM", "id": "rule-engine"},
          "subject": {"partyId": "$partyId", "accountId": "acc_01J6ZK7Q3W8K0M2N4P6R8T0V2E", "analysisCycleId": null, "caseId": null},
          "dataClassification": "CONFIDENTIAL",
          "payload": {
            "signalId": "$signalId",
            "evaluationId": "evl_01J6ZK7Q3W8K0M2N4P6R8T0V2G",
            "transactionId": "txn_01J6ZK7Q3W8K0M2N4P6R8T0V2H",
            "signalType": "RULE_MATCH",
            "severity": "HIGH",
            "ruleMatches": [{"ruleCode": "PIX-009", "ruleVersion": 4, "explanationCode": "AMOUNT_OUTSIDE_PROFILE"}],
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

data class CaseRow(
    val caseId: String,
    val partyId: String,
    val origin: String,
    val status: String,
    val sourceCount: Int,
)
