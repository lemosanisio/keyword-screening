package br.com.decision.domain.service

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ContextBuildingStep
import br.com.decision.domain.model.DecisionStep
import br.com.decision.domain.model.EvaluationStep
import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.PersistenceStep
import br.com.decision.domain.model.PublicationStep
import br.com.decision.domain.model.ReceptionStep
import br.com.decision.domain.model.ResolverOutcome
import br.com.decision.domain.model.ResolverResult
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleIdentificationStep
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Property-Based Tests for DecisionExplanation completeness.
 *
 * **Property 8: Decision Explanation Completeness**
 * **Validates: Requirements 16.1, 16.2, 16.4, 16.5**
 *
 * Properties verified:
 * 1. DecisionExplanation always contains exactly 7 steps
 * 2. Steps are ordered with stepNumber 1,2,3,4,5,6,7
 * 3. Each step has a non-null timestamp
 * 4. Each step has a non-blank stepName
 * 5. Steps are of the correct types in order: ReceptionStep, RuleIdentificationStep,
 *    ContextBuildingStep, EvaluationStep, DecisionStep, PersistenceStep, PublicationStep
 * 6. traceId is always non-blank
 */
class DecisionExplanationPropertyTest {

    private val contextBuilder = mockk<ContextBuilder>()
    private val expressionEvaluator = ExpressionEvaluator()
    private val ruleEngine = RuleEngine(expressionEvaluator)
    private val decisionEngine = DecisionEngine(contextBuilder, ruleEngine)

    // --- Random Generators ---

    private fun randomTransactionId(): TransactionId {
        val suffix = (1..Random.nextInt(1, 21))
            .map { ('a'..'z').random() }
            .joinToString("")
        return TransactionId("TX-$suffix")
    }

    private fun randomCustomerId(): CustomerId {
        val suffix = (1..Random.nextInt(1, 21))
            .map { ('a'..'z').random() }
            .joinToString("")
        return CustomerId("CUST-$suffix")
    }

    private fun randomRuleCode(): RuleCode {
        val codes = listOf("KEYWORD_SCREENING", "SANCTIONS_CHECK", "AML_RULE", "FRAUD_DETECTION")
        return RuleCode(codes[Random.nextInt(codes.size)])
    }

    private fun randomCustomerRisk(): CustomerRisk =
        CustomerRisk.entries[Random.nextInt(CustomerRisk.entries.size)]

    private fun randomDetectionEvent(): DetectionEvent {
        val txId = randomTransactionId()
        val custId = randomCustomerId()
        val ruleCode = randomRuleCode()
        val matched = Random.nextBoolean()
        val matchCount = if (matched) Random.nextInt(1, 6) else 0
        val categories = listOf("AML", "TERRORISM", "FRAUD", "SANCTIONS")
        val matches = (0 until matchCount).map {
            DetectionMatch(
                term = "term-${Random.nextInt(1, 1001)}",
                category = categories[Random.nextInt(categories.size)]
            )
        }
        return DetectionEvent(
            eventId = EventId("evt-${UUID.randomUUID()}"),
            traceId = TraceId("trace-${UUID.randomUUID()}"),
            timestamp = Instant.now(),
            transactionId = txId,
            customerId = custId,
            ruleCode = ruleCode,
            detectionResult = DetectionResult(matched = matched, matches = matches)
        )
    }

    private fun randomCondition(): Condition {
        val useBoolean = Random.nextBoolean()
        return if (useBoolean) {
            Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(Random.nextBoolean())
            )
        } else {
            val risk = randomCustomerRisk()
            val operators = listOf(
                ComparisonOperator.EQUALS,
                ComparisonOperator.NOT_EQUALS,
                ComparisonOperator.GREATER_THAN_OR_EQUAL
            )
            Condition(
                factName = FactName("customerRisk"),
                operator = operators[Random.nextInt(operators.size)],
                expectedValue = FactValue.EnumValue(risk.name)
            )
        }
    }

    private fun randomExpressions(): List<Expression> {
        val count = Random.nextInt(1, 11)
        return (0 until count).map { randomCondition() }
    }

    private fun randomConfiguration(): RuleConfiguration {
        val expressions = randomExpressions()
        val active = Random.nextBoolean()
        return RuleConfiguration(
            id = UUID.randomUUID(),
            ruleId = RuleId(UUID.randomUUID()),
            expressions = expressions,
            actions = listOf(Action.GENERATE_ALERT),
            active = active,
            draft = false,
            currentVersion = ConfigurationVersion(Random.nextInt(1, 101)),
            versions = emptyList(),
            createdBy = "analyst@test.com",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun randomFacts(): Map<FactName, FactValue> {
        val includeKeyword = Random.nextBoolean()
        val includeRisk = Random.nextBoolean()
        return buildMap {
            if (includeKeyword) put(FactName("keywordMatched"), FactValue.BooleanValue(Random.nextBoolean()))
            if (includeRisk) put(FactName("customerRisk"), FactValue.EnumValue(randomCustomerRisk().name))
        }
    }

    private fun randomTraceId(): TraceId = TraceId("trace-${UUID.randomUUID()}")

    private fun setupContextBuilder(facts: Map<FactName, FactValue>) {
        clearMocks(contextBuilder, answers = false, recordedCalls = true)
        every { contextBuilder.buildContext(any(), any()) } returns FactSet(
            facts = facts,
            resolverResults = facts.map { (name, value) ->
                ResolverResult(
                    resolverName = "MockResolver",
                    entity = "Mock",
                    sourceSystem = "Mock",
                    startedAt = Instant.now(),
                    finishedAt = Instant.now(),
                    durationMs = 1L,
                    result = ResolverOutcome.Success(factName = name, value = value)
                )
            }
        )
    }

    // --- Property Tests ---

    @RepeatedTest(200)
    @DisplayName("explanation always contains exactly 7 steps")
    fun `explanation always contains exactly 7 steps`() {
        val event = randomDetectionEvent()
        val config = randomConfiguration()
        val facts = randomFacts()
        val traceId = randomTraceId()

        setupContextBuilder(facts)
        val result = decisionEngine.evaluate(event, config, traceId)

        assertNotNull(result.explanation)
        assertEquals(7, result.explanation!!.steps.size)
    }

    @RepeatedTest(200)
    @DisplayName("steps are ordered with stepNumber 1 through 7")
    fun `steps are ordered with stepNumber 1 through 7`() {
        val event = randomDetectionEvent()
        val config = randomConfiguration()
        val facts = randomFacts()
        val traceId = randomTraceId()

        setupContextBuilder(facts)
        val result = decisionEngine.evaluate(event, config, traceId)

        val steps = result.explanation!!.steps
        steps.forEachIndexed { index, step ->
            assertEquals(index + 1, step.stepNumber)
        }
    }

    @RepeatedTest(200)
    @DisplayName("each step has a non-null timestamp")
    fun `each step has a non-null timestamp`() {
        val event = randomDetectionEvent()
        val config = randomConfiguration()
        val facts = randomFacts()
        val traceId = randomTraceId()

        setupContextBuilder(facts)
        val result = decisionEngine.evaluate(event, config, traceId)

        result.explanation!!.steps.forEach { step ->
            assertNotNull(step.timestamp)
        }
    }

    @RepeatedTest(200)
    @DisplayName("each step has a non-blank stepName")
    fun `each step has a non-blank stepName`() {
        val event = randomDetectionEvent()
        val config = randomConfiguration()
        val facts = randomFacts()
        val traceId = randomTraceId()

        setupContextBuilder(facts)
        val result = decisionEngine.evaluate(event, config, traceId)

        result.explanation!!.steps.forEach { step ->
            assertTrue(step.stepName.isNotBlank())
        }
    }

    @RepeatedTest(200)
    @DisplayName("steps are of the correct types in order")
    fun `steps are of the correct types in order`() {
        val event = randomDetectionEvent()
        val config = randomConfiguration()
        val facts = randomFacts()
        val traceId = randomTraceId()

        setupContextBuilder(facts)
        val result = decisionEngine.evaluate(event, config, traceId)

        val steps = result.explanation!!.steps
        assertInstanceOf(ReceptionStep::class.java, steps[0])
        assertInstanceOf(RuleIdentificationStep::class.java, steps[1])
        assertInstanceOf(ContextBuildingStep::class.java, steps[2])
        assertInstanceOf(EvaluationStep::class.java, steps[3])
        assertInstanceOf(DecisionStep::class.java, steps[4])
        assertInstanceOf(PersistenceStep::class.java, steps[5])
        assertInstanceOf(PublicationStep::class.java, steps[6])
    }

    @RepeatedTest(200)
    @DisplayName("traceId is always non-blank in the explanation")
    fun `traceId is always non-blank in the explanation`() {
        val event = randomDetectionEvent()
        val config = randomConfiguration()
        val facts = randomFacts()
        val traceId = randomTraceId()

        setupContextBuilder(facts)
        val result = decisionEngine.evaluate(event, config, traceId)

        assertTrue(result.explanation!!.traceId.value.isNotBlank())
        assertEquals(traceId, result.explanation!!.traceId)
    }
}
