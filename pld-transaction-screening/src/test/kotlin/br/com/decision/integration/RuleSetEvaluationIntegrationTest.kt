package br.com.decision.integration

import br.com.decision.application.usecase.EvaluateRuleSetCommand
import br.com.decision.application.usecase.EvaluateRuleSetUseCase
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.EvaluationOutcome
import br.com.decision.domain.model.EvaluationStatus
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.port.CustomerRiskPort
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.util.UUID

/**
 * Ruleset congelado multi-regra: uma avaliação cobre todas as regras ativas,
 * cada regra acionada gera um sinal distinto e a avaliação é idempotente.
 */
@SpringBootTest(properties = ["pld.integration.transaction-signals.enabled=true"])
@Testcontainers
class RuleSetEvaluationIntegrationTest {

    @Autowired
    private lateinit var useCase: EvaluateRuleSetUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val partyId = "pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D"

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute(
            """
            TRUNCATE transaction_evaluation_execution, transaction_evaluation, decision_execution,
                integration_outbox, alert, rule_execution, screening_intake, transaction_identity
            RESTART IDENTITY CASCADE
            """.trimIndent(),
        )
        jdbcTemplate.update("DELETE FROM rule_configuration WHERE rule_id IN (SELECT id FROM rule_definition WHERE code = ?)", SECOND_RULE)
        jdbcTemplate.update("DELETE FROM rule_definition WHERE code = ?", SECOND_RULE)

        ensureKeywordConfiguration()
        insertSecondRule()
    }

    @Test
    fun `two triggered rules produce distinct signals in one evaluation`() {
        val result = useCase.execute(command())

        assertThat(result.executionStatus).isEqualTo(EvaluationStatus.COMPLETED)
        assertThat(result.evaluationOutcome).isEqualTo(EvaluationOutcome.SIGNAL_RAISED)
        assertThat(result.reviewRequired).isTrue()
        assertThat(result.rulesetVersion).isEqualTo("$SECOND_RULE:1;KEYWORD_SCREENING:1")
        assertThat(result.ruleOutcomes).hasSize(2)
        assertThat(result.ruleOutcomes.map { it.ruleCode }).containsExactly(SECOND_RULE, "KEYWORD_SCREENING")
        assertThat(result.ruleOutcomes.all { it.signalRaised }).isTrue()

        val evaluationId = result.evaluationId!!
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM transaction_evaluation",
            Long::class.java,
        )).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM transaction_evaluation_execution WHERE evaluation_id = ?",
            Long::class.java,
            evaluationId,
        )).isEqualTo(2)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM decision_execution WHERE evaluation_id = ?",
            Long::class.java,
            evaluationId,
        )).isEqualTo(2)

        val outbox = jdbcTemplate.queryForList(
            "SELECT event_type, event_version, envelope::text AS envelope FROM integration_outbox ORDER BY event_type",
        )
        assertThat(outbox.map { it["event_type"] }).containsExactlyInAnyOrder(
            "TransactionEvaluationCompleted",
            "TransactionSignalDetected",
            "TransactionSignalDetected",
            "ManualReviewRequested",
        )

        val signalIds = outbox.filter { it["event_type"] == "TransactionSignalDetected" }
            .map { objectMapper.readTree(it["envelope"] as String).at("/payload/signalId").asText() }
        assertThat(signalIds).hasSize(2).doesNotHaveDuplicates()
        val review = outbox.single { it["event_type"] == "ManualReviewRequested" }
        val reviewSignals = objectMapper.readTree(review["envelope"] as String)
            .at("/payload/signalIds").map { it.asText() }
        assertThat(reviewSignals).containsExactlyInAnyOrderElementsOf(signalIds)

        val completion = outbox.single { it["event_type"] == "TransactionEvaluationCompleted" }
        val completionEnvelope = completion["envelope"] as String
        val completionJson = objectMapper.readTree(completionEnvelope) as com.fasterxml.jackson.databind.node.ObjectNode
        completionJson.put("publishedAt", "2026-07-20T15:30:01Z")
        val errors = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(
                Path.of(System.getProperty("user.dir"))
                    .resolveSibling("pld-platform-docs/schemas/v1/TransactionEvaluationCompletedV2.schema.json")
                    .toUri(),
            )
            .validate(completionJson)
        assertThat(errors).isEmpty()
        val completionPayload = objectMapper.readTree(completionEnvelope).at("/payload")
        assertThat(completionPayload.at("/rulesetVersion").asText()).isEqualTo("$SECOND_RULE:1;KEYWORD_SCREENING:1")
        assertThat(completionPayload.at("/rulesTriggered")).hasSize(2)
    }

    @Test
    fun `same input event replays the same multi rule evaluation`() {
        val first = useCase.execute(command())
        val second = useCase.execute(command())

        assertThat(second.evaluationId).isEqualTo(first.evaluationId)
        assertThat(second.ruleOutcomes).hasSize(2)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM transaction_evaluation",
            Long::class.java,
        )).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM integration_outbox WHERE event_type = 'TransactionSignalDetected'",
            Long::class.java,
        )).isEqualTo(2)
    }

    private fun command(): EvaluateRuleSetCommand = EvaluateRuleSetCommand(
        transactionId = TransactionId("TX-RULESET-001"),
        customerId = CustomerId(partyId),
        detectionResult = DetectionResult(
            matched = true,
            matches = listOf(DetectionMatch(term = "lavagem", category = "AML")),
        ),
        sourceSystem = "LEGACY_HTTP",
        inputEventId = "01J6ZK7Q3W8K0M2N4P6R8T0V6A",
        correlationId = "corr-ruleset-1",
    )

    private fun ensureKeywordConfiguration() {
        val ruleId = jdbcTemplate.queryForObject(
            "SELECT id FROM rule_definition WHERE code = 'KEYWORD_SCREENING'",
            UUID::class.java,
        )!!
        jdbcTemplate.update("DELETE FROM rule_configuration WHERE rule_id = ?", ruleId)
        jdbcTemplate.update(
            """
            INSERT INTO rule_configuration (id, rule_id, expressions, actions, active, draft, current_version, created_by, created_at, updated_at)
            VALUES (?, ?, ?::jsonb, ?::jsonb, TRUE, FALSE, 1, 'ruleset-test', NOW(), NOW())
            """.trimIndent(),
            UUID.randomUUID(),
            ruleId,
            """
            [
                {"type":"CONDITION","factName":"keywordMatched","operator":"EQUALS","expectedValue":{"type":"BOOLEAN","value":true}},
                {"type":"CONDITION","factName":"customerRisk","operator":"GREATER_THAN_OR_EQUAL","expectedValue":{"type":"ENUM","value":"MR"}}
            ]
            """.trimIndent(),
            """["GENERATE_ALERT","REVIEW"]""",
        )
    }

    private fun insertSecondRule() {
        val ruleId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO rule_definition (id, code, name, description, context, category, supported_facts, supported_actions, status)
            VALUES (?, ?, 'Customer High Risk', 'Risco alto do cliente', 'SCREENING', 'AML',
                '["customerRisk"]'::jsonb, '["GENERATE_ALERT"]'::jsonb, 'ACTIVE')
            """.trimIndent(),
            ruleId,
            SECOND_RULE,
        )
        jdbcTemplate.update(
            """
            INSERT INTO rule_configuration (id, rule_id, expressions, actions, active, draft, current_version, created_by, created_at, updated_at)
            VALUES (?, ?, ?::jsonb, ?::jsonb, TRUE, FALSE, 1, 'ruleset-test', NOW(), NOW())
            """.trimIndent(),
            UUID.randomUUID(),
            ruleId,
            """
            [
                {"type":"CONDITION","factName":"customerRisk","operator":"EQUALS","expectedValue":{"type":"ENUM","value":"AR"}}
            ]
            """.trimIndent(),
            """["GENERATE_ALERT"]""",
        )
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun mockCustomerRiskPort(): CustomerRiskPort = object : CustomerRiskPort {
            override fun getCustomerRisk(customerId: CustomerId): CustomerRisk? =
                if (customerId.value.startsWith("pty_")) CustomerRisk.AR else CustomerRisk.MR
        }
    }

    companion object {
        private const val SECOND_RULE = "CUSTOMER_HIGH_RISK"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("keyword_screening_ruleset_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
