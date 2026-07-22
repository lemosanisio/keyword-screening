package br.com.decision.integration

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.port.CustomerRiskPort
import br.com.integration.IntegrationEventPublisher
import br.com.integration.IntegrationOutboxEntity
import br.com.integration.IntegrationOutboxRepository
import br.com.integration.OutboxDrainService
import br.com.integration.OutboxStatus
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.nio.file.Path
import java.util.UUID

/**
 * Integration test para o fluxo completo de decisão:
 * DetectionEvent → DecisionService → DecisionExecution persistido → DecisionMadeEvent → Alert criado
 *
 * Validates: Requirements 4.7, 9.1, 10.2, 12.2
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["pld.integration.transaction-signals.enabled=true"],
)
@Testcontainers
class DecisionFlowIntegrationTest {

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var customerRiskPort: CustomerRiskPort

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var outboxRepository: IntegrationOutboxRepository

    @Autowired
    private lateinit var outboxDrainService: OutboxDrainService

    @Autowired
    private lateinit var integrationEventPublisher: ControllableIntegrationEventPublisher

    @Autowired
    private lateinit var evaluateKeywordScreeningUseCase: EvaluateKeywordScreeningUseCase

    @Autowired
    private lateinit var failOnceDecisionListener: FailOnceDecisionListener

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

        private val noRiskPartyIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
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
                        in noRiskPartyIds -> null
                        else -> CustomerRisk.MR
                    }
                }
            }
        }

        @Bean
        @Primary
        fun integrationEventPublisher(): ControllableIntegrationEventPublisher =
            ControllableIntegrationEventPublisher()

        @Bean
        fun failOnceDecisionListener(): FailOnceDecisionListener = FailOnceDecisionListener()
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
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_outbox o JOIN transaction_evaluation e ON e.evaluation_id = o.aggregate_id WHERE e.external_transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
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
    fun `typed transaction and party produce schema-valid evaluation signal and review outbox`() {
        val ruleId = getRuleDefinitionId("KEYWORD_SCREENING")
        ensureActiveRuleConfiguration(ruleId)
        val transactionId = "txn_01J6ZK7Q3W8K0M2N4P6R8T0V2H"
        val partyId = "pty_01J6ZK7Q3W8K0M2N4P6R8T0V2D"
        val correlationId = "01J6ZK7Q3W8K0M2N4P6R8T0V2B"

        applicationEventPublisher.publishEvent(
            DetectionEvent(
                eventId = EventId("01J6ZK7Q3W8K0M2N4P6R8T0V2C"),
                traceId = TraceId(correlationId),
                timestamp = Instant.now(),
                transactionId = TransactionId(transactionId),
                customerId = CustomerId(partyId),
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                detectionResult = DetectionResult(
                    matched = true,
                    matches = listOf(DetectionMatch(term = "lavagem", category = "AML")),
                ),
            ),
        )

        val evaluationId = jdbcTemplate.queryForObject(
            "SELECT evaluation_id FROM decision_execution WHERE transaction_id = ?",
            String::class.java,
            transactionId,
        )
        assertTrue(evaluationId!!.matches(Regex("^evl_[0-9A-HJKMNP-TV-Z]{26}$")))
        assertEquals(partyId, jdbcTemplate.queryForObject(
            "SELECT party_id FROM decision_execution WHERE transaction_id = ?",
            String::class.java,
            transactionId,
        ))
        assertEquals(correlationId, jdbcTemplate.queryForObject(
            "SELECT correlation_id FROM decision_execution WHERE transaction_id = ?",
            String::class.java,
            transactionId,
        ))

        assertEnvelopeValid(evaluationId, "TransactionEvaluationCompleted", "TransactionEvaluationCompletedV2")
        assertEnvelopeValid(evaluationId, "TransactionSignalDetected", "TransactionSignalDetected")
        assertEnvelopeValid(evaluationId, "ManualReviewRequested", "ManualReviewRequestedV2")
        assertEquals(3L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_outbox WHERE aggregate_id = ?",
            Long::class.java,
            evaluationId,
        ))
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM alert WHERE transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_evaluation WHERE evaluation_id = ? AND snapshot_hash ~ '^[a-f0-9]{64}$'",
            Long::class.java,
            evaluationId,
        ))
    }

    @Test
    fun `outbox retry preserves event identity until publication succeeds`() {
        jdbcTemplate.update("DELETE FROM integration_outbox")
        integrationEventPublisher.calls.clear()
        val eventId = br.com.shared.domain.valueobject.PrefixedUlid.ulid()
        val envelope = """{"eventId":"$eventId","eventType":"TransactionSignalDetected"}"""
        outboxRepository.saveAndFlush(
            IntegrationOutboxEntity(
                eventId = eventId,
                eventType = "TransactionSignalDetected",
                eventVersion = 1,
                aggregateType = "TransactionEvaluation",
                aggregateId = br.com.shared.domain.valueobject.PrefixedUlid.next("evl_"),
                logicalId = eventId,
                envelope = envelope,
                occurredAt = Instant.now(),
                nextAttemptAt = Instant.EPOCH,
            ),
        )
        integrationEventPublisher.failNext = true

        assertEquals(0, outboxDrainService.publishPending(10))
        val failed = outboxRepository.findById(eventId).orElseThrow()
        assertEquals(OutboxStatus.PENDING, failed.status)
        assertEquals(1, failed.attemptCount)
        assertEquals(eventId, objectMapper.readTree(failed.envelope).required("eventId").asText())

        failed.nextAttemptAt = Instant.EPOCH
        outboxRepository.saveAndFlush(failed)
        assertEquals(1, outboxDrainService.publishPending(10))

        val published = outboxRepository.findById(eventId).orElseThrow()
        assertEquals(OutboxStatus.PUBLISHED, published.status)
        assertEquals(eventId, objectMapper.readTree(published.envelope).required("eventId").asText())
        assertEquals(2, integrationEventPublisher.calls.count { it == eventId })
    }

    @Test
    fun `failure after screening rolls back and retry creates decision and outbox`() {
        val ruleId = getRuleDefinitionId("KEYWORD_SCREENING")
        ensureActiveRuleConfiguration(ruleId)
        val transactionId = br.com.shared.domain.valueobject.PrefixedUlid.next("txn_")
        val partyId = br.com.shared.domain.valueobject.PrefixedUlid.next("pty_")
        failOnceDecisionListener.failNextForParty = partyId
        val command = EvaluateKeywordScreeningCommand(
            transactionId = TransactionId(transactionId),
            customerId = CustomerId(partyId),
            description = "pagamento relacionado a lavagem de dinheiro",
            correlationId = "rollback-retry-correlation",
        )

        assertThrows(RuntimeException::class.java) {
            evaluateKeywordScreeningUseCase.execute(command)
        }
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rule_execution WHERE transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decision_execution WHERE transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_outbox WHERE envelope -> 'payload' ->> 'transactionId' = ?",
            Long::class.java,
            transactionId,
        ))
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_evaluation WHERE external_transaction_id = ?",
            Long::class.java,
            transactionId,
        ))

        evaluateKeywordScreeningUseCase.execute(command)

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rule_execution WHERE transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decision_execution WHERE transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
        assertEquals(3L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_outbox WHERE envelope -> 'payload' ->> 'transactionId' = ?",
            Long::class.java,
            transactionId,
        ))
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_evaluation WHERE external_transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
    }

    @Test
    fun `no signal produces only evaluation completion`() {
        val ruleId = getRuleDefinitionId("KEYWORD_SCREENING")
        ensureActiveRuleConfiguration(ruleId)
        val transactionId = br.com.shared.domain.valueobject.PrefixedUlid.next("txn_")
        val partyId = br.com.shared.domain.valueobject.PrefixedUlid.next("pty_")

        evaluateKeywordScreeningUseCase.execute(
            EvaluateKeywordScreeningCommand(
                transactionId = TransactionId(transactionId),
                customerId = CustomerId(partyId),
                description = "pagamento comum de mercado",
            ),
        )

        val evaluationId = jdbcTemplate.queryForObject(
            "SELECT evaluation_id FROM transaction_evaluation WHERE transaction_id = ?",
            String::class.java,
            transactionId,
        )!!
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_outbox WHERE aggregate_id = ? AND event_type = 'TransactionEvaluationCompleted'",
            Long::class.java,
            evaluationId,
        ))
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_outbox WHERE aggregate_id = ? AND event_type <> 'TransactionEvaluationCompleted'",
            Long::class.java,
            evaluationId,
        ))
        assertEnvelopeValid(evaluationId, "TransactionEvaluationCompleted", "TransactionEvaluationCompletedV2")
    }

    @Test
    fun `missing risk is indeterminate and requests review without signal`() {
        val ruleId = getRuleDefinitionId("KEYWORD_SCREENING")
        ensureActiveRuleConfiguration(ruleId)
        val externalTransactionId = "TX-INDETERMINATE-${UUID.randomUUID()}"
        val partyId = br.com.shared.domain.valueobject.PrefixedUlid.next("pty_")
        noRiskPartyIds += partyId
        applicationEventPublisher.publishEvent(
            DetectionEvent(
                eventId = EventId(br.com.shared.domain.valueobject.PrefixedUlid.ulid()),
                traceId = TraceId(br.com.shared.domain.valueobject.PrefixedUlid.ulid()),
                timestamp = Instant.now(),
                transactionId = TransactionId(externalTransactionId),
                customerId = CustomerId(partyId),
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                detectionResult = DetectionResult(
                    matched = true,
                    matches = listOf(DetectionMatch("lavagem", "AML")),
                ),
            ),
        )

        val evaluationId = jdbcTemplate.queryForObject(
            "SELECT evaluation_id FROM transaction_evaluation WHERE external_transaction_id = ?",
            String::class.java,
            externalTransactionId,
        )!!
        assertEquals("INDETERMINATE", jdbcTemplate.queryForObject(
            "SELECT execution_status FROM transaction_evaluation WHERE evaluation_id = ?",
            String::class.java,
            evaluationId,
        ))
        assertEquals("UNKNOWN", jdbcTemplate.queryForObject(
            "SELECT fact ->> 'quality' FROM transaction_evaluation, jsonb_array_elements(facts) fact WHERE evaluation_id = ? AND fact ->> 'code' = 'customerRisk'",
            String::class.java,
            evaluationId,
        ))
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_outbox WHERE aggregate_id = ? AND event_type = 'TransactionSignalDetected'",
            Long::class.java,
            evaluationId,
        ))
        assertEquals("[]", jdbcTemplate.queryForObject(
            "SELECT (envelope -> 'payload' -> 'signalIds')::text FROM integration_outbox WHERE aggregate_id = ? AND event_type = 'ManualReviewRequested'",
            String::class.java,
            evaluationId,
        ))
        assertEnvelopeValid(evaluationId, "TransactionEvaluationCompleted", "TransactionEvaluationCompletedV2")
        assertEnvelopeValid(evaluationId, "ManualReviewRequested", "ManualReviewRequestedV2")
    }

    @Test
    fun `transaction versions and replay requests create distinct idempotent evaluations`() {
        val ruleId = getRuleDefinitionId("KEYWORD_SCREENING")
        ensureActiveRuleConfiguration(ruleId)
        val transactionId = br.com.shared.domain.valueobject.PrefixedUlid.next("txn_")
        val partyId = br.com.shared.domain.valueobject.PrefixedUlid.next("pty_")

        fun event(version: Int, purpose: String = "LIVE", requestId: String? = null) = DetectionEvent(
            eventId = EventId(br.com.shared.domain.valueobject.PrefixedUlid.ulid()),
            traceId = TraceId(br.com.shared.domain.valueobject.PrefixedUlid.ulid()),
            timestamp = Instant.now(),
            transactionId = TransactionId(transactionId),
            customerId = CustomerId(partyId),
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            detectionResult = DetectionResult(true, listOf(DetectionMatch("lavagem", "AML"))),
            transactionVersion = version,
            purpose = purpose,
            evaluationRequestId = requestId,
        )

        applicationEventPublisher.publishEvent(event(1))
        applicationEventPublisher.publishEvent(event(2))
        applicationEventPublisher.publishEvent(event(2, "REPLAY", "replay-request-1"))
        applicationEventPublisher.publishEvent(event(2, "REPLAY", "replay-request-1"))

        assertEquals(3L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_evaluation WHERE transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_outbox o JOIN transaction_evaluation e ON e.evaluation_id = o.aggregate_id WHERE e.transaction_id = ? AND o.event_type = 'TransactionEvaluationCompleted'",
            Long::class.java,
            transactionId,
        ))
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM alert WHERE transaction_id = ?",
            Long::class.java,
            transactionId,
        ))
    }

    @Test
    fun `legacy transaction id rejects divergent payload`() {
        val ruleId = getRuleDefinitionId("KEYWORD_SCREENING")
        ensureActiveRuleConfiguration(ruleId)
        val transactionId = TransactionId("TX-CONFLICT-${UUID.randomUUID()}")
        val customerId = CustomerId("CUST-HIGH-RISK")
        evaluateKeywordScreeningUseCase.execute(
            EvaluateKeywordScreeningCommand(transactionId, customerId, "pagamento comum"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            evaluateKeywordScreeningUseCase.execute(
                EvaluateKeywordScreeningCommand(transactionId, customerId, "pagamento relacionado a lavagem"),
            )
        }
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM screening_intake WHERE transaction_id = ?",
            Long::class.java,
            transactionId.value,
        ))
    }

    private fun assertEnvelopeValid(evaluationId: String, eventType: String, schemaName: String) {
        val envelope = objectMapper.readTree(
            jdbcTemplate.queryForObject(
                "SELECT envelope::text FROM integration_outbox WHERE aggregate_id = ? AND event_type = ?",
                String::class.java,
                evaluationId,
                eventType,
            ),
        ).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        envelope.put("publishedAt", Instant.now().toString())
        val schemaPath = Path.of(System.getProperty("user.dir"))
            .resolveSibling("pld-platform-docs/schemas/v1/$schemaName.schema.json")
        val errors = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(schemaPath.toUri())
            .validate(envelope)
        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
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
        val actions = """["GENERATE_ALERT","REVIEW"]"""

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

class ControllableIntegrationEventPublisher : IntegrationEventPublisher {
    var failNext: Boolean = false
    val calls = mutableListOf<String>()

    override fun publish(eventId: String, eventType: String, envelope: String, publishedAt: Instant) {
        calls += eventId
        if (failNext) {
            failNext = false
            throw RuntimeException("SQS unavailable")
        }
    }
}

class FailOnceDecisionListener {
    var failNextForParty: String? = null

    @EventListener
    fun fail(event: br.com.decision.domain.event.DecisionMadeEvent) {
        if (event.customerId.value == failNextForParty) {
            failNextForParty = null
            throw RuntimeException("simulated failure after screening persistence")
        }
    }
}
