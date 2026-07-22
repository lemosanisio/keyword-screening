package br.com.decision.domain.service

import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RuleEngine — Unit Tests")
class RuleEngineTest {

    private val expressionEvaluator = ExpressionEvaluator()
    private val ruleEngine = RuleEngine(expressionEvaluator)

    @Nested
    @DisplayName("AND-implicit semantics")
    inner class AndImplicitSemantics {

        @Test
        fun `allSatisfied is true when all conditions are satisfied`() {
            val expressions: List<Expression> = listOf(
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
            )
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isTrue()
            assertThat(result.evaluations).hasSize(2)
            assertThat(result.evaluations).allMatch { it.satisfied }
        }

        @Test
        fun `allSatisfied is false when one condition fails`() {
            val expressions: List<Expression> = listOf(
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
            )
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("BR")
            )

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isFalse()
            assertThat(result.evaluations).hasSize(2)
            assertThat(result.evaluations[0].satisfied).isTrue()
            assertThat(result.evaluations[1].satisfied).isFalse()
        }

        @Test
        fun `allSatisfied is false when all conditions fail`() {
            val expressions: List<Expression> = listOf(
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
            )
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(false),
                FactName("customerRisk") to FactValue.EnumValue("BR")
            )

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isFalse()
            assertThat(result.evaluations).hasSize(2)
            assertThat(result.evaluations).noneMatch { it.satisfied }
        }
    }

    @Nested
    @DisplayName("Single expression")
    inner class SingleExpression {

        @Test
        fun `allSatisfied is true with single satisfied condition`() {
            val expressions: List<Expression> = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isTrue()
            assertThat(result.evaluations).hasSize(1)
        }

        @Test
        fun `allSatisfied is false with single unsatisfied condition`() {
            val expressions: List<Expression> = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(false))

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isFalse()
        }
    }

    @Nested
    @DisplayName("Empty expressions")
    inner class EmptyExpressions {

        @Test
        fun `allSatisfied is false when expressions list is empty`() {
            val result = ruleEngine.evaluate(
                facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
                expressions = emptyList()
            )

            assertThat(result.allSatisfied).isFalse()
            assertThat(result.evaluations).isEmpty()
        }
    }

    @Nested
    @DisplayName("Absent facts")
    inner class AbsentFacts {

        @Test
        fun `allSatisfied is false when a required fact is absent`() {
            val expressions: List<Expression> = listOf(
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
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isFalse()
            assertThat(result.outcome).isEqualTo(RuleEvaluationOutcome.INDETERMINATE)
            assertThat(result.evaluations[0].satisfied).isTrue()
            assertThat(result.evaluations[1].satisfied).isFalse()
            assertThat(result.evaluations[1].actualValue).isNull()
        }

        @Test
        fun `allSatisfied is false when all facts are absent`() {
            val expressions: List<Expression> = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            )
            val facts = emptyMap<FactName, FactValue>()

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isFalse()
            assertThat(result.outcome).isEqualTo(RuleEvaluationOutcome.INDETERMINATE)
        }
    }

    @Nested
    @DisplayName("Evaluation results ordering")
    inner class EvaluationOrdering {

        @Test
        fun `evaluations are in the same order as expressions`() {
            val expressions: List<Expression> = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                ),
                Condition(
                    factName = FactName("customerRisk"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.EnumValue("AR")
                ),
                Condition(
                    factName = FactName("pep"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(false)
                )
            )
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR"),
                FactName("pep") to FactValue.BooleanValue(false)
            )

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.evaluations[0].factName).isEqualTo(FactName("keywordMatched"))
            assertThat(result.evaluations[1].factName).isEqualTo(FactName("customerRisk"))
            assertThat(result.evaluations[2].factName).isEqualTo(FactName("pep"))
        }
    }

    @Nested
    @DisplayName("MVP scenario — Keyword Screening rule")
    inner class MvpScenario {

        @Test
        fun `ALERT scenario — keyword match AND high risk customer`() {
            val expressions: List<Expression> = listOf(
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
            )
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isTrue()
        }

        @Test
        fun `IGNORE scenario — keyword match BUT low risk customer`() {
            val expressions: List<Expression> = listOf(
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
            )
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("BR")
            )

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isFalse()
        }

        @Test
        fun `IGNORE scenario — no keyword match even with high risk`() {
            val expressions: List<Expression> = listOf(
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
            )
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(false),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )

            val result = ruleEngine.evaluate(facts, expressions)

            assertThat(result.allSatisfied).isFalse()
        }
    }
}
