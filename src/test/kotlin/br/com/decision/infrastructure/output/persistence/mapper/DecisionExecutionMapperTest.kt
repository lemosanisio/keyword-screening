package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.*
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.DecisionExecutionEntity
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("DecisionExecutionMapper")
class DecisionExecutionMapperTest {

    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }
    private val mapper = DecisionExecutionMapper(objectMapper)

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val now = Instant.now()
        val entity = DecisionExecutionEntity(
            id = id,
            transactionId = "TX-001",
            ruleId = ruleId,
            configurationVersion = 2,
            facts = mapOf(
                "keywordMatched" to mapOf("type" to "BOOLEAN", "value" to true),
                "amount" to mapOf("type" to "NUMBER", "value" to 5000)
            ),
            decision = "ALERT",
            actions = listOf("GENERATE_ALERT"),
            matchedExpressions = listOf(
                mapOf(
                    "factName" to "keywordMatched",
                    "operator" to "EQUALS",
                    "expectedValue" to mapOf("type" to "BOOLEAN", "value" to true),
                    "actualValue" to mapOf("type" to "BOOLEAN", "value" to true),
                    "satisfied" to true,
                    "justification" to "Keyword was matched"
                )
            ),
            failedExpressions = emptyList(),
            explanation = mapOf("traceId" to "trace-123"),
            executionTimeMs = 45,
            traceId = "trace-123",
            createdAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(id, domain.id)
        assertEquals(TransactionId("TX-001"), domain.transactionId)
        assertEquals(RuleId(ruleId), domain.ruleId)
        assertEquals(ConfigurationVersion(2), domain.configurationVersion)
        assertEquals(2, domain.facts.size)
        assertEquals(FactValue.BooleanValue(true), domain.facts[FactName("keywordMatched")])
        assertEquals(Decision.ALERT, domain.result.decision)
        assertEquals(listOf(Action.GENERATE_ALERT), domain.result.actions)
        assertEquals(1, domain.result.matchedExpressions.size)
        assertTrue(domain.result.failedExpressions.isEmpty())
        assertEquals(45L, domain.executionTimeMs)
        assertEquals(TraceId("trace-123"), domain.traceId)
        assertEquals(now, domain.timestamp)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val now = Instant.now()
        val domain = DecisionExecution(
            id = id,
            transactionId = TransactionId("TX-002"),
            ruleId = RuleId(ruleId),
            configurationVersion = ConfigurationVersion(1),
            facts = mapOf(
                FactName("risk") to FactValue.EnumValue("HIGH")
            ),
            result = DecisionResult(
                decision = Decision.BLOCK,
                actions = listOf(Action.BLOCK),
                matchedExpressions = listOf(
                    ExpressionEvaluation(
                        factName = FactName("risk"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.EnumValue("HIGH"),
                        actualValue = FactValue.EnumValue("HIGH"),
                        satisfied = true,
                        justification = "Risk matches"
                    )
                ),
                failedExpressions = emptyList(),
                executionTimeMs = 30,
                configurationVersion = ConfigurationVersion(1),
                facts = mapOf(FactName("risk") to FactValue.EnumValue("HIGH"))
            ),
            explanation = DecisionExplanation(
                traceId = TraceId("trace-456"),
                steps = emptyList()
            ),
            executionTimeMs = 30,
            traceId = TraceId("trace-456"),
            timestamp = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(id, entity.id)
        assertEquals("TX-002", entity.transactionId)
        assertEquals(ruleId, entity.ruleId)
        assertEquals(1, entity.configurationVersion)
        assertEquals("BLOCK", entity.decision)
        assertEquals(listOf("BLOCK"), entity.actions)
        assertEquals(1, entity.matchedExpressions.size)
        assertTrue(entity.failedExpressions.isEmpty())
        assertEquals(30L, entity.executionTimeMs)
        assertEquals("trace-456", entity.traceId)
        assertEquals(now, entity.createdAt)
    }

    @Test
    @DisplayName("toDomain maps all FactValue types in facts")
    fun toDomainMapsAllFactValueTypes() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-003",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = mapOf(
                "boolFact" to mapOf("type" to "BOOLEAN", "value" to false),
                "enumFact" to mapOf("type" to "ENUM", "value" to "LOW"),
                "numFact" to mapOf("type" to "NUMBER", "value" to 42.5),
                "strFact" to mapOf("type" to "STRING", "value" to "hello"),
                "moneyFact" to mapOf("type" to "MONEY", "amount" to 100, "currency" to "USD")
            ),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            explanation = mapOf("traceId" to "t"),
            executionTimeMs = 10,
            traceId = "t",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)

        assertEquals(FactValue.BooleanValue(false), domain.facts[FactName("boolFact")])
        assertEquals(FactValue.EnumValue("LOW"), domain.facts[FactName("enumFact")])
        assertTrue(domain.facts[FactName("numFact")] is FactValue.NumberValue)
        assertEquals(FactValue.StringValue("hello"), domain.facts[FactName("strFact")])
        val money = domain.facts[FactName("moneyFact")] as FactValue.MoneyValue
        assertEquals("USD", money.currency)
    }

    @Test
    @DisplayName("toDomain handles null traceId in entity")
    fun toDomainHandlesNullTraceId() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-004",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = emptyMap(),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            explanation = mapOf("traceId" to "unknown"),
            executionTimeMs = 5,
            traceId = null,
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)

        assertEquals(TraceId("unknown"), domain.traceId)
    }

    @Test
    @DisplayName("maps all Decision values correctly")
    fun mapsAllDecisions() {
        Decision.entries.forEach { decision ->
            val entity = DecisionExecutionEntity(
                id = UUID.randomUUID(),
                transactionId = "TX-DEC",
                ruleId = UUID.randomUUID(),
                configurationVersion = 1,
                facts = emptyMap(),
                decision = decision.name,
                actions = emptyList(),
                matchedExpressions = emptyList(),
                failedExpressions = emptyList(),
                explanation = mapOf("traceId" to "trace"),
                executionTimeMs = 1,
                traceId = "trace",
                createdAt = Instant.now()
            )

            val domain = mapper.toDomain(entity)

            assertEquals(decision, domain.result.decision)
        }
    }

    @Test
    @DisplayName("toDomain maps failed expressions correctly")
    fun toDomainMapsFailedExpressions() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-FAIL",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = emptyMap(),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = listOf(
                mapOf(
                    "factName" to "amount",
                    "operator" to "GREATER_THAN",
                    "expectedValue" to mapOf("type" to "NUMBER", "value" to 10000),
                    "actualValue" to mapOf("type" to "NUMBER", "value" to 500),
                    "satisfied" to false,
                    "justification" to "Amount below threshold"
                )
            ),
            explanation = mapOf("traceId" to "t"),
            executionTimeMs = 10,
            traceId = "t",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)

        assertEquals(1, domain.result.failedExpressions.size)
        val failed = domain.result.failedExpressions[0]
        assertEquals(FactName("amount"), failed.factName)
        assertEquals(ComparisonOperator.GREATER_THAN, failed.operator)
        assertFalse(failed.satisfied)
        assertEquals("Amount below threshold", failed.justification)
    }

    @Test
    @DisplayName("toDomain: maps fact with unknown type using inferFactValue")
    fun toDomainMapsFactWithUnknownType() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-UNK",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = mapOf(
                "unknownFact" to mapOf("type" to "CUSTOM_TYPE", "value" to "someValue")
            ),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            explanation = mapOf("traceId" to "t"),
            executionTimeMs = 5,
            traceId = "t",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        // unknown type triggers else branch → inferFactValue(raw["value"]) which is a String
        val factValue = domain.facts[FactName("unknownFact")]
        assertTrue(factValue is FactValue.StringValue)
    }

    @Test
    @DisplayName("toDomain: maps raw non-map fact using inferFactValue")
    fun toDomainMapsRawNonMapFact() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-RAW",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = mapOf(
                "boolRaw" to true,
                "numRaw" to 42,
                "strRaw" to "hello"
            ),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            explanation = mapOf("traceId" to "t"),
            executionTimeMs = 5,
            traceId = "t",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)

        assertEquals(FactValue.BooleanValue(true), domain.facts[FactName("boolRaw")])
        assertTrue(domain.facts[FactName("numRaw")] is FactValue.NumberValue)
        assertEquals(FactValue.StringValue("hello"), domain.facts[FactName("strRaw")])
    }

    @Test
    @DisplayName("toDomain: maps null raw value using inferFactValue else branch")
    fun toDomainMapsNullRawValue() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-NULL",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = mapOf(
                "nullFact" to null
            ),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            explanation = mapOf("traceId" to "t"),
            executionTimeMs = 5,
            traceId = "t",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        // null → inferFactValue(null) → else branch → FactValue.StringValue("null")
        val factValue = domain.facts[FactName("nullFact")]
        assertTrue(factValue is FactValue.StringValue)
    }

    @Test
    @DisplayName("toDomain: evaluation with null actualValue maps correctly")
    fun toDomainEvaluationWithNullActualValue() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-NULLAV",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = emptyMap(),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = listOf(
                mapOf(
                    "factName" to "missing",
                    "operator" to "EQUALS",
                    "expectedValue" to mapOf("type" to "BOOLEAN", "value" to true),
                    "actualValue" to null,
                    "satisfied" to false,
                    "justification" to "Fact ausente"
                )
            ),
            explanation = mapOf("traceId" to "t"),
            executionTimeMs = 5,
            traceId = "t",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        val failed = domain.result.failedExpressions[0]
        assertNull(failed.actualValue)
        assertFalse(failed.satisfied)
    }

    @Test
    @DisplayName("toDomain: explanation with null traceId defaults to 'unknown'")
    fun toDomainExplanationNullTraceId() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-EXPL",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = emptyMap(),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            explanation = mapOf("traceId" to null),
            executionTimeMs = 5,
            traceId = "t",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        assertEquals(br.com.shared.domain.valueobject.TraceId("unknown"), domain.explanation.traceId)
    }

    @Test
    @DisplayName("toEntity: MoneyValue fact serializes to map with type, amount, currency")
    fun toEntitySerializesMoneyValue() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val now = Instant.now()
        val domain = DecisionExecution(
            id = id,
            transactionId = TransactionId("TX-MONEY"),
            ruleId = RuleId(ruleId),
            configurationVersion = ConfigurationVersion(1),
            facts = mapOf(
                FactName("txAmount") to FactValue.MoneyValue(BigDecimal("9999.99"), "BRL")
            ),
            result = DecisionResult(
                decision = Decision.IGNORE,
                actions = emptyList(),
                matchedExpressions = emptyList(),
                failedExpressions = emptyList(),
                executionTimeMs = 1,
                configurationVersion = ConfigurationVersion(1),
                facts = mapOf(FactName("txAmount") to FactValue.MoneyValue(BigDecimal("9999.99"), "BRL"))
            ),
            explanation = DecisionExplanation(
                traceId = TraceId("t"),
                steps = emptyList()
            ),
            executionTimeMs = 1,
            traceId = TraceId("t"),
            timestamp = now
        )

        val entity = mapper.toEntity(domain)
        val factMap = entity.facts["txAmount"] as Map<*, *>
        assertEquals("MONEY", factMap["type"])
        assertEquals(BigDecimal("9999.99"), factMap["amount"])
        assertEquals("BRL", factMap["currency"])
    }

    @Test
    @DisplayName("toDomain: inferFactValue with List/unrecognized type maps to StringValue")
    fun toDomainInferFactValueWithListType() {
        val entity = DecisionExecutionEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-LIST",
            ruleId = UUID.randomUUID(),
            configurationVersion = 1,
            facts = mapOf(
                "listFact" to listOf("a", "b")
            ),
            decision = "IGNORE",
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            explanation = mapOf("traceId" to "t"),
            executionTimeMs = 5,
            traceId = "t",
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)
        val factValue = domain.facts[FactName("listFact")]
        assertTrue(factValue is FactValue.StringValue)
    }
}
