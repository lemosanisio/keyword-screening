package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.RuleDefinitionEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("RuleDefinitionMapper")
class RuleDefinitionMapperTest {

    private val mapper = RuleDefinitionMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val entity = RuleDefinitionEntity(
            id = id,
            code = "KEYWORD_ALERT",
            name = "Keyword Alert Rule",
            description = "Generates alert when keyword is matched",
            context = "SCREENING",
            category = "KEYWORD_SCREENING",
            supportedFacts = listOf("keywordMatched", "customerRisk"),
            supportedActions = listOf("GENERATE_ALERT", "REVIEW"),
            status = "ACTIVE",
            createdAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(RuleId(id), domain.id)
        assertEquals(RuleCode("KEYWORD_ALERT"), domain.code)
        assertEquals("Keyword Alert Rule", domain.name)
        assertEquals("Generates alert when keyword is matched", domain.description)
        assertEquals(RuleContext.SCREENING, domain.context)
        assertEquals(RuleCategory.KEYWORD_SCREENING, domain.category)
        assertEquals(listOf(FactName("keywordMatched"), FactName("customerRisk")), domain.supportedFacts)
        assertEquals(listOf(Action.GENERATE_ALERT, Action.REVIEW), domain.supportedActions)
        assertEquals(RuleStatus.ACTIVE, domain.status)
        assertEquals(now, domain.createdAt)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val domain = RuleDefinition(
            id = RuleId(id),
            code = RuleCode("SANCTIONS_CHECK"),
            name = "Sanctions Check",
            description = "Check sanctions list",
            context = RuleContext.CUSTOMER,
            category = RuleCategory.SANCTIONS,
            supportedFacts = listOf(FactName("sanctionsHit")),
            supportedActions = listOf(Action.BLOCK),
            status = RuleStatus.INACTIVE,
            createdAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(id, entity.id)
        assertEquals("SANCTIONS_CHECK", entity.code)
        assertEquals("Sanctions Check", entity.name)
        assertEquals("Check sanctions list", entity.description)
        assertEquals("CUSTOMER", entity.context)
        assertEquals("SANCTIONS", entity.category)
        assertEquals(listOf("sanctionsHit"), entity.supportedFacts)
        assertEquals(listOf("BLOCK"), entity.supportedActions)
        assertEquals("INACTIVE", entity.status)
        assertEquals(now, entity.createdAt)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves all data")
    fun roundTripPreservesData() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val original = RuleDefinition(
            id = RuleId(id),
            code = RuleCode("AML_VELOCITY"),
            name = "AML Velocity",
            description = "Detects high-frequency transactions",
            context = RuleContext.TRANSACTION,
            category = RuleCategory.AML,
            supportedFacts = listOf(FactName("txCount24h"), FactName("totalAmount24h")),
            supportedActions = listOf(Action.GENERATE_ALERT, Action.REVIEW),
            status = RuleStatus.ACTIVE,
            createdAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
    }

    @Test
    @DisplayName("maps all RuleStatus values correctly")
    fun mapsAllRuleStatuses() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        RuleStatus.entries.forEach { status ->
            val domain = RuleDefinition(
                id = RuleId(id),
                code = RuleCode("CODE"),
                name = "name",
                description = "desc",
                context = RuleContext.SCREENING,
                category = RuleCategory.FRAUD,
                supportedFacts = emptyList(),
                supportedActions = emptyList(),
                status = status,
                createdAt = now
            )

            val result = mapper.toDomain(mapper.toEntity(domain))

            assertEquals(status, result.status)
        }
    }

    @Test
    @DisplayName("maps all RuleCategory values correctly")
    fun mapsAllRuleCategories() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        RuleCategory.entries.forEach { category ->
            val domain = RuleDefinition(
                id = RuleId(id),
                code = RuleCode("CODE"),
                name = "name",
                description = "desc",
                context = RuleContext.SCREENING,
                category = category,
                supportedFacts = emptyList(),
                supportedActions = emptyList(),
                status = RuleStatus.ACTIVE,
                createdAt = now
            )

            val result = mapper.toDomain(mapper.toEntity(domain))

            assertEquals(category, result.category)
        }
    }

    @Test
    @DisplayName("maps all Action values correctly")
    fun mapsAllActions() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val allActions = Action.entries.toList()
        val domain = RuleDefinition(
            id = RuleId(id),
            code = RuleCode("ALL_ACTIONS"),
            name = "All Actions",
            description = "Supports all actions",
            context = RuleContext.SCREENING,
            category = RuleCategory.FRAUD,
            supportedFacts = emptyList(),
            supportedActions = allActions,
            status = RuleStatus.ACTIVE,
            createdAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(allActions, result.supportedActions)
    }
}
