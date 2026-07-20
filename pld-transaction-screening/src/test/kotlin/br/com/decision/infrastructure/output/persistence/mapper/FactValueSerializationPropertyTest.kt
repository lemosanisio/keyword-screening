package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.math.BigDecimal
import kotlin.random.Random

/**
 * Property-Based Tests for FactValue serialization round-trip through RuleConfigurationMapper.
 *
 * **Property 9: FactValue Serialization Round-Trip**
 * **Validates: Requirements 13.6, 9.1**
 *
 * Property verified:
 * For every FactValue subtype (BooleanValue, EnumValue, NumberValue, StringValue, MoneyValue):
 *   serialize → deserialize → result equals original
 *
 * The test exercises the round-trip through the RuleConfigurationMapper by wrapping FactValues
 * in a Condition expression, serializing the full Expression to Map, then deserializing back.
 * This tests the actual production code path (mapFromExpression/mapToExpression) which
 * internally uses mapFromFactValue/mapToFactValue.
 */
class FactValueSerializationPropertyTest {

    private val mapper = RuleConfigurationMapper()

    @RepeatedTest(200)
    @DisplayName("BooleanValue round-trip: serialize then deserialize yields original")
    fun `BooleanValue round-trip`() {
        val bool = Random.nextBoolean()
        val original = FactValue.BooleanValue(bool)
        val deserialized = roundTrip(mapper, original)
        assertEquals(original, deserialized)
    }

    @RepeatedTest(200)
    @DisplayName("EnumValue round-trip: serialize then deserialize yields original")
    fun `EnumValue round-trip`() {
        val riskName = CustomerRisk.entries.random().name
        val original = FactValue.EnumValue(riskName)
        val deserialized = roundTrip(mapper, original)
        assertEquals(original, deserialized)
    }

    @RepeatedTest(200)
    @DisplayName("NumberValue round-trip: serialize then deserialize yields original")
    fun `NumberValue round-trip`() {
        val number = BigDecimal(Random.nextDouble(-999999.0, 999999.0).toString())
        val normalized = BigDecimal(number.toPlainString())
        val original = FactValue.NumberValue(normalized)
        val deserialized = roundTrip(mapper, original)
        // BigDecimal compareTo equality (ignoring scale differences from serialization)
        assertTrue(deserialized is FactValue.NumberValue)
        assertEquals(0, (deserialized as FactValue.NumberValue).value.compareTo(original.value))
    }

    @RepeatedTest(200)
    @DisplayName("StringValue round-trip: serialize then deserialize yields original")
    fun `StringValue round-trip`() {
        val length = Random.nextInt(0, 101)
        val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf(' ', '-', '_', '.', '!')
        val str = (1..length).map { chars.random() }.joinToString("")
        val original = FactValue.StringValue(str)
        val deserialized = roundTrip(mapper, original)
        assertEquals(original, deserialized)
    }

    @RepeatedTest(200)
    @DisplayName("MoneyValue round-trip: serialize then deserialize yields original")
    fun `MoneyValue round-trip`() {
        val amount = BigDecimal(Random.nextDouble(-999999.0, 999999.0).toString())
        val normalizedAmount = BigDecimal(amount.toPlainString())
        val currency = listOf("BRL", "USD", "EUR").random()
        val original = FactValue.MoneyValue(normalizedAmount, currency)
        val deserialized = roundTrip(mapper, original)
        // MoneyValue compareTo equality for amount
        assertTrue(deserialized is FactValue.MoneyValue)
        val money = deserialized as FactValue.MoneyValue
        assertEquals(0, money.amount.compareTo(original.amount))
        assertEquals(original.currency, money.currency)
    }

    companion object {
        /**
         * Performs a round-trip through the mapper's production code path:
         * FactValue → Condition → Expression Map → Condition → FactValue
         *
         * Uses toEntity/toDomain on a minimal RuleConfiguration to exercise
         * the private mapFromFactValue/mapToFactValue methods indirectly.
         */
        fun roundTrip(mapper: RuleConfigurationMapper, factValue: FactValue): FactValue {
            val condition = Condition(
                factName = FactName("testFact"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = factValue
            )

            // Use reflection to access the private mapFromExpression method
            val mapFromExpression = RuleConfigurationMapper::class.java
                .getDeclaredMethod("mapFromExpression", br.com.decision.domain.model.Expression::class.java)
            mapFromExpression.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val serialized = mapFromExpression.invoke(mapper, condition) as Map<String, Any?>

            // Use reflection to access the private mapToExpression method
            val mapToExpression = RuleConfigurationMapper::class.java
                .getDeclaredMethod("mapToExpression", Map::class.java)
            mapToExpression.isAccessible = true

            val deserialized = mapToExpression.invoke(mapper, serialized) as Condition

            return deserialized.expectedValue
        }
    }
}
