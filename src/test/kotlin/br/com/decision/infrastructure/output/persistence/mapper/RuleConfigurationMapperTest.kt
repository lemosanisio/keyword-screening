package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.*
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.ConfigurationVersionEntity
import br.com.decision.infrastructure.output.persistence.entity.RuleConfigurationEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import br.com.decision.domain.model.LogicalOperator

@DisplayName("RuleConfigurationMapper")
class RuleConfigurationMapperTest {

    private val mapper = RuleConfigurationMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly with CONDITION expressions")
    fun toDomainMapsAllFields() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val now = Instant.now()
        val entity = RuleConfigurationEntity(
            id = id,
            ruleId = ruleId,
            expressions = listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "amount",
                    "operator" to "GREATER_THAN",
                    "expectedValue" to mapOf("type" to "NUMBER", "value" to 1000)
                )
            ),
            actions = listOf("GENERATE_ALERT"),
            active = true,
            draft = false,
            currentVersion = 2,
            createdBy = "analyst-01",
            createdAt = now,
            updatedAt = now,
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)

        assertEquals(id, domain.id)
        assertEquals(RuleId(ruleId), domain.ruleId)
        assertEquals(1, domain.expressions.size)
        val condition = domain.expressions[0] as Condition
        assertEquals(FactName("amount"), condition.factName)
        assertEquals(ComparisonOperator.GREATER_THAN, condition.operator)
        assertTrue(domain.active)
        assertFalse(domain.draft)
        assertEquals(ConfigurationVersion(2), domain.currentVersion)
        assertEquals("analyst-01", domain.createdBy)
        assertEquals(now, domain.createdAt)
        assertEquals(now, domain.updatedAt)
    }

    @Test
    @DisplayName("toDomain maps GROUP expressions correctly")
    fun toDomainMapsGroupExpression() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val now = Instant.now()
        val entity = RuleConfigurationEntity(
            id = id,
            ruleId = ruleId,
            expressions = listOf(
                mapOf(
                    "type" to "GROUP",
                    "logicalOperator" to "AND",
                    "expressions" to listOf(
                        mapOf(
                            "type" to "CONDITION",
                            "factName" to "risk",
                            "operator" to "EQUALS",
                            "expectedValue" to mapOf("type" to "ENUM", "value" to "HIGH")
                        )
                    )
                )
            ),
            actions = listOf("BLOCK"),
            active = false,
            draft = true,
            currentVersion = 1,
            createdBy = "admin",
            createdAt = now,
            updatedAt = now,
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)

        val group = domain.expressions[0] as Group
        assertEquals(LogicalOperator.AND, group.logicalOperator)
        assertEquals(1, group.expressions.size)
        val nestedCondition = group.expressions[0] as Condition
        assertEquals(FactName("risk"), nestedCondition.factName)
        assertEquals(FactValue.EnumValue("HIGH"), nestedCondition.expectedValue)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val now = Instant.now()
        val domain = RuleConfiguration(
            id = id,
            ruleId = RuleId(ruleId),
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
            currentVersion = ConfigurationVersion(3),
            versions = emptyList(),
            createdBy = "analyst",
            createdAt = now,
            updatedAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(id, entity.id)
        assertEquals(ruleId, entity.ruleId)
        assertEquals(1, entity.expressions.size)
        assertEquals("CONDITION", entity.expressions[0]["type"])
        assertEquals("keywordMatched", entity.expressions[0]["factName"])
        assertEquals("EQUALS", entity.expressions[0]["operator"])
        assertEquals(listOf("GENERATE_ALERT"), entity.actions)
        assertTrue(entity.active)
        assertFalse(entity.draft)
        assertEquals(3, entity.currentVersion)
        assertEquals("analyst", entity.createdBy)
    }

    @Test
    @DisplayName("versionToDomain maps ConfigurationVersionEntity correctly")
    fun versionToDomainMapsCorrectly() {
        val now = Instant.now()
        val versionEntity = ConfigurationVersionEntity(
            id = UUID.randomUUID(),
            configurationId = UUID.randomUUID(),
            version = 2,
            expressions = listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "txAmount",
                    "operator" to "GREATER_THAN_OR_EQUAL",
                    "expectedValue" to mapOf("type" to "MONEY", "amount" to 5000, "currency" to "BRL")
                )
            ),
            actions = listOf("REVIEW"),
            active = true,
            createdBy = "admin",
            createdAt = now
        )

        val domain = mapper.versionToDomain(versionEntity)

        assertEquals(ConfigurationVersion(2), domain.version)
        assertEquals(1, domain.expressions.size)
        val condition = domain.expressions[0] as Condition
        assertEquals(FactName("txAmount"), condition.factName)
        assertEquals(ComparisonOperator.GREATER_THAN_OR_EQUAL, condition.operator)
        val moneyValue = condition.expectedValue as FactValue.MoneyValue
        assertEquals(0, BigDecimal("5000").compareTo(moneyValue.amount))
        assertEquals("BRL", moneyValue.currency)
        assertEquals(listOf(Action.REVIEW), domain.actions)
        assertTrue(domain.active)
        assertEquals("admin", domain.createdBy)
    }

    @Test
    @DisplayName("maps all FactValue types in expressions correctly")
    fun mapsAllFactValueTypes() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val now = Instant.now()
        val domain = RuleConfiguration(
            id = id,
            ruleId = RuleId(ruleId),
            expressions = listOf(
                Condition(FactName("boolFact"), ComparisonOperator.EQUALS, FactValue.BooleanValue(true)),
                Condition(FactName("enumFact"), ComparisonOperator.EQUALS, FactValue.EnumValue("HIGH")),
                Condition(FactName("numFact"), ComparisonOperator.GREATER_THAN, FactValue.NumberValue(BigDecimal("100"))),
                Condition(FactName("strFact"), ComparisonOperator.CONTAINS, FactValue.StringValue("keyword")),
                Condition(
                    FactName("moneyFact"),
                    ComparisonOperator.LESS_THAN,
                    FactValue.MoneyValue(BigDecimal("50000"), "USD")
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            active = true,
            draft = false,
            currentVersion = ConfigurationVersion(1),
            versions = emptyList(),
            createdBy = "test",
            createdAt = now,
            updatedAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(5, result.expressions.size)
        val cond0 = result.expressions[0] as Condition
        assertEquals(FactValue.BooleanValue(true), cond0.expectedValue)
        val cond1 = result.expressions[1] as Condition
        assertEquals(FactValue.EnumValue("HIGH"), cond1.expectedValue)
        val cond2 = result.expressions[2] as Condition
        assertEquals(0, BigDecimal("100").compareTo((cond2.expectedValue as FactValue.NumberValue).value))
        val cond3 = result.expressions[3] as Condition
        assertEquals(FactValue.StringValue("keyword"), cond3.expectedValue)
        val cond4 = result.expressions[4] as Condition
        val money = cond4.expectedValue as FactValue.MoneyValue
        assertEquals(0, BigDecimal("50000").compareTo(money.amount))
        assertEquals("USD", money.currency)
    }

    @Test
    @DisplayName("handles empty expressions list")
    fun handlesEmptyExpressions() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val now = Instant.now()
        val entity = RuleConfigurationEntity(
            id = id,
            ruleId = ruleId,
            expressions = emptyList(),
            actions = listOf("IGNORE"),
            active = false,
            draft = true,
            currentVersion = 1,
            createdBy = "test",
            createdAt = now,
            updatedAt = now,
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)

        assertTrue(domain.expressions.isEmpty())
    }

    @Test
    @DisplayName("toDomain: unknown expression type throws IllegalArgumentException")
    fun toDomainUnknownExpressionTypeThrows() {
        val entity = RuleConfigurationEntity(
            id = UUID.randomUUID(),
            ruleId = UUID.randomUUID(),
            expressions = listOf(
                mapOf("type" to "INVALID_TYPE", "factName" to "x", "operator" to "EQUALS")
            ),
            actions = listOf("IGNORE"),
            active = false,
            draft = true,
            currentVersion = 1,
            createdBy = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            versions = emptyList()
        )

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            mapper.toDomain(entity)
        }
    }

    @Test
    @DisplayName("toDomain: expression without type defaults to CONDITION")
    fun toDomainExpressionWithoutTypeDefaultsToCondition() {
        val entity = RuleConfigurationEntity(
            id = UUID.randomUUID(),
            ruleId = UUID.randomUUID(),
            expressions = listOf(
                mapOf(
                    "factName" to "keywordMatched",
                    "operator" to "EQUALS",
                    "expectedValue" to mapOf("type" to "BOOLEAN", "value" to true)
                )
            ),
            actions = listOf("GENERATE_ALERT"),
            active = true,
            draft = false,
            currentVersion = 1,
            createdBy = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)
        assertEquals(1, domain.expressions.size)
        val condition = domain.expressions[0] as Condition
        assertEquals(FactName("keywordMatched"), condition.factName)
    }

    @Test
    @DisplayName("toEntity and toDomain: GROUP expression round-trip")
    fun groupExpressionRoundTrip() {
        val domain = RuleConfiguration(
            id = UUID.randomUUID(),
            ruleId = RuleId(UUID.randomUUID()),
            expressions = listOf(
                Group(
                    logicalOperator = LogicalOperator.OR,
                    expressions = listOf(
                        Condition(FactName("keywordMatched"), ComparisonOperator.EQUALS, FactValue.BooleanValue(true)),
                        Condition(FactName("risk"), ComparisonOperator.EQUALS, FactValue.EnumValue("HIGH"))
                    )
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            active = true,
            draft = false,
            currentVersion = ConfigurationVersion(1),
            versions = emptyList(),
            createdBy = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))
        assertEquals(1, result.expressions.size)
        val group = result.expressions[0] as Group
        assertEquals(LogicalOperator.OR, group.logicalOperator)
        assertEquals(2, group.expressions.size)
    }

    @Test
    @DisplayName("toDomain: GROUP expression with null expressions list gets empty list")
    fun groupExpressionWithNullExpressionsList() {
        val entity = RuleConfigurationEntity(
            id = UUID.randomUUID(),
            ruleId = UUID.randomUUID(),
            expressions = listOf(
                mapOf(
                    "type" to "GROUP",
                    "logicalOperator" to "AND",
                    "expressions" to null
                )
            ),
            actions = listOf("IGNORE"),
            active = false,
            draft = true,
            currentVersion = 1,
            createdBy = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)
        val group = domain.expressions[0] as Group
        assertEquals(LogicalOperator.AND, group.logicalOperator)
        assertTrue(group.expressions.isEmpty())
    }

    @Test
    @DisplayName("toDomain: raw non-map expected value uses inferFactValue")
    fun toDomainRawNonMapExpectedValue() {
        val entity = RuleConfigurationEntity(
            id = UUID.randomUUID(),
            ruleId = UUID.randomUUID(),
            expressions = listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "flag",
                    "operator" to "EQUALS",
                    "expectedValue" to true
                )
            ),
            actions = listOf("IGNORE"),
            active = false,
            draft = true,
            currentVersion = 1,
            createdBy = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)
        val condition = domain.expressions[0] as Condition
        assertEquals(FactValue.BooleanValue(true), condition.expectedValue)
    }

    @Test
    @DisplayName("toDomain: null raw expected value returns StringValue")
    fun toDomainNullRawExpectedValue() {
        val entity = RuleConfigurationEntity(
            id = UUID.randomUUID(),
            ruleId = UUID.randomUUID(),
            expressions = listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "flag",
                    "operator" to "EQUALS",
                    "expectedValue" to null
                )
            ),
            actions = listOf("IGNORE"),
            active = false,
            draft = true,
            currentVersion = 1,
            createdBy = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)
        val condition = domain.expressions[0] as Condition
        assertTrue(condition.expectedValue is FactValue.StringValue)
    }

    @Test
    @DisplayName("versionToEntity maps domain to entity correctly")
    fun versionToEntityMapsCorrectly() {
        val configId = UUID.randomUUID()
        val now = Instant.now()
        val domainVersion = ConfigurationVersionEntry(
            version = ConfigurationVersion(3),
            expressions = listOf(
                Condition(FactName("risk"), ComparisonOperator.EQUALS, FactValue.EnumValue("HIGH"))
            ),
            actions = listOf(Action.BLOCK),
            active = true,
            createdBy = "admin",
            createdAt = now
        )

        val entity = mapper.versionToEntity(domainVersion, configId)

        assertEquals(configId, entity.configurationId)
        assertEquals(3, entity.version)
        assertEquals(1, entity.expressions.size)
        assertEquals(listOf("BLOCK"), entity.actions)
        assertEquals(true, entity.active)
        assertEquals("admin", entity.createdBy)
        assertEquals(now, entity.createdAt)
    }

    @Test
    @DisplayName("toDomain: mapToFactValue unknown type with null value uses inferFactValue else")
    fun toDomainMapToFactValueUnknownTypeNullValue() {
        val entity = RuleConfigurationEntity(
            id = UUID.randomUUID(),
            ruleId = UUID.randomUUID(),
            expressions = listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "testFact",
                    "operator" to "EQUALS",
                    "expectedValue" to mapOf("type" to "UNKNOWN", "value" to null)
                )
            ),
            actions = listOf("IGNORE"),
            active = false,
            draft = true,
            currentVersion = 1,
            createdBy = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)
        val condition = domain.expressions[0] as Condition
        // Unknown type → inferFactValue(null) → else → StringValue("null")
        assertTrue(condition.expectedValue is FactValue.StringValue)
    }

    @Test
    @DisplayName("toDomain: inferFactValue with list/object uses else → StringValue")
    fun toDomainInferFactValueListFallback() {
        val entity = RuleConfigurationEntity(
            id = UUID.randomUUID(),
            ruleId = UUID.randomUUID(),
            expressions = listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "testFact",
                    "operator" to "EQUALS",
                    "expectedValue" to listOf("a", "b")
                )
            ),
            actions = listOf("IGNORE"),
            active = false,
            draft = true,
            currentVersion = 1,
            createdBy = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            versions = emptyList()
        )

        val domain = mapper.toDomain(entity)
        val condition = domain.expressions[0] as Condition
        // non-map raw → inferFactValue(List) → else → StringValue
        assertTrue(condition.expectedValue is FactValue.StringValue)
    }
}
