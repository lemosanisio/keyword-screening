package br.com.decision.integration

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.port.CustomerRiskPort
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Integration test para o fluxo completo de decisão:
 * DetectionEvent → DecisionService → DecisionExecution persistido → DecisionMadeEvent → Alert criado
 *
 * Validates: Requirements 4.7, 9.1, 10.2, 12.2
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DecisionFlowIntegrationTest {

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var customerRiskPort: CustomerRiskPort

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("keyword_screening_test")
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

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun mockCustomerRiskPort(): CustomerRiskPort {
            return object : CustomerRiskPort {
                override fun getCustomerRisk(customerId: CustomerId): CustomerRisk? {
                    return when (customerId.value) {
                        "CUST-HIGH-RISK" -> CustomerRisk.AR
                        "CUST-LOW-RISK" -> CustomerRisk.BR
                        else -> CustomerRisk.MR
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("full flow: DetectionEvent with keywordMatched=true and high risk customer generates alert")
    fun `full flow DetectionEvent with keywordMatched true and high risk customer generates alert`() {
        // Arrange: create an active RuleConfiguration for KEYWORD_SCREENING
        val ruleId = getRuleDefinitionId("KEYWORD_SCREENING")
        insertActiveRuleConfiguration(ruleId)

        val transactionId = "TX-FLOW-${UUID.randomUUID()}"
        val customerId = "CUST-HIGH-RISK"

        val detectionEvent = DetectionEvent(
            eventId = EventId(UUID.randomUUID().toString()),
            traceId = TraceId(UUID.randomUUID().toString()),
            timestamp = Instant.now(),
            transactionId = TransactionId(transactionId),
            customerId = CustomerId(customerId),
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            detectionResult = DetectionResult(
                matched = true,
                matches = listOf(DetectionMatch(term = "lavagem", category = "AML"))
            )
        )

        // Act: publish DetectionEvent — triggers the full chain
        applicationEventPublisher.publishEvent(detectionEvent)

        // Assert: DecisionExecution persisted in DB
        val execCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decision_execution WHERE transaction_id = ?",
            Long::class.java,
            transactionId
        )
        assertEquals(1L, execCount)

        // Verify decision is ALERT
        val decision = jdbcTemplate.queryForObject(
            "SELECT decision FROM decision_execution WHERE transaction_id = ?",
            String::class.java,
            transactionId
        )
        assertEquals("ALERT", decision)

        // Verify execution metadata
        val executionTimeMs = jdbcTemplate.queryForObject(
            "SELECT execution_time_ms FROM decision_execution WHERE transaction_id = ?",
            Long::class.java,
            transactionId
        )
        assertNotNull(executionTimeMs)
        assertTrue(executionTimeMs!! >= 0L)

        // Assert: Alert created in DB (via DecisionMadeEvent AFTER_COMMIT listener)
        val alertCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM alert WHERE transaction_id = ?",
            Long::class.java,
            transactionId
        )
        assertEquals(1L, alertCount)

        // Verify alert status is OPEN
        val alertStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM alert WHERE transaction_id = ?",
            String::class.java,
            transactionId
        )
        assertEquals("OPEN", alertStatus)
    }

    @Test
    @DisplayName("idempotency: publishing same DetectionEvent twice produces only 1 DecisionExecution")
    fun `idempotency publishing same DetectionEvent twice produces only 1 DecisionExecution`() {
        // Arrange
        val ruleId = getRuleDefinitionId("KEYWORD_SCREENING")
        ensureActiveRuleConfiguration(ruleId)

        val transactionId = "TX-IDEMP-${UUID.randomUUID()}"
        val customerId = "CUST-HIGH-RISK"

        val detectionEvent = DetectionEvent(
            eventId = EventId(UUID.randomUUID().toString()),
            traceId = TraceId(UUID.randomUUID().toString()),
            timestamp = Instant.now(),
            transactionId = TransactionId(transactionId),
            customerId = CustomerId(customerId),
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            detectionResult = DetectionResult(
                matched = true,
                matches = listOf(DetectionMatch(term = "terrorismo", category = "TERRORISM"))
            )
        )

        // Act: publish the same event twice
        applicationEventPublisher.publishEvent(detectionEvent)

        val duplicateEvent = detectionEvent.copy(
            eventId = EventId(UUID.randomUUID().toString()),
            traceId = TraceId(UUID.randomUUID().toString())
        )
        applicationEventPublisher.publishEvent(duplicateEvent)

        // Assert: only 1 DecisionExecution in DB (UNIQUE constraint on transaction_id + rule_id)
        val execCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decision_execution WHERE transaction_id = ?",
            Long::class.java,
            transactionId
        )
        assertEquals(1L, execCount)

        // Assert: only 1 Alert in DB (idempotent)
        val alertCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM alert WHERE transaction_id = ?",
            Long::class.java,
            transactionId
        )
        assertEquals(1L, alertCount)
    }

    @Test
    @DisplayName("seed data: entity_definition, fact_definition, rule_definition tables have MVP data after Flyway")
    fun `seed data tables have MVP data after Flyway`() {
        // Verify Entity Definitions
        val entityCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM entity_definition WHERE name IN ('Risk', 'Screening')",
            Long::class.java
        )
        assertEquals(2L, entityCount)

        // Verify Fact Definitions
        val factNames = jdbcTemplate.queryForList(
            "SELECT name FROM fact_definition WHERE enabled = TRUE ORDER BY name",
            String::class.java
        )
        assertEquals(2, factNames.size)
        assertEquals(listOf("customerRisk", "keywordMatched"), factNames)

        // Verify Rule Definition
        val ruleCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rule_definition WHERE code = 'KEYWORD_SCREENING' AND status = 'ACTIVE'",
            Long::class.java
        )
        assertEquals(1L, ruleCount)

        // Verify supported facts for KEYWORD_SCREENING
        val supportedFacts = jdbcTemplate.queryForObject(
            "SELECT supported_facts::text FROM rule_definition WHERE code = 'KEYWORD_SCREENING'",
            String::class.java
        )
        assertNotNull(supportedFacts)
        assertTrue(supportedFacts!!.contains("keywordMatched"))
        assertTrue(supportedFacts.contains("customerRisk"))
    }

    private fun getRuleDefinitionId(code: String): UUID {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM rule_definition WHERE code = ?",
            UUID::class.java,
            code
        ) ?: error("RuleDefinition not found for code: $code")
    }

    private fun insertActiveRuleConfiguration(ruleId: UUID): UUID {
        jdbcTemplate.update(
            "DELETE FROM rule_configuration WHERE rule_id = ?",
            ruleId
        )

        val configId = UUID.randomUUID()
        val expressions = """
            [
                {"type":"CONDITION","factName":"keywordMatched","operator":"EQUALS","expectedValue":{"type":"BOOLEAN","value":true}},
                {"type":"CONDITION","factName":"customerRisk","operator":"GREATER_THAN_OR_EQUAL","expectedValue":{"type":"ENUM","value":"MR"}}
            ]
        """.trimIndent()
        val actions = """["GENERATE_ALERT"]"""

        jdbcTemplate.update(
            """
            INSERT INTO rule_configuration (id, rule_id, expressions, actions, active, draft, current_version, created_by, created_at, updated_at)
            VALUES (?, ?, ?::jsonb, ?::jsonb, TRUE, FALSE, 1, 'integration-test', NOW(), NOW())
            """.trimIndent(),
            configId, ruleId, expressions, actions
        )

        return configId
    }

    private fun ensureActiveRuleConfiguration(ruleId: UUID) {
        val existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rule_configuration WHERE rule_id = ? AND active = TRUE",
            Long::class.java,
            ruleId
        )
        if (existing == 0L) {
            insertActiveRuleConfiguration(ruleId)
        }
    }
}
