package br.com.pld.customeranalysis.transactionprojection

import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import br.com.pld.customeranalysis.integration.InboxProcessingResult
import br.com.pld.customeranalysis.party.CreatePartyCommand
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartyType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.nio.file.Files
import java.nio.file.Path
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = [
    "pld.transaction-signals.case-trigger-mode=MANUAL_REVIEW_LIVE",
    "pld.transaction-signals.manual-review-cutover-at=2020-01-01T00:00:00Z",
])
class ManualReviewLiveIntegrationTest {
    @Autowired
    private lateinit var consumer: ManualReviewConsumer

    @Autowired
    private lateinit var signalConsumer: TransactionSignalConsumer

    @Autowired
    private lateinit var partyService: PartyService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table manual_review_request, transaction_signal_projection, transaction_evaluation_projection, case_source, pld_case, inbox_event, outbox_event, timeline_entry, analysis_cycle, party_snapshot, party restart identity cascade",
        )
    }

    @Test
    fun `manual review is the only idempotent live case trigger`() {
        val partyId = partyService.create(
            CreatePartyCommand(
                partyType = PartyType.PERSON,
                officialName = "Manual Review Live",
                sourceSystem = "manual",
                actor = Actor("analyst-1", ActorRole.ANALYST),
                correlationId = "corr-party",
            ),
        ).partyId
        val event = fixture()
            .replace("pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D", partyId)

        assertThat(signalConsumer.consume(signalFixture().replace(
            "pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D",
            partyId,
        ))).isEqualTo(InboxProcessingResult.PROCESSED)
        assertThat(jdbcTemplate.queryForObject("select count(*) from pld_case", Long::class.java)).isZero()

        assertThat(consumer.consume(event)).isEqualTo(InboxProcessingResult.PROCESSED)
        val redeliveryWithAnotherEventId = event.replace(
            "01J6ZK7Q3W8K0M2N4P6R8T0V6A",
            "01J6ZK7Q3W8K0M2N4P6R8T0V7A",
        )
        assertThat(consumer.consume(redeliveryWithAnotherEventId)).isEqualTo(InboxProcessingResult.PROCESSED)

        assertThat(jdbcTemplate.queryForObject("select count(*) from pld_case", Long::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("select count(*) from case_source", Long::class.java)).isEqualTo(2)
        assertThat(jdbcTemplate.queryForList("select source_type from case_source order by source_type", String::class.java))
            .containsExactly("ManualReviewRequest", "TransactionSignal")
        assertThat(jdbcTemplate.queryForObject("select effect_status from manual_review_request", String::class.java))
            .isEqualTo("CASE_EFFECT_APPLIED")
    }

    @Test
    fun `review before signal attaches only the listed signal when it arrives`() {
        val partyId = createParty("Review Before Signal")
        val review = fixture().replace("pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D", partyId)

        consumer.consume(review)
        assertThat(jdbcTemplate.queryForObject("select count(*) from case_source", Long::class.java)).isEqualTo(1)
        signalConsumer.consume(signalFixture().replace("pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D", partyId))

        assertThat(jdbcTemplate.queryForList("select source_type from case_source order by source_type", String::class.java))
            .containsExactly("ManualReviewRequest", "TransactionSignal")
    }

    @Test
    fun `business redelivery rejects a conflicting payload`() {
        val partyId = createParty("Conflicting Review")
        val event = fixture().replace("pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D", partyId)
        consumer.consume(event)

        val conflicting = event
            .replace("01J6ZK7Q3W8K0M2N4P6R8T0V6A", "01J6ZK7Q3W8K0M2N4P6R8T0V7A")
            .replace("POLICY_REQUIRES_REVIEW", "INSUFFICIENT_EVIDENCE")

        assertThatThrownBy { consumer.consume(conflicting) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("conflicts with payload")
        assertThat(jdbcTemplate.queryForObject("select count(*) from pld_case", Long::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("select count(*) from inbox_event", Long::class.java)).isEqualTo(1)
    }

    @Test
    fun `delayed listed signal stays on the review case after status changes`() {
        val partyId = createParty("Stable Review Case")
        consumer.consume(fixture().replace("pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D", partyId))
        val caseId = jdbcTemplate.queryForObject("select id from pld_case", String::class.java)!!
        jdbcTemplate.update("update pld_case set status = 'PENDING_APPROVAL' where id = ?", caseId)

        signalConsumer.consume(signalFixture().replace("pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D", partyId))

        assertThat(jdbcTemplate.queryForObject("select count(*) from pld_case", Long::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForList("select distinct case_id from case_source", String::class.java))
            .containsExactly(caseId)
    }

    @Test
    fun `reconciliation recovers a pending live effect`() {
        val partyId = createParty("Pending Live Review")
        val event = fixture().replace("pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D", partyId)
        insertProjectedRequest(event, partyId, "MANUAL_REVIEW_LIVE", "CASE_EFFECT_PENDING")

        assertThat(consumer.reconcilePending()).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("select count(*) from pld_case", Long::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("select effect_status from manual_review_request", String::class.java))
            .isEqualTo("CASE_EFFECT_APPLIED")
    }

    @Test
    fun `reconciliation applies request projected before cutover`() {
        val partyId = createParty("Shadow Cutover")
        val event = fixture().replace("pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D", partyId)
        insertProjectedRequest(event, partyId, "SHADOW", "PROJECTED_SHADOW")

        assertThat(consumer.reconcilePending()).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("select count(*) from pld_case", Long::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("select effect_status from manual_review_request", String::class.java))
            .isEqualTo("CASE_EFFECT_APPLIED")
    }

    private fun insertProjectedRequest(event: String, partyId: String, triggerMode: String, effectStatus: String) {
        val root = objectMapper.readTree(event)
        val payload = root.requiredObject("payload")
        jdbcTemplate.update(
            """
                INSERT INTO manual_review_request (
                    source_system, source_request_id, event_id, evaluation_id, party_id, transaction_id,
                    signal_ids, reason_codes, recommended_route, grouping_policy_version_applied,
                    trigger_mode, effect_status, payload, occurred_at, received_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """.trimIndent(),
            root.requiredText("producer"),
            payload.requiredText("reviewRequestId"),
            root.requiredText("eventId"),
            payload.requiredText("evaluationId"),
            partyId,
            payload.requiredText("transactionId"),
            payload.requiredNode("signalIds").toString(),
            payload.requiredNode("reasonCodes").toString(),
            payload.requiredText("recommendedRoute"),
            br.com.pld.customeranalysis.casemanagement.CaseService.GROUPING_POLICY_VERSION,
            triggerMode,
            effectStatus,
            event,
            Timestamp.from(Instant.parse(root.requiredText("occurredAt"))),
            Timestamp.from(Instant.now()),
        )
    }

    private fun createParty(name: String): String = partyService.create(
        CreatePartyCommand(
            partyType = PartyType.PERSON,
            officialName = name,
            sourceSystem = "manual",
            actor = Actor("analyst-1", ActorRole.ANALYST),
            correlationId = "corr-party-$name",
        ),
    ).partyId

    private fun fixture(): String = Files.readString(
        Path.of(System.getProperty("user.dir"))
            .resolveSibling("pld-platform-docs/schemas/v1/fixtures/ManualReviewRequestedV2.json"),
    )

    private fun signalFixture(): String = Files.readString(
        Path.of(System.getProperty("user.dir"))
            .resolveSibling("pld-platform-docs/schemas/v1/fixtures/TransactionSignalDetected.json"),
    )

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
