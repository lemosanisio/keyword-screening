package br.com.decision.domain.service

import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.ExpressionOutcome
import br.com.decision.domain.model.Group
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue

/**
 * Expression Evaluator — comparador puro.
 * Avalia uma única Expression contra o conjunto de Facts.
 * Classe pura sem Spring annotations — registrada via @Configuration.
 */
class ExpressionEvaluator {

    fun evaluate(expression: Expression, facts: Map<FactName, FactValue>): ExpressionEvaluation {
        return when (expression) {
            is Condition -> evaluateCondition(expression, facts)
            is Group -> throw UnsupportedOperationException(
                "Group expressions não são suportadas no MVP"
            )
        }
    }

    private fun evaluateCondition(condition: Condition, facts: Map<FactName, FactValue>): ExpressionEvaluation {
        val factName = condition.factName
        val actualValue = facts[factName]

        if (actualValue == null) {
            return ExpressionEvaluation(
                factName = factName,
                operator = condition.operator,
                expectedValue = condition.expectedValue,
                actualValue = null,
                satisfied = false,
                justification = "Fact '${factName.value}' ausente no contexto",
                outcome = ExpressionOutcome.INDETERMINATE,
            )
        }

        return when (condition.operator) {
            ComparisonOperator.EQUALS -> evaluateEquals(condition, actualValue)
            ComparisonOperator.NOT_EQUALS -> evaluateNotEquals(condition, actualValue)
            ComparisonOperator.GREATER_THAN_OR_EQUAL -> evaluateGte(condition, actualValue)
            else -> throw UnsupportedOperationException(
                "Operador ${condition.operator} não suportado no MVP"
            )
        }
    }

    private fun evaluateEquals(condition: Condition, actualValue: FactValue): ExpressionEvaluation {
        val satisfied = valuesEqual(actualValue, condition.expectedValue)
        val actualDisplay = displayValue(actualValue)
        val expectedDisplay = displayValue(condition.expectedValue)

        val justification = if (satisfied) {
            "Fact '${condition.factName.value}' ($actualDisplay) é igual a $expectedDisplay"
        } else {
            "Fact '${condition.factName.value}' ($actualDisplay) não é igual a $expectedDisplay"
        }

        return ExpressionEvaluation(
            factName = condition.factName,
            operator = condition.operator,
            expectedValue = condition.expectedValue,
            actualValue = actualValue,
            satisfied = satisfied,
            justification = justification
        )
    }

    private fun evaluateNotEquals(condition: Condition, actualValue: FactValue): ExpressionEvaluation {
        val equal = valuesEqual(actualValue, condition.expectedValue)
        val satisfied = !equal
        val actualDisplay = displayValue(actualValue)
        val expectedDisplay = displayValue(condition.expectedValue)

        val justification = if (satisfied) {
            "Fact '${condition.factName.value}' ($actualDisplay) é diferente de $expectedDisplay"
        } else {
            "Fact '${condition.factName.value}' ($actualDisplay) é igual a $expectedDisplay"
        }

        return ExpressionEvaluation(
            factName = condition.factName,
            operator = condition.operator,
            expectedValue = condition.expectedValue,
            actualValue = actualValue,
            satisfied = satisfied,
            justification = justification
        )
    }

    private fun evaluateGte(condition: Condition, actualValue: FactValue): ExpressionEvaluation {
        require(actualValue is FactValue.EnumValue && condition.expectedValue is FactValue.EnumValue) {
            "GREATER_THAN_OR_EQUAL requer EnumValue para ambos os lados"
        }

        val actualRisk = CustomerRisk.valueOf(actualValue.value)
        val expectedRisk = CustomerRisk.valueOf(condition.expectedValue.value)
        val satisfied = actualRisk.ordinal >= expectedRisk.ordinal

        val justification = if (satisfied) {
            "Fact '${condition.factName.value}' ($actualRisk) é >= $expectedRisk (ordinal ${actualRisk.ordinal} >= ${expectedRisk.ordinal})"
        } else {
            "Fact '${condition.factName.value}' ($actualRisk) não é >= $expectedRisk (ordinal ${actualRisk.ordinal} < ${expectedRisk.ordinal})"
        }

        return ExpressionEvaluation(
            factName = condition.factName,
            operator = condition.operator,
            expectedValue = condition.expectedValue,
            actualValue = actualValue,
            satisfied = satisfied,
            justification = justification
        )
    }

    private fun valuesEqual(actual: FactValue, expected: FactValue): Boolean {
        return when {
            actual is FactValue.BooleanValue && expected is FactValue.BooleanValue ->
                actual.value == expected.value
            actual is FactValue.EnumValue && expected is FactValue.EnumValue ->
                actual.value == expected.value
            actual is FactValue.StringValue && expected is FactValue.StringValue ->
                actual.value == expected.value
            actual is FactValue.NumberValue && expected is FactValue.NumberValue ->
                actual.value.compareTo(expected.value) == 0
            else -> false
        }
    }

    private fun displayValue(value: FactValue): String {
        return when (value) {
            is FactValue.BooleanValue -> value.value.toString()
            is FactValue.EnumValue -> value.value
            is FactValue.StringValue -> value.value
            is FactValue.NumberValue -> value.value.toPlainString()
            is FactValue.MoneyValue -> "${value.amount.toPlainString()} ${value.currency}"
        }
    }
}
