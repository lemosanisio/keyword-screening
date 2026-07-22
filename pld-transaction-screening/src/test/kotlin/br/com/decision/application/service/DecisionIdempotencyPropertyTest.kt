package br.com.decision.application.service

import br.com.decision.application.usecase.ExecuteDecisionCommand
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ConfigurationVersionEntry
import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.DecisionResult
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.port.DecisionExecutionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.service.DecisionEngine
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.DomainEventPublisher
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import br.com.evaluation.infrastructure.CanonicalSnapshot
import br.com.evaluation.infrastructure.SnapshotCanonicalizer
import br.com.evaluation.infrastructure.TransactionEvaluationRepository
import br.com.evaluation.infrastructure.TransactionIdentityResolver
import br.com.evaluation.infrastructure.TransactionEvaluationLock
import br.com.evaluation.infrastructure.IntakeValidator
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Property-Based Tests for Decision Execution Idempotency.
 *
 * **Property 4: Decision Execution Idempotency**
 * **Validates: Requirements 4.7, 9.2**
 *
 * Properties verified:
 * 1. First call creates DecisionExecution and publishes event
 * 2. Second call with same (transactionId, ruleCode) returns identical result
 * 3. Second call does NOT invoke DecisionEngine
 * 4. Second call does NOT persist new DecisionExecution
 * 5. Second call does NOT publish new event
 * 6. Result from second call equals result from first call
 */
class DecisionIdempotencyPropertyTest {

    private val decisionEngine = mockk<DecisionEngine>()
    private val decisionExecutionRepository = mockk<DecisionExecutionRepository>()
    private val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()
    private val ruleConfigurationRepository = mockk<RuleConfigurationRepository>()
    private val domainEventPublisher = mockk<DomainEventPublisher>()
    private val transactionIdentityResolver = mockk<TransactionIdentityResolver>()
    private val snapshotCanonicalizer = mockk<SnapshotCanonicalizer>()
    private val transactionEvaluationRepository = mockk<TransactionEvaluationRepository>(relaxed = true)
    private val transactionEvaluationLock = mockk<TransactionEvaluationLock>(relaxed = true)
    private val intakeValidator = mockk<IntakeValidator>()

    init {
        every { transactionIdentityResolver.resolve(any(), any()) } answers { secondArg() }
        every { snapshotCanonicalizer.canonicalize(any()) } returns CanonicalSnapshot("{}", "0".repeat(64))
        every { transactionEvaluationRepository.findDecisionExecutionId(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        every { intakeValidator.validate(any()) } returns IntakeValidator.IntakeResult.Valid
    }

    private val decisionService = DecisionService(
        decisionEngine = decisionEngine,
        decisionExecutionRepository = decisionExecutionRepository,
        ruleDefinitionRepository = ruleDefinitionRepository,
        ruleConfigurationRepository = ruleConfigurationRepository,
        domainEventPublisher = domainEventPublisher,
        transactionIdentityResolver = transactionIdentityResolver,
        snapshotCanonicalizer = snapshotCanonicalizer,
        transactionEvaluationRepository = transactionEvaluationRepository,
        transactionEvaluationLock = transactionEvaluationLock,
        intakeValidator = intakeValidator,
    )

    // --- Random generators ---

    private val ruleCodes = listOf("KEYWORD_SCREENING", "SANCTIONS_CHECK", "AML_DETECTION", "FRAUD_RULE", "VELOCITY_CHECK")

    private fun randomTransactionId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val length = Random.nextInt(1, 65)
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("").ifEmpty { "TX" }
    }

    private fun randomCustomerId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val length = Random.nextInt(1, 65)
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("").ifEmpty { "CUST" }
    }

    private fun randomRuleCode(): String = ruleCodes[Random.nextInt(ruleCodes.size)]

    // --- Helpers ---

    private fun buildRuleDefinition(ruleCode: String): Pair<RuleId, RuleDefinition> {
        val ruleId = RuleId(UUID.randomUUID())
        val definition = RuleDefinition(
            id = ruleId,
            code = RuleCode(ruleCode),
            name = "$ruleCode Rule",
            description = "Rule for $ruleCode",
            context = RuleContext.SCREENING,
            category = RuleCategory.KEYWORD_SCREENING,
            supportedFacts = listOf(FactName("keywordMatched"), FactName("customerRisk")),
            supportedActions = listOf(Action.GENERATE_ALERT, Action.IGNORE),
            status = RuleStatus.ACTIVE,
            createdAt = Instant.now()
        )
        return ruleId to definition
    }

    private fun buildActiveConfig(ruleId: RuleId): RuleConfiguration {
        return RuleConfiguration(
            id = UUID.randomUUID(),
            ruleId = ruleId,
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            active = true,
            draft = false,
            currentVersion = ConfigurationVersion(1),
            versions = listOf(
                ConfigurationVersionEntry(
                    version = ConfigurationVersion(1),
                    expressions = listOf(
                        Condition(
                            factName = FactName("keywordMatched"),
                            operator = ComparisonOperator.EQUALS,
                            expectedValue = FactValue.BooleanValue(true)
                        )
                    ),
                    actions = listOf(Action.GENERATE_ALERT),
                    active = true,
                    createdBy = "analyst@company.com",
                    createdAt = Instant.now()
                )
            ),
            createdBy = "analyst@company.com",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun buildDecisionResult(matched: Boolean): DecisionResult {
        val decision = if (matched) Decision.ALERT else Decision.IGNORE
        val actions = if (matched) listOf(Action.GENERATE_ALERT) else emptyList()
        return DecisionResult(
            decision = decision,
            actions = actions,
            matchedExpressions = if (matched) listOf(
                ExpressionEvaluation(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true),
                    actualValue = FactValue.BooleanValue(true),
                    satisfied = true,
                    justification = "keywordMatched EQUALS true → satisfied"
                )
            ) else emptyList(),
            failedExpressions = if (!matched) listOf(
                ExpressionEvaluation(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true),
                    actualValue = FactValue.BooleanValue(false),
                    satisfied = false,
                    justification = "keywordMatched EQUALS true → NOT satisfied"
                )
            ) else emptyList(),
            executionTimeMs = 10L,
            configurationVersion = ConfigurationVersion(1),
            facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(matched)),
            explanation = DecisionExplanation(traceId = TraceId("trace-test"), steps = emptyList())
        )
    }

    private fun buildCommand(transactionId: String, customerId: String, ruleCode: String, matched: Boolean) =
        ExecuteDecisionCommand(
            transactionId = TransactionId(transactionId),
            customerId = CustomerId(customerId),
            ruleCode = RuleCode(ruleCode),
            detectionResult = DetectionResult(
                matched = matched,
                matches = if (matched) listOf(DetectionMatch("term", "AML")) else emptyList()
            )
        )

    // --- Property Tests ---

    @RepeatedTest(200)
    @DisplayName("Property: first call creates execution and publishes event; second call returns same result without side effects")
    fun `first call creates execution and publishes event - second call returns same result without side effects`() {
        val transactionId = randomTransactionId()
        val customerId = randomCustomerId()
        val ruleCode = randomRuleCode()
        val matched = Random.nextBoolean()

        // Reset mocks for each iteration
        clearMocks(
            decisionEngine, decisionExecutionRepository,
            ruleDefinitionRepository, ruleConfigurationRepository,
            domainEventPublisher
        )

        val (ruleId, ruleDefinition) = buildRuleDefinition(ruleCode)
        val activeConfig = buildActiveConfig(ruleId)
        val engineResult = buildDecisionResult(matched)

        // Track saved executions to simulate idempotency check
        var savedExecution: DecisionExecution? = null

        // Setup mocks for first call (no existing execution)
        every { ruleDefinitionRepository.findByCode(RuleCode(ruleCode)) } returns ruleDefinition
        every {
            decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId(transactionId), ruleId)
        } answers {
            // Returns null on first call, then the saved execution on subsequent calls
            savedExecution
        }
        every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig
        every { decisionEngine.evaluate(any(), any(), any()) } returns engineResult
        every { decisionExecutionRepository.save(any()) } answers {
            val exec = firstArg<DecisionExecution>()
            savedExecution = exec
            exec
        }
        every { transactionEvaluationRepository.findDecisionExecutionId(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            savedExecution?.id
        }
        every { decisionExecutionRepository.findById(any()) } answers { savedExecution }
        every { domainEventPublisher.publish(any()) } just Runs

        val command = buildCommand(transactionId, customerId, ruleCode, matched)

        // --- First call ---
        val firstResult = decisionService.execute(command)

        // Verify first call DID invoke engine, persist, and publish
        verify(exactly = 1) { decisionEngine.evaluate(any(), any(), any()) }
        verify(exactly = 1) { decisionExecutionRepository.save(any()) }
        verify(exactly = 1) { domainEventPublisher.publish(any()) }

        // --- Second call (same transactionId + ruleCode) ---
        val secondResult = decisionService.execute(command)

        // Property: second call returns identical result
        assertEquals(firstResult, secondResult)

        // Property: second call does NOT invoke DecisionEngine again
        verify(exactly = 1) { decisionEngine.evaluate(any(), any(), any()) }

        // Property: second call does NOT persist new DecisionExecution
        verify(exactly = 1) { decisionExecutionRepository.save(any()) }

        // Property: second call does NOT publish new event
        verify(exactly = 1) { domainEventPublisher.publish(any()) }
    }
}
