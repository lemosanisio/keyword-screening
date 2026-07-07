package br.com.decision.integration

import br.com.alert.infrastructure.output.persistence.repository.AlertJpaRepository
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.port.CustomerRiskPort
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

/**
 * Integration test para verificar que o Alert Context cria alertas corretamente
 * quando recebe DecisionMadeEvents publicados pelo Decision Context.
 *
 * Validates: Requirements 12.2, 12.3, 12.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertCreationIntegrationTest {

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var alertJpaRepository: AlertJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @MockBean
    private lateinit var customerRiskPort: CustomerRiskPort

    companion object {
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("keyword_screening_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    private fun buildDecisionMadeEvent(
        transactionId: String = "TX-ALERT-${UUID.randomUUID()}",
        customerId: String = "CUST-001",
        ruleId: UUID = UUID.randomUUID(),
        decision: Decision = Decision.ALERT,
        actions: List<Action> = listOf(Action.GENERATE_ALERT),
        traceId: String = "trace-${UUID.randomUUID()}"
    ): DecisionMadeEvent {
        val now = Instant.now()
        return DecisionMadeEvent(
            eventId = EventId(UUID.randomUUID().toString()),
            traceId = TraceId(traceId),
            timestamp = now,
            transactionId = TransactionId(transactionId),
            customerId = CustomerId(customerId),
            ruleId = RuleId(ruleId),
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            decision = decision,
            actions = actions,
            facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            ),
            matchedExpressions = listOf(
                ExpressionEvaluation(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true),
                    actualValue = FactValue.BooleanValue(true),
                    satisfied = true,
                    justification = "keywordMatched EQUALS true → true"
                )
            ),
            configurationVersion = ConfigurationVersion(1),
            executionTimeMs = 15,
            explanation = DecisionExplanation(
                traceId = TraceId(traceId),
                steps = emptyList()
            )
        )
    }

    /**
     * Publica o evento dentro de uma transação para que o @TransactionalEventListener(AFTER_COMMIT)
     * seja acionado após o commit.
     */
    private fun publishEventInTransaction(event: DecisionMadeEvent) {
        transactionTemplate.executeWithoutResult {
            applicationEventPublisher.publishEvent(event)
        }
    }

    @Test
    @DisplayName("DecisionMadeEvent with GENERATE_ALERT creates Alert with status OPEN in database")
    fun `DecisionMadeEvent with GENERATE_ALERT creates Alert with status OPEN in database`() {
        val ruleId = UUID.randomUUID()
        val transactionId = "TX-ALERT-CREATE-001"

        val event = buildDecisionMadeEvent(
            transactionId = transactionId,
            ruleId = ruleId,
            decision = Decision.ALERT,
            actions = listOf(Action.GENERATE_ALERT)
        )

        publishEventInTransaction(event)

        val alertEntity = alertJpaRepository.findByTransactionIdAndRuleId(transactionId, ruleId)

        assertNotNull(alertEntity)
        assertEquals(transactionId, alertEntity!!.transactionId)
        assertEquals(ruleId, alertEntity.ruleId)
        assertEquals("CUST-001", alertEntity.customerId)
        assertEquals("OPEN", alertEntity.status)
        assertEquals(1, alertEntity.configurationVersion)
        assertEquals(event.traceId.value, alertEntity.traceId)
    }

    @Test
    @DisplayName("DecisionMadeEvent with IGNORE action does not create any alert")
    fun `DecisionMadeEvent with IGNORE action does not create any alert`() {
        val ruleId = UUID.randomUUID()
        val transactionId = "TX-ALERT-IGNORE-001"

        val event = buildDecisionMadeEvent(
            transactionId = transactionId,
            ruleId = ruleId,
            decision = Decision.IGNORE,
            actions = listOf(Action.IGNORE)
        )

        publishEventInTransaction(event)

        val alertEntity = alertJpaRepository.findByTransactionIdAndRuleId(transactionId, ruleId)
        assertNull(alertEntity)
    }

    @Test
    @DisplayName("duplicate DecisionMadeEvent does not create duplicate alert (idempotency)")
    fun `duplicate DecisionMadeEvent does not create duplicate alert`() {
        val ruleId = UUID.randomUUID()
        val transactionId = "TX-ALERT-IDEMPOTENT-001"

        val event = buildDecisionMadeEvent(
            transactionId = transactionId,
            ruleId = ruleId,
            decision = Decision.ALERT,
            actions = listOf(Action.GENERATE_ALERT)
        )

        // Publish same event twice
        publishEventInTransaction(event)
        publishEventInTransaction(event)

        // Verify only one alert exists
        val alerts = alertJpaRepository.findByTransactionId(transactionId)
        assertEquals(1, alerts.size)
        assertEquals(ruleId, alerts[0].ruleId)
        assertEquals("OPEN", alerts[0].status)
    }
}
