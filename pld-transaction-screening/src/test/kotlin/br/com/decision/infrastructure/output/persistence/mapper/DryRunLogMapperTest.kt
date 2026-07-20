package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.model.DryRunLogResult
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.infrastructure.output.persistence.entity.DryRunLogEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("DryRunLogMapper")
class DryRunLogMapperTest {

    private val mapper = DryRunLogMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val now = Instant.now()
        val entity = DryRunLogEntity(
            id = id,
            configurationId = configId,
            version = 3,
            facts = mapOf(
                "keywordMatched" to mapOf("type" to "BOOLEAN", "value" to true),
                "amount" to mapOf("type" to "NUMBER", "value" to 1500)
            ),
            result = mapOf(
                "decision" to "ALERT",
                "actions" to listOf("GENERATE_ALERT", "REVIEW")
            ),
            executedBy = "analyst-01",
            createdAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(id, domain.id)
        assertEquals(configId, domain.configurationId)
        assertEquals(ConfigurationVersion(3), domain.version)
        assertEquals(2, domain.facts.size)
        assertEquals(FactValue.BooleanValue(true), domain.facts[FactName("keywordMatched")])
        assertEquals(Decision.ALERT, domain.result.decision)
        assertEquals(listOf(Action.GENERATE_ALERT, Action.REVIEW), domain.result.actions)
        assertEquals("analyst-01", domain.executedBy)
        assertEquals(now, domain.createdAt)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val now = Instant.now()
        val domain = DryRunLog(
            id = id,
            configurationId = configId,
            version = ConfigurationVersion(2),
            facts = mapOf(
                FactName("customerRisk") to FactValue.EnumValue("HIGH"),
                FactName("blocked") to FactValue.BooleanValue(false)
            ),
            result = DryRunLogResult(
                decision = Decision.IGNORE,
                actions = listOf(Action.IGNORE)
            ),
            executedBy = "system",
            createdAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(id, entity.id)
        assertEquals(configId, entity.configurationId)
        assertEquals(2, entity.version)
        assertEquals(2, entity.facts.size)
        assertEquals("system", entity.executedBy)
        assertEquals(now, entity.createdAt)
        // Verify result serialization
        assertEquals("IGNORE", entity.result["decision"])
        assertEquals(listOf("IGNORE"), entity.result["actions"])
    }

    @Test
    @DisplayName("round-trip with BOOLEAN fact type")
    fun roundTripBooleanFact() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val domain = DryRunLog(
            id = id,
            configurationId = configId,
            version = ConfigurationVersion(1),
            facts = mapOf(FactName("flag") to FactValue.BooleanValue(true)),
            result = DryRunLogResult(Decision.ALERT, listOf(Action.GENERATE_ALERT)),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(FactValue.BooleanValue(true), result.facts[FactName("flag")])
    }

    @Test
    @DisplayName("round-trip with ENUM fact type")
    fun roundTripEnumFact() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val domain = DryRunLog(
            id = id,
            configurationId = configId,
            version = ConfigurationVersion(1),
            facts = mapOf(FactName("risk") to FactValue.EnumValue("HIGH")),
            result = DryRunLogResult(Decision.REVIEW, listOf(Action.REVIEW)),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(FactValue.EnumValue("HIGH"), result.facts[FactName("risk")])
    }

    @Test
    @DisplayName("round-trip with NUMBER fact type")
    fun roundTripNumberFact() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val domain = DryRunLog(
            id = id,
            configurationId = configId,
            version = ConfigurationVersion(1),
            facts = mapOf(FactName("amount") to FactValue.NumberValue(BigDecimal("1500.50"))),
            result = DryRunLogResult(Decision.BLOCK, listOf(Action.BLOCK)),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        val factValue = result.facts[FactName("amount")] as FactValue.NumberValue
        assertEquals(0, BigDecimal("1500.50").compareTo(factValue.value))
    }

    @Test
    @DisplayName("round-trip with STRING fact type")
    fun roundTripStringFact() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val domain = DryRunLog(
            id = id,
            configurationId = configId,
            version = ConfigurationVersion(1),
            facts = mapOf(FactName("description") to FactValue.StringValue("payment for services")),
            result = DryRunLogResult(Decision.IGNORE, emptyList()),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(FactValue.StringValue("payment for services"), result.facts[FactName("description")])
    }

    @Test
    @DisplayName("round-trip with MONEY fact type")
    fun roundTripMoneyFact() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val domain = DryRunLog(
            id = id,
            configurationId = configId,
            version = ConfigurationVersion(1),
            facts = mapOf(
                FactName("txAmount") to FactValue.MoneyValue(BigDecimal("9999.99"), "BRL")
            ),
            result = DryRunLogResult(Decision.ALERT, listOf(Action.GENERATE_ALERT)),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        val factValue = result.facts[FactName("txAmount")] as FactValue.MoneyValue
        assertEquals(0, BigDecimal("9999.99").compareTo(factValue.amount))
        assertEquals("BRL", factValue.currency)
    }

    @Test
    @DisplayName("maps all Decision values correctly")
    fun mapsAllDecisions() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        Decision.entries.forEach { decision ->
            val domain = DryRunLog(
                id = id,
                configurationId = configId,
                version = ConfigurationVersion(1),
                facts = emptyMap(),
                result = DryRunLogResult(decision, emptyList()),
                executedBy = "test",
                createdAt = Instant.now()
            )

            val result = mapper.toDomain(mapper.toEntity(domain))

            assertEquals(decision, result.result.decision)
        }
    }

    @Test
    @DisplayName("maps all Action values correctly")
    fun mapsAllActions() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val allActions = Action.entries.toList()
        val domain = DryRunLog(
            id = id,
            configurationId = configId,
            version = ConfigurationVersion(1),
            facts = emptyMap(),
            result = DryRunLogResult(Decision.ALERT, allActions),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(allActions, result.result.actions)
    }

    @Test
    @DisplayName("handles empty facts and actions")
    fun handlesEmptyFactsAndActions() {
        val id = UUID.randomUUID()
        val configId = UUID.randomUUID()
        val domain = DryRunLog(
            id = id,
            configurationId = configId,
            version = ConfigurationVersion(1),
            facts = emptyMap(),
            result = DryRunLogResult(Decision.IGNORE, emptyList()),
            executedBy = "analyst",
            createdAt = Instant.now()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertTrue(result.facts.isEmpty())
        assertTrue(result.result.actions.isEmpty())
    }

    @Test
    @DisplayName("toDomain: maps fact with unknown type using inferFactValue")
    fun toDomainMapsFactWithUnknownType() {
        val entity = DryRunLogEntity(
            id = UUID.randomUUID(),
            configurationId = UUID.randomUUID(),
            version = 1,
            facts = mapOf(
                "customFact" to mapOf("type" to "UNKNOWN_TYPE", "value" to "someVal")
            ),
            result = mapOf("decision" to "IGNORE", "actions" to emptyList<String>()),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        val factValue = domain.facts[FactName("customFact")]
        assertTrue(factValue is FactValue.StringValue)
    }

    @Test
    @DisplayName("toDomain: maps raw non-map fact using inferFactValue")
    fun toDomainMapsRawNonMapFact() {
        val entity = DryRunLogEntity(
            id = UUID.randomUUID(),
            configurationId = UUID.randomUUID(),
            version = 1,
            facts = mapOf(
                "boolRaw" to true,
                "numRaw" to 99,
                "strRaw" to "test"
            ),
            result = mapOf("decision" to "IGNORE", "actions" to emptyList<String>()),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        assertEquals(FactValue.BooleanValue(true), domain.facts[FactName("boolRaw")])
        assertTrue(domain.facts[FactName("numRaw")] is FactValue.NumberValue)
        assertEquals(FactValue.StringValue("test"), domain.facts[FactName("strRaw")])
    }

    @Test
    @DisplayName("toDomain: maps null raw fact value to StringValue")
    fun toDomainMapsNullFact() {
        val entity = DryRunLogEntity(
            id = UUID.randomUUID(),
            configurationId = UUID.randomUUID(),
            version = 1,
            facts = mapOf("nullFact" to null),
            result = mapOf("decision" to "IGNORE", "actions" to emptyList<String>()),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        val factValue = domain.facts[FactName("nullFact")]
        assertTrue(factValue is FactValue.StringValue)
    }

    @Test
    @DisplayName("toDomain: result with null decision defaults to IGNORE")
    fun toDomainResultNullDecisionDefaultsToIgnore() {
        val entity = DryRunLogEntity(
            id = UUID.randomUUID(),
            configurationId = UUID.randomUUID(),
            version = 1,
            facts = emptyMap(),
            result = mapOf("actions" to listOf("GENERATE_ALERT")),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        assertEquals(Decision.IGNORE, domain.result.decision)
    }

    @Test
    @DisplayName("toDomain: result with null actions defaults to empty list")
    fun toDomainResultNullActionsDefaultsToEmptyList() {
        val entity = DryRunLogEntity(
            id = UUID.randomUUID(),
            configurationId = UUID.randomUUID(),
            version = 1,
            facts = emptyMap(),
            result = mapOf("decision" to "ALERT"),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        assertEquals(Decision.ALERT, domain.result.decision)
        assertTrue(domain.result.actions.isEmpty())
    }

    @Test
    @DisplayName("toDomain: inferFactValue with List type maps to StringValue (else branch)")
    fun toDomainInferFactValueListType() {
        val entity = DryRunLogEntity(
            id = UUID.randomUUID(),
            configurationId = UUID.randomUUID(),
            version = 1,
            facts = mapOf(
                "listFact" to listOf("x", "y")
            ),
            result = mapOf("decision" to "IGNORE", "actions" to emptyList<String>()),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        val factValue = domain.facts[FactName("listFact")]
        assertTrue(factValue is FactValue.StringValue)
    }

    @Test
    @DisplayName("toDomain: result with non-list actions casts correctly")
    fun toDomainResultWithNonListActions() {
        val entity = DryRunLogEntity(
            id = UUID.randomUUID(),
            configurationId = UUID.randomUUID(),
            version = 1,
            facts = emptyMap(),
            result = mapOf("decision" to "ALERT", "actions" to listOf("GENERATE_ALERT")),
            executedBy = "test",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        assertEquals(Decision.ALERT, domain.result.decision)
        assertEquals(listOf(Action.GENERATE_ALERT), domain.result.actions)
    }
}
