package br.com.decision.domain.service

import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Property-Based Tests for [RuleEngine].
 *
 * **Property 3: Decision Logic — AND Semantics**
 * **Validates: Requirements 5.6, 5.8, 6.4, 6.5, 6.6**
 *
 * Properties verified:
 * 1. allSatisfied == true iff ALL individual evaluations return satisfied == true
 * 2. allSatisfied == false if any single evaluation returns satisfied == false
 * 3. Empty expressions list → allSatisfied == false
 * 4. Number of evaluations == number of expressions (always)
 */
class RuleEnginePropertyTest {

    private val evaluator = ExpressionEvaluator()
    private val ruleEngine = RuleEngine(evaluator)

    // --- Random Generators ---

    private val booleanFactNames = listOf("keywordMatched", "pep", "highAmount", "sanctionsHit")
    private val enumFactNames = listOf("customerRisk")

    private fun randomCustomerRisk(): CustomerRisk =
        CustomerRisk.entries[Random.nextInt(CustomerRisk.entries.size)]

    /**
     * Generates a Condition with type-compatible FactValue for the chosen operator.
     * - GTE always uses enum facts (CustomerRisk) on both sides
     * - EQUALS / NOT_EQUALS randomly picks between boolean facts and enum facts
     */
    private fun randomCondition(): Condition {
        val operators = listOf(
            ComparisonOperator.EQUALS,
            ComparisonOperator.NOT_EQUALS,
            ComparisonOperator.GREATER_THAN_OR_EQUAL
        )
        val operator = operators[Random.nextInt(operators.size)]
        val boolVal = Random.nextBoolean()
        val riskVal = randomCustomerRisk()
        val boolFactName = booleanFactNames[Random.nextInt(booleanFactNames.size)]
        val enumFactName = enumFactNames[Random.nextInt(enumFactNames.size)]

        return when (operator) {
            ComparisonOperator.GREATER_THAN_OR_EQUAL -> Condition(
                factName = FactName(enumFactName),
                operator = operator,
                expectedValue = FactValue.EnumValue(riskVal.name)
            )
            else -> {
                // Pick boolean or enum condition based on boolVal as coin flip
                if (boolVal) {
                    Condition(
                        factName = FactName(boolFactName),
                        operator = operator,
                        expectedValue = FactValue.BooleanValue(boolVal)
                    )
                } else {
                    Condition(
                        factName = FactName(enumFactName),
                        operator = operator,
                        expectedValue = FactValue.EnumValue(riskVal.name)
                    )
                }
            }
        }
    }

    /** Generate a list of 1-10 conditions */
    private fun randomConditions(): List<Expression> {
        val count = Random.nextInt(1, 11)
        return (0 until count).map { randomCondition() }
    }

    /**
     * Generates a fact map that may or may not contain facts for the pools.
     * Each fact has a chance to be present or absent, exercising absent-fact paths.
     * Type safety: boolean facts get BooleanValue, enum facts get EnumValue.
     */
    private fun randomFacts(): Map<FactName, FactValue> {
        val inclKeyword = Random.nextBoolean()
        val inclRisk = Random.nextBoolean()
        val inclPep = Random.nextBoolean()
        val inclHighAmount = Random.nextBoolean()
        val inclSanctions = Random.nextBoolean()
        val keywordVal = Random.nextBoolean()
        val riskVal = randomCustomerRisk()
        val pepVal = Random.nextBoolean()
        val highAmountVal = Random.nextBoolean()
        val sanctionsVal = Random.nextBoolean()

        return buildMap {
            if (inclKeyword) put(FactName("keywordMatched"), FactValue.BooleanValue(keywordVal))
            if (inclRisk) put(FactName("customerRisk"), FactValue.EnumValue(riskVal.name))
            if (inclPep) put(FactName("pep"), FactValue.BooleanValue(pepVal))
            if (inclHighAmount) put(FactName("highAmount"), FactValue.BooleanValue(highAmountVal))
            if (inclSanctions) put(FactName("sanctionsHit"), FactValue.BooleanValue(sanctionsVal))
        }
    }

    // --- Property Tests ---

    @RepeatedTest(200)
    @DisplayName("allSatisfied is true iff all conditions are satisfied")
    fun `allSatisfied is true iff all conditions are satisfied`() {
        val conditions = randomConditions()
        val facts = randomFacts()

        val result = ruleEngine.evaluate(facts, conditions)

        val expectedAll = result.evaluations.isNotEmpty() && result.evaluations.all { it.satisfied }
        assertEquals(expectedAll, result.allSatisfied)
    }

    @RepeatedTest(200)
    @DisplayName("allSatisfied is false if any single evaluation is not satisfied")
    fun `allSatisfied is false if any single evaluation is not satisfied`() {
        val conditions = randomConditions()
        val facts = randomFacts()

        val result = ruleEngine.evaluate(facts, conditions)

        if (result.evaluations.any { !it.satisfied }) {
            assertEquals(false, result.allSatisfied)
        }
    }

    @RepeatedTest(200)
    @DisplayName("empty expressions list returns allSatisfied=false")
    fun `empty expressions list returns allSatisfied=false`() {
        val facts = randomFacts()

        val result = ruleEngine.evaluate(facts, emptyList())

        assertEquals(false, result.allSatisfied)
        assertEquals(0, result.evaluations.size)
    }

    @RepeatedTest(200)
    @DisplayName("number of evaluations equals number of expressions")
    fun `number of evaluations equals number of expressions`() {
        val conditions = randomConditions()
        val facts = randomFacts()

        val result = ruleEngine.evaluate(facts, conditions)

        assertEquals(conditions.size, result.evaluations.size)
    }
}
