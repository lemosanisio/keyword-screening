package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.vo.FactName
import br.com.decision.infrastructure.output.persistence.entity.FactDefinitionEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("FactDefinitionMapper")
class FactDefinitionMapperTest {

    private val mapper = FactDefinitionMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val id = UUID.randomUUID()
        val entity = FactDefinitionEntity(
            id = id,
            name = "transactionAmount",
            displayName = "Valor da Transação",
            entity = "transaction",
            type = "NUMBER",
            context = "TRANSACTION",
            source = "core-banking",
            supportedOperators = listOf("GREATER_THAN", "LESS_THAN", "EQUALS"),
            enabled = true
        )

        val domain = mapper.toDomain(entity)

        assertEquals(id, domain.id)
        assertEquals(FactName("transactionAmount"), domain.name)
        assertEquals("Valor da Transação", domain.displayName)
        assertEquals("transaction", domain.entity)
        assertEquals(FactType.NUMBER, domain.type)
        assertEquals(RuleContext.TRANSACTION, domain.context)
        assertEquals("core-banking", domain.source)
        assertEquals(
            listOf(ComparisonOperator.GREATER_THAN, ComparisonOperator.LESS_THAN, ComparisonOperator.EQUALS),
            domain.supportedOperators
        )
        assertTrue(domain.enabled)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val id = UUID.randomUUID()
        val domain = FactDefinition(
            id = id,
            name = FactName("customerRisk"),
            displayName = "Risco do Cliente",
            entity = "customer",
            type = FactType.ENUM,
            context = RuleContext.CUSTOMER,
            source = "risk-engine",
            supportedOperators = listOf(ComparisonOperator.EQUALS, ComparisonOperator.NOT_EQUALS),
            enabled = false
        )

        val entity = mapper.toEntity(domain)

        assertEquals(id, entity.id)
        assertEquals("customerRisk", entity.name)
        assertEquals("Risco do Cliente", entity.displayName)
        assertEquals("customer", entity.entity)
        assertEquals("ENUM", entity.type)
        assertEquals("CUSTOMER", entity.context)
        assertEquals("risk-engine", entity.source)
        assertEquals(listOf("EQUALS", "NOT_EQUALS"), entity.supportedOperators)
        assertFalse(entity.enabled)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves all data")
    fun roundTripPreservesData() {
        val id = UUID.randomUUID()
        val original = FactDefinition(
            id = id,
            name = FactName("keywordMatched"),
            displayName = "Keyword Matched",
            entity = "screening",
            type = FactType.BOOLEAN,
            context = RuleContext.SCREENING,
            source = "keyword-screening",
            supportedOperators = listOf(ComparisonOperator.EQUALS),
            enabled = true
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
    }

    @Test
    @DisplayName("maps all FactType values correctly")
    fun mapsAllFactTypes() {
        val id = UUID.randomUUID()
        FactType.entries.forEach { factType ->
            val domain = FactDefinition(
                id = id,
                name = FactName("test"),
                displayName = "Test",
                entity = "test",
                type = factType,
                context = RuleContext.SCREENING,
                source = "test",
                supportedOperators = listOf(ComparisonOperator.EQUALS),
                enabled = true
            )

            val result = mapper.toDomain(mapper.toEntity(domain))

            assertEquals(factType, result.type)
        }
    }

    @Test
    @DisplayName("maps all RuleContext values correctly")
    fun mapsAllRuleContexts() {
        val id = UUID.randomUUID()
        RuleContext.entries.forEach { context ->
            val domain = FactDefinition(
                id = id,
                name = FactName("test"),
                displayName = "Test",
                entity = "test",
                type = FactType.STRING,
                context = context,
                source = "test",
                supportedOperators = emptyList(),
                enabled = true
            )

            val result = mapper.toDomain(mapper.toEntity(domain))

            assertEquals(context, result.context)
        }
    }

    @Test
    @DisplayName("maps all ComparisonOperator values correctly")
    fun mapsAllOperators() {
        val id = UUID.randomUUID()
        val allOperators = ComparisonOperator.entries.toList()
        val domain = FactDefinition(
            id = id,
            name = FactName("allOps"),
            displayName = "All Operators",
            entity = "test",
            type = FactType.NUMBER,
            context = RuleContext.TRANSACTION,
            source = "test",
            supportedOperators = allOperators,
            enabled = true
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(allOperators, result.supportedOperators)
    }
}
