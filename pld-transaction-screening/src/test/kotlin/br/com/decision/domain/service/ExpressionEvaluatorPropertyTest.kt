package br.com.decision.domain.service

import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Property-Based Tests for [ExpressionEvaluator].
 *
 * **Property 2: Expression Evaluation Correctness**
 * **Validates: Requirements 5.3, 5.4, 5.5, 5.7**
 *
 * Properties verified:
 * 1. EQUALS is satisfied iff actual == expected (Boolean and Enum)
 * 2. NOT_EQUALS is satisfied iff actual != expected (Boolean and Enum)
 * 3. GTE on CustomerRisk is satisfied iff actual.ordinal >= expected.ordinal
 * 4. Absent fact → always not satisfied, actualValue is null
 * 5. Every evaluation has non-blank justification
 */
class ExpressionEvaluatorPropertyTest {

    private val evaluator = ExpressionEvaluator()

    private fun randomCustomerRisk(): CustomerRisk =
        CustomerRisk.entries[Random.nextInt(CustomerRisk.entries.size)]

    private fun randomComparisonOperator(): ComparisonOperator =
        ComparisonOperator.entries[Random.nextInt(ComparisonOperator.entries.size)]

    @RepeatedTest(200)
    @DisplayName("EQUALS on BooleanValue is satisfied if and only if values are equal")
    fun `EQUALS on BooleanValue is satisfied if and only if values are equal`() {
        val actualBool = Random.nextBoolean()
        val expectedBool = Random.nextBoolean()

        val condition = Condition(
            factName = FactName("keywordMatched"),
            operator = ComparisonOperator.EQUALS,
            expectedValue = FactValue.BooleanValue(expectedBool)
        )
        val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(actualBool))

        val result = evaluator.evaluate(condition, facts)

        assertEquals(actualBool == expectedBool, result.satisfied)
        assertEquals(FactValue.BooleanValue(actualBool), result.actualValue)
    }

    @RepeatedTest(200)
    @DisplayName("EQUALS on EnumValue (CustomerRisk) is satisfied if and only if values are equal")
    fun `EQUALS on EnumValue (CustomerRisk) is satisfied if and only if values are equal`() {
        val actualRisk = randomCustomerRisk()
        val expectedRisk = randomCustomerRisk()

        val condition = Condition(
            factName = FactName("customerRisk"),
            operator = ComparisonOperator.EQUALS,
            expectedValue = FactValue.EnumValue(expectedRisk.name)
        )
        val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue(actualRisk.name))

        val result = evaluator.evaluate(condition, facts)

        assertEquals(actualRisk == expectedRisk, result.satisfied)
        assertEquals(FactValue.EnumValue(actualRisk.name), result.actualValue)
    }

    @RepeatedTest(200)
    @DisplayName("NOT_EQUALS on BooleanValue is satisfied if and only if values differ")
    fun `NOT_EQUALS on BooleanValue is satisfied if and only if values differ`() {
        val actualBool = Random.nextBoolean()
        val expectedBool = Random.nextBoolean()

        val condition = Condition(
            factName = FactName("keywordMatched"),
            operator = ComparisonOperator.NOT_EQUALS,
            expectedValue = FactValue.BooleanValue(expectedBool)
        )
        val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(actualBool))

        val result = evaluator.evaluate(condition, facts)

        assertEquals(actualBool != expectedBool, result.satisfied)
        assertEquals(FactValue.BooleanValue(actualBool), result.actualValue)
    }

    @RepeatedTest(200)
    @DisplayName("NOT_EQUALS on EnumValue (CustomerRisk) is satisfied if and only if values differ")
    fun `NOT_EQUALS on EnumValue (CustomerRisk) is satisfied if and only if values differ`() {
        val actualRisk = randomCustomerRisk()
        val expectedRisk = randomCustomerRisk()

        val condition = Condition(
            factName = FactName("customerRisk"),
            operator = ComparisonOperator.NOT_EQUALS,
            expectedValue = FactValue.EnumValue(expectedRisk.name)
        )
        val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue(actualRisk.name))

        val result = evaluator.evaluate(condition, facts)

        assertEquals(actualRisk != expectedRisk, result.satisfied)
        assertEquals(FactValue.EnumValue(actualRisk.name), result.actualValue)
    }

    @RepeatedTest(200)
    @DisplayName("GTE on CustomerRisk is satisfied if and only if actual.ordinal >= expected.ordinal")
    fun `GTE on CustomerRisk is satisfied if and only if actual ordinal ge expected ordinal`() {
        val actualRisk = randomCustomerRisk()
        val expectedRisk = randomCustomerRisk()

        val condition = Condition(
            factName = FactName("customerRisk"),
            operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
            expectedValue = FactValue.EnumValue(expectedRisk.name)
        )
        val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue(actualRisk.name))

        val result = evaluator.evaluate(condition, facts)

        assertEquals(actualRisk.ordinal >= expectedRisk.ordinal, result.satisfied)
        assertEquals(FactValue.EnumValue(actualRisk.name), result.actualValue)
    }

    @RepeatedTest(200)
    @DisplayName("absent fact always results in not satisfied with null actualValue")
    fun `absent fact always results in not satisfied with null actualValue`() {
        val risk = randomCustomerRisk()
        val operator = randomComparisonOperator()

        // Only test operators supported in MVP to avoid UnsupportedOperationException
        val supportedOperators = listOf(
            ComparisonOperator.EQUALS,
            ComparisonOperator.NOT_EQUALS,
            ComparisonOperator.GREATER_THAN_OR_EQUAL
        )
        if (operator in supportedOperators) {
            val condition = Condition(
                factName = FactName("customerRisk"),
                operator = operator,
                expectedValue = FactValue.EnumValue(risk.name)
            )
            val facts = emptyMap<FactName, FactValue>()

            val result = evaluator.evaluate(condition, facts)

            assertEquals(false, result.satisfied)
            assertNull(result.actualValue)
        }
    }

    @RepeatedTest(200)
    @DisplayName("every evaluation produces a non-blank justification")
    fun `every evaluation produces a non-blank justification`() {
        val actualRisk = randomCustomerRisk()
        val expectedRisk = randomCustomerRisk()

        val condition = Condition(
            factName = FactName("customerRisk"),
            operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
            expectedValue = FactValue.EnumValue(expectedRisk.name)
        )
        val facts = mapOf(FactName("customerRisk") to FactValue.EnumValue(actualRisk.name))

        val result = evaluator.evaluate(condition, facts)

        assertTrue(result.justification.isNotBlank())
    }

    @RepeatedTest(200)
    @DisplayName("every evaluation of absent fact produces a non-blank justification")
    fun `every evaluation of absent fact produces a non-blank justification`() {
        val boolValue = Random.nextBoolean()

        val condition = Condition(
            factName = FactName("keywordMatched"),
            operator = ComparisonOperator.EQUALS,
            expectedValue = FactValue.BooleanValue(boolValue)
        )
        val facts = emptyMap<FactName, FactValue>()

        val result = evaluator.evaluate(condition, facts)

        assertTrue(result.justification.isNotBlank())
        assertEquals(false, result.satisfied)
    }
}
