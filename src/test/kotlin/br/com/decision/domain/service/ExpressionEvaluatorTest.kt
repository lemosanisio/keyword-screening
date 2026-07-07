package br.com.decision.domain.service

import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.Group
import br.com.decision.domain.model.LogicalOperator
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ExpressionEvaluator — Unit Tests")
class ExpressionEvaluatorTest {

    private val evaluator = ExpressionEvaluator()

    @Nested
    @DisplayName("EQUALS on BooleanValue")
    inner class EqualsBoolean {

        @Test
        fun `satisfied when both values are true`() {
            val condition = Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(true)
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
            assertThat(result.actualValue).isEqualTo(FactValue.BooleanValue(true))
            assertThat(result.justification).contains("é igual a")
        }

        @Test
        fun `satisfied when both values are false`() {
            val condition = Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(false)
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(false))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `not satisfied when values differ`() {
            val condition = Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(true)
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(false))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
            assertThat(result.justification).contains("não é igual a")
        }
    }

    @Nested
    @DisplayName("NOT_EQUALS on BooleanValue")
    inner class NotEqualsBoolean {

        @Test
        fun `satisfied when values differ`() {
            val condition = Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.NOT_EQUALS,
                expectedValue = FactValue.BooleanValue(true)
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(false))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
            assertThat(result.justification).contains("é diferente de")
        }

        @Test
        fun `not satisfied when values are equal`() {
            val condition = Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.NOT_EQUALS,
                expectedValue = FactValue.BooleanValue(true)
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
            assertThat(result.justification).contains("é igual a")
        }
    }

    @Nested
    @DisplayName("EQUALS on EnumValue")
    inner class EqualsEnum {

        @Test
        fun `satisfied when enum values match`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.EnumValue("AR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("AR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `not satisfied when enum values differ`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.EnumValue("AR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("BR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }
    }

    @Nested
    @DisplayName("NOT_EQUALS on EnumValue")
    inner class NotEqualsEnum {

        @Test
        fun `satisfied when enum values differ`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.NOT_EQUALS,
                expectedValue = FactValue.EnumValue("AR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("BR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `not satisfied when enum values match`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.NOT_EQUALS,
                expectedValue = FactValue.EnumValue("MR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("MR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }
    }

    @Nested
    @DisplayName("GREATER_THAN_OR_EQUAL on CustomerRisk")
    inner class GteCustomerRisk {

        @Test
        fun `AR gte BR is satisfied`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("BR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("AR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
            assertThat(result.justification).contains("é >=")
        }

        @Test
        fun `AR gte MR is satisfied`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("MR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("AR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `AR gte AR is satisfied (equal)`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("AR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("AR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `BR gte MR is not satisfied`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("MR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("BR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
            assertThat(result.justification).contains("não é >=")
        }

        @Test
        fun `BR gte AR is not satisfied`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("AR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("BR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }

        @Test
        fun `MR gte MR is satisfied (equal)`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("MR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("MR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `MR gte AR is not satisfied`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("AR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("MR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }
    }

    @Nested
    @DisplayName("Absent fact")
    inner class AbsentFact {

        @Test
        fun `returns not satisfied when fact is absent`() {
            val condition = Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(true)
            )
            val facts = emptyMap<FactName, FactValue>()

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
            assertThat(result.actualValue).isNull()
            assertThat(result.justification).isEqualTo("Fact 'keywordMatched' ausente no contexto")
        }

        @Test
        fun `returns not satisfied when different fact is present`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue("MR")
            )
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
            assertThat(result.actualValue).isNull()
            assertThat(result.justification).contains("ausente no contexto")
        }
    }

    @Nested
    @DisplayName("Group expression")
    inner class GroupExpression {

        @Test
        fun `throws UnsupportedOperationException for Group`() {
            val group = Group(
                logicalOperator = LogicalOperator.AND,
                expressions = listOf(
                    Condition(
                        factName = FactName("keywordMatched"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.BooleanValue(true)
                    )
                )
            )

            assertThatThrownBy {
                evaluator.evaluate(group, emptyMap())
            }.isInstanceOf(UnsupportedOperationException::class.java)
                .hasMessageContaining("Group expressions não são suportadas no MVP")
        }
    }

    @Nested
    @DisplayName("Evaluation metadata")
    inner class EvaluationMetadata {

        @Test
        fun `result contains correct factName operator and expectedValue`() {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.EnumValue("AR")
            )
            val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue("AR"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.factName).isEqualTo(FactName("customerRisk"))
            assertThat(result.operator).isEqualTo(ComparisonOperator.EQUALS)
            assertThat(result.expectedValue).isEqualTo(FactValue.EnumValue("AR"))
            assertThat(result.actualValue).isEqualTo(FactValue.EnumValue("AR"))
        }
    }

    @Nested
    @DisplayName("EQUALS on StringValue")
    inner class EqualsString {

        @Test
        fun `satisfied when both string values match`() {
            val condition = Condition(
                factName = FactName("description"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.StringValue("hello")
            )
            val facts = mapOf(FactName("description") to FactValue.StringValue("hello"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `not satisfied when string values differ`() {
            val condition = Condition(
                factName = FactName("description"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.StringValue("hello")
            )
            val facts = mapOf(FactName("description") to FactValue.StringValue("world"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }
    }

    @Nested
    @DisplayName("EQUALS on NumberValue")
    inner class EqualsNumber {

        @Test
        fun `satisfied when number values are equal`() {
            val condition = Condition(
                factName = FactName("amount"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.NumberValue(java.math.BigDecimal("100.00"))
            )
            val facts = mapOf(FactName("amount") to FactValue.NumberValue(java.math.BigDecimal("100.00")))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `not satisfied when number values differ`() {
            val condition = Condition(
                factName = FactName("amount"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.NumberValue(java.math.BigDecimal("100"))
            )
            val facts = mapOf(FactName("amount") to FactValue.NumberValue(java.math.BigDecimal("200")))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }
    }

    @Nested
    @DisplayName("valuesEqual mismatched types")
    inner class ValuesEqualMismatch {

        @Test
        fun `returns false when actual is BooleanValue but expected is EnumValue`() {
            val condition = Condition(
                factName = FactName("mixed"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.EnumValue("true")
            )
            val facts = mapOf(FactName("mixed") to FactValue.BooleanValue(true))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }

        @Test
        fun `returns false when actual is NumberValue but expected is StringValue`() {
            val condition = Condition(
                factName = FactName("mixed"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.StringValue("100")
            )
            val facts = mapOf(FactName("mixed") to FactValue.NumberValue(java.math.BigDecimal("100")))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }
    }

    @Nested
    @DisplayName("Unsupported operator")
    inner class UnsupportedOperator {

        @Test
        fun `throws UnsupportedOperationException for GREATER_THAN operator`() {
            val condition = Condition(
                factName = FactName("amount"),
                operator = ComparisonOperator.GREATER_THAN,
                expectedValue = FactValue.NumberValue(java.math.BigDecimal("100"))
            )
            val facts = mapOf(FactName("amount") to FactValue.NumberValue(java.math.BigDecimal("200")))

            assertThatThrownBy {
                evaluator.evaluate(condition, facts)
            }.isInstanceOf(UnsupportedOperationException::class.java)
                .hasMessageContaining("GREATER_THAN")
        }

        @Test
        fun `throws UnsupportedOperationException for CONTAINS operator`() {
            val condition = Condition(
                factName = FactName("desc"),
                operator = ComparisonOperator.CONTAINS,
                expectedValue = FactValue.StringValue("keyword")
            )
            val facts = mapOf(FactName("desc") to FactValue.StringValue("some keyword here"))

            assertThatThrownBy {
                evaluator.evaluate(condition, facts)
            }.isInstanceOf(UnsupportedOperationException::class.java)
                .hasMessageContaining("CONTAINS")
        }

        @Test
        fun `throws UnsupportedOperationException for LESS_THAN operator`() {
            val condition = Condition(
                factName = FactName("amount"),
                operator = ComparisonOperator.LESS_THAN,
                expectedValue = FactValue.NumberValue(java.math.BigDecimal("100"))
            )
            val facts = mapOf(FactName("amount") to FactValue.NumberValue(java.math.BigDecimal("50")))

            assertThatThrownBy {
                evaluator.evaluate(condition, facts)
            }.isInstanceOf(UnsupportedOperationException::class.java)
                .hasMessageContaining("LESS_THAN")
        }
    }

    @Nested
    @DisplayName("EQUALS with MoneyValue type")
    inner class EqualsMoneyValue {

        @Test
        fun `not satisfied when comparing MoneyValue with NumberValue (type mismatch in valuesEqual)`() {
            val condition = Condition(
                factName = FactName("txAmount"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.MoneyValue(java.math.BigDecimal("1000"), "BRL")
            )
            val facts = mapOf(FactName("txAmount") to FactValue.NumberValue(java.math.BigDecimal("1000")))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }

        @Test
        fun `displayValue for MoneyValue shows amount and currency`() {
            val condition = Condition(
                factName = FactName("txAmount"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.MoneyValue(java.math.BigDecimal("5000"), "USD")
            )
            val facts = mapOf(FactName("txAmount") to FactValue.MoneyValue(java.math.BigDecimal("5000"), "USD"))

            val result = evaluator.evaluate(condition, facts)

            // MoneyValue → else branch in valuesEqual → false (no specific MoneyValue comparison)
            assertThat(result.satisfied).isFalse()
            assertThat(result.justification).contains("5000")
            assertThat(result.justification).contains("USD")
        }
    }

    @Nested
    @DisplayName("NOT_EQUALS on StringValue")
    inner class NotEqualsString {

        @Test
        fun `satisfied when string values differ`() {
            val condition = Condition(
                factName = FactName("description"),
                operator = ComparisonOperator.NOT_EQUALS,
                expectedValue = FactValue.StringValue("hello")
            )
            val facts = mapOf(FactName("description") to FactValue.StringValue("world"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `not satisfied when string values are equal`() {
            val condition = Condition(
                factName = FactName("description"),
                operator = ComparisonOperator.NOT_EQUALS,
                expectedValue = FactValue.StringValue("same")
            )
            val facts = mapOf(FactName("description") to FactValue.StringValue("same"))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }
    }

    @Nested
    @DisplayName("NOT_EQUALS on NumberValue")
    inner class NotEqualsNumber {

        @Test
        fun `satisfied when number values differ`() {
            val condition = Condition(
                factName = FactName("amount"),
                operator = ComparisonOperator.NOT_EQUALS,
                expectedValue = FactValue.NumberValue(java.math.BigDecimal("100"))
            )
            val facts = mapOf(FactName("amount") to FactValue.NumberValue(java.math.BigDecimal("200")))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isTrue()
        }

        @Test
        fun `not satisfied when number values are equal`() {
            val condition = Condition(
                factName = FactName("amount"),
                operator = ComparisonOperator.NOT_EQUALS,
                expectedValue = FactValue.NumberValue(java.math.BigDecimal("100"))
            )
            val facts = mapOf(FactName("amount") to FactValue.NumberValue(java.math.BigDecimal("100")))

            val result = evaluator.evaluate(condition, facts)

            assertThat(result.satisfied).isFalse()
        }
    }
}
