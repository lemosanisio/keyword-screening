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
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleIdentificationStep
import br.com.decision.domain.model.ConfigurationVersionEntry
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import br.com.shared.domain.valueobject.TransactionId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.TraceId

@DisplayName("DecisionEngine — Unit Tests")
class DecisionEngineTest {

    private val contextBuilder = mockk<ContextBuilder>()
    private val expressionEvaluator = ExpressionEvaluator()
    private val ruleEngine = RuleEngine(expressionEvaluator)
    private val decisionEngine = DecisionEngine(contextBuilder, ruleEngine)

    private val traceId = TraceId("trace-${UUID.randomUUID()}")

    private fun buildDetectionEvent(
        transactionId: TransactionId = TransactionId("TX-001"),
        customerId: CustomerId = CustomerId("CUST-42"),
        ruleCode: RuleCode = RuleCode("KEYWORD_SCREENING"),
        matched: Boolean = true,
        matches: List<DetectionMatch> = listOf(DetectionMatch("lavagem", "AML"))
    ) = DetectionEvent(
        eventId = EventId(UUID.randomUUID().toString()),
        traceId = traceId,
        timestamp = Instant.now(),
        transactionId = transactionId,
        customerId = customerId,
        ruleCode = ruleCode,
        detectionResult = DetectionResult(matched = matched, matches = matches)
    )

    private fun buildConfiguration(
        active: Boolean = true,
        expressions: List<Expression> = listOf(
            Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(true)
            ),
            Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("MR")
            )
        ),
        actions: List<Action> = listOf(Action.GENERATE_ALERT)
    ) = RuleConfiguration(
        id = UUID.randomUUID(),
        ruleId = RuleId(UUID.randomUUID()),
        expressions = expressions,
        actions = actions,
        active = active,
        draft = false,
        currentVersion = ConfigurationVersion(1),
        versions = listOf(
            ConfigurationVersionEntry(
                version = ConfigurationVersion(1),
                expressions = expressions,
                actions = actions,
                active = active,
                createdBy = "analyst@company.com",
                createdAt = Instant.now()
            )
        ),
        createdBy = "analyst@company.com",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Nested
    @DisplayName("Inactive configuration")
    inner class InactiveConfiguration {

        @Test
        fun `returns IGNORE without evaluating when configuration is inactive`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration(active = false)

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()
            assertThat(result.matchedExpressions).isEmpty()
            assertThat(result.failedExpressions).isEmpty()
            assertThat(result.facts).isEmpty()

            // ContextBuilder should NOT be invoked for inactive config
            verify(exactly = 0) { contextBuilder.buildContext(any(), any()) }
        }

        @Test
        fun `returns explanation with 7 steps even for inactive config`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration(active = false)

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.explanation).isNotNull
            assertThat(result.explanation!!.steps).hasSize(7)
            assertThat(result.explanation!!.traceId).isEqualTo(traceId)

            // Verify step types in order
            assertThat(result.explanation!!.steps[0]).isInstanceOf(ReceptionStep::class.java)
            assertThat(result.explanation!!.steps[1]).isInstanceOf(RuleIdentificationStep::class.java)
            assertThat(result.explanation!!.steps[2]).isInstanceOf(ContextBuildingStep::class.java)
            assertThat(result.explanation!!.steps[3]).isInstanceOf(EvaluationStep::class.java)
            assertThat(result.explanation!!.steps[4]).isInstanceOf(DecisionStep::class.java)
            assertThat(result.explanation!!.steps[5]).isInstanceOf(PersistenceStep::class.java)
            assertThat(result.explanation!!.steps[6]).isInstanceOf(PublicationStep::class.java)
        }

        @Test
        fun `inactive config decision step justification mentions inactive`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration(active = false)

            val result = decisionEngine.evaluate(event, config, traceId)

            val decisionStep = result.explanation!!.steps[4] as DecisionStep
            assertThat(decisionStep.justification).contains("inativa")
            assertThat(decisionStep.decision).isEqualTo(Decision.IGNORE)
        }
    }

    @Nested
    @DisplayName("ALERT decision — all conditions satisfied")
    inner class AlertDecision {

        @Test
        fun `returns ALERT when all conditions are satisfied`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.decision).isEqualTo(Decision.ALERT)
            assertThat(result.actions).containsExactly(Action.GENERATE_ALERT)
            assertThat(result.matchedExpressions).hasSize(2)
            assertThat(result.failedExpressions).isEmpty()
            assertThat(result.facts).isEqualTo(facts)
            assertThat(result.configurationVersion).isEqualTo(ConfigurationVersion(1))
        }

        @Test
        fun `ALERT decision includes full 7-step explanation`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.explanation).isNotNull
            assertThat(result.explanation!!.steps).hasSize(7)
            assertThat(result.explanation!!.traceId).isEqualTo(traceId)

            val decisionStep = result.explanation!!.steps[4] as DecisionStep
            assertThat(decisionStep.decision).isEqualTo(Decision.ALERT)
            assertThat(decisionStep.actions).containsExactly(Action.GENERATE_ALERT)
        }
    }

    @Nested
    @DisplayName("IGNORE decision — not all conditions satisfied")
    inner class IgnoreDecision {

        @Test
        fun `returns IGNORE when keyword match is false`() {
            val event = buildDetectionEvent(matched = false)
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(false),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()
            assertThat(result.matchedExpressions).hasSize(1) // customerRisk matched
            assertThat(result.failedExpressions).hasSize(1) // keywordMatched failed
        }

        @Test
        fun `returns IGNORE when customer risk is too low`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("BR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()
            assertThat(result.matchedExpressions).hasSize(1) // keywordMatched matched
            assertThat(result.failedExpressions).hasSize(1) // customerRisk failed
        }

        @Test
        fun `returns IGNORE when a required fact is absent`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            // Only keywordMatched available, customerRisk absent
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true)
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()
            assertThat(result.evaluationStatus).isEqualTo(br.com.decision.domain.model.EvaluationStatus.INDETERMINATE)
            assertThat(result.reviewRequired).isTrue()
            // keywordMatched matched, customerRisk failed due to absence
            assertThat(result.matchedExpressions).hasSize(1)
            assertThat(result.failedExpressions).hasSize(1)
            assertThat(result.failedExpressions[0].actualValue).isNull()
        }
    }

    @Nested
    @DisplayName("Required facts extraction")
    inner class RequiredFacts {

        @Test
        fun `invokes ContextBuilder with distinct required fact names`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            decisionEngine.evaluate(event, config, traceId)

            verify {
                contextBuilder.buildContext(
                    event,
                    match { requiredFacts ->
                        requiredFacts.containsAll(
                            listOf(FactName("keywordMatched"), FactName("customerRisk"))
                        ) && requiredFacts.size == 2
                    }
                )
            }
        }

        @Test
        fun `deduplicates required fact names when same factName appears in multiple conditions`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration(
                expressions = listOf(
                    Condition(
                        factName = FactName("customerRisk"),
                        operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                        expectedValue = FactValue.EnumValue("MR")
                    ),
                    Condition(
                        factName = FactName("customerRisk"),
                        operator = ComparisonOperator.NOT_EQUALS,
                        expectedValue = FactValue.EnumValue("BR")
                    )
                )
            )
            val facts = mapOf(
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            decisionEngine.evaluate(event, config, traceId)

            verify {
                contextBuilder.buildContext(
                    event,
                    match { requiredFacts ->
                        requiredFacts.size == 1 && requiredFacts[0] == FactName("customerRisk")
                    }
                )
            }
        }
    }

    @Nested
    @DisplayName("Explanation completeness")
    inner class ExplanationCompleteness {

        @Test
        fun `explanation steps are in correct order (1 through 7)`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)
            val steps = result.explanation!!.steps

            assertThat(steps[0].stepNumber).isEqualTo(1)
            assertThat(steps[1].stepNumber).isEqualTo(2)
            assertThat(steps[2].stepNumber).isEqualTo(3)
            assertThat(steps[3].stepNumber).isEqualTo(4)
            assertThat(steps[4].stepNumber).isEqualTo(5)
            assertThat(steps[5].stepNumber).isEqualTo(6)
            assertThat(steps[6].stepNumber).isEqualTo(7)
        }

        @Test
        fun `reception step contains event data`() {
            val event = buildDetectionEvent( transactionId = TransactionId("TX-123"), customerId = CustomerId("CUST-99"), ruleCode = RuleCode("KEYWORD_SCREENING")
            )
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)
            val receptionStep = result.explanation!!.steps[0] as ReceptionStep

            assertThat(receptionStep.transactionId).isEqualTo(TransactionId("TX-123"))
            assertThat(receptionStep.customerId).isEqualTo(CustomerId("CUST-99"))
            assertThat(receptionStep.ruleCode.value).isEqualTo("KEYWORD_SCREENING")
            assertThat(receptionStep.stepName).isEqualTo("RECEPTION")
        }

        @Test
        fun `rule identification step contains configuration data`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration(
                actions = listOf(Action.GENERATE_ALERT)
            )
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)
            val ruleStep = result.explanation!!.steps[1] as RuleIdentificationStep

            assertThat(ruleStep.configurationVersion).isEqualTo(ConfigurationVersion(1))
            assertThat(ruleStep.expressions).hasSize(2)
            assertThat(ruleStep.actions).containsExactly(Action.GENERATE_ALERT)
            assertThat(ruleStep.stepName).isEqualTo("RULE_IDENTIFICATION")
        }

        @Test
        fun `evaluation step contains all expression evaluations`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)
            val evalStep = result.explanation!!.steps[3] as EvaluationStep

            assertThat(evalStep.evaluations).hasSize(2)
            assertThat(evalStep.stepName).isEqualTo("EVALUATION")
        }

        @Test
        fun `all steps have non-null timestamps`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)

            result.explanation!!.steps.forEach { step ->
                assertThat(step.timestamp).isNotNull()
            }
        }
    }

    @Nested
    @DisplayName("Execution time measurement")
    inner class ExecutionTime {

        @Test
        fun `executionTimeMs is non-negative`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration()
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.executionTimeMs).isGreaterThanOrEqualTo(0)
        }

        @Test
        fun `executionTimeMs is non-negative for inactive config`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration(active = false)

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.executionTimeMs).isGreaterThanOrEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Single condition configuration")
    inner class SingleCondition {

        @Test
        fun `ALERT with single satisfied condition`() {
            val event = buildDetectionEvent()
            val config = buildConfiguration(
                expressions = listOf(
                    Condition(
                        factName = FactName("keywordMatched"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.BooleanValue(true)
                    )
                )
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))

            every { contextBuilder.buildContext(event, any()) } returns FactSet(
                facts = facts,
                resolverResults = emptyList()
            )

            val result = decisionEngine.evaluate(event, config, traceId)

            assertThat(result.decision).isEqualTo(Decision.ALERT)
            assertThat(result.actions).containsExactly(Action.GENERATE_ALERT)
        }
    }
}
