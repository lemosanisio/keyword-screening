package br.com.alert.infrastructure.output.persistence.mapper

import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.model.vo.AlertId
import br.com.alert.infrastructure.output.persistence.entity.AlertEntity
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("AlertMapper")
class AlertMapperTest {

    private val mapper = AlertMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val now = Instant.now()
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val entity = AlertEntity(
            id = id,
            transactionId = "TX-001",
            ruleId = ruleId,
            customerId = "CUST-42",
            facts = mapOf("keywordMatched" to true, "amount" to 1000),
            configurationVersion = 3,
            traceId = "trace-abc-123",
            actions = listOf("GENERATE_ALERT", "REVIEW"),
            explanation = mapOf("step" to "evaluation"),
            status = "OPEN",
            createdAt = now,
            updatedAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(AlertId(id), domain.id)
        assertEquals(TransactionId("TX-001"), domain.transactionId)
        assertEquals(RuleId(ruleId), domain.ruleId)
        assertEquals(CustomerId("CUST-42"), domain.customerId)
        assertEquals(mapOf("keywordMatched" to true, "amount" to 1000), domain.facts)
        assertEquals(3, domain.configurationVersion)
        assertEquals(TraceId("trace-abc-123"), domain.traceId)
        assertEquals(listOf("GENERATE_ALERT", "REVIEW"), domain.actions)
        assertEquals(mapOf("step" to "evaluation"), domain.explanation)
        assertEquals(AlertStatus.OPEN, domain.status)
        assertEquals(now, domain.createdAt)
        assertEquals(now, domain.updatedAt)
    }

    @Test
    @DisplayName("toDomain converts blank traceId to null")
    fun toDomainBlankTraceIdToNull() {
        val entity = AlertEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-002",
            ruleId = UUID.randomUUID(),
            customerId = "CUST-01",
            facts = emptyMap(),
            configurationVersion = 1,
            traceId = "",
            actions = emptyList(),
            explanation = emptyMap(),
            status = "OPEN",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)

        assertNull(domain.traceId)
    }

    @Test
    @DisplayName("toDomain converts whitespace-only traceId to null")
    fun toDomainWhitespaceTraceIdToNull() {
        val entity = AlertEntity(
            id = UUID.randomUUID(),
            transactionId = "TX-003",
            ruleId = UUID.randomUUID(),
            customerId = "CUST-02",
            facts = emptyMap(),
            configurationVersion = 1,
            traceId = "   ",
            actions = emptyList(),
            explanation = emptyMap(),
            status = "UNDER_REVIEW",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)

        assertNull(domain.traceId)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val now = Instant.now()
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val domain = Alert(
            id = AlertId(id),
            transactionId = TransactionId("TX-010"),
            ruleId = RuleId(ruleId),
            customerId = CustomerId("CUST-99"),
            facts = mapOf("risk" to "HIGH"),
            configurationVersion = 2,
            traceId = TraceId("trace-xyz"),
            actions = listOf("BLOCK"),
            explanation = mapOf("reason" to "high risk"),
            status = AlertStatus.UNDER_REVIEW,
            createdAt = now,
            updatedAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(id, entity.id)
        assertEquals("TX-010", entity.transactionId)
        assertEquals(ruleId, entity.ruleId)
        assertEquals("CUST-99", entity.customerId)
        assertEquals(mapOf("risk" to "HIGH"), entity.facts)
        assertEquals(2, entity.configurationVersion)
        assertEquals("trace-xyz", entity.traceId)
        assertEquals(listOf("BLOCK"), entity.actions)
        assertEquals(mapOf("reason" to "high risk"), entity.explanation)
        assertEquals("UNDER_REVIEW", entity.status)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.updatedAt)
    }

    @Test
    @DisplayName("toEntity converts null traceId to empty string")
    fun toEntityNullTraceIdToEmptyString() {
        val domain = Alert(
            id = AlertId(UUID.randomUUID()),
            transactionId = TransactionId("TX-020"),
            ruleId = RuleId(UUID.randomUUID()),
            customerId = CustomerId("CUST-01"),
            facts = null,
            configurationVersion = null,
            traceId = null,
            actions = null,
            explanation = null,
            status = AlertStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val entity = mapper.toEntity(domain)

        assertEquals("", entity.traceId)
    }

    @Test
    @DisplayName("toEntity converts null facts to emptyMap")
    fun toEntityNullFactsToEmptyMap() {
        val domain = Alert(
            id = AlertId(UUID.randomUUID()),
            transactionId = TransactionId("TX-030"),
            ruleId = RuleId(UUID.randomUUID()),
            customerId = CustomerId("CUST-02"),
            facts = null,
            configurationVersion = null,
            traceId = null,
            actions = null,
            explanation = null,
            status = AlertStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val entity = mapper.toEntity(domain)

        assertEquals(emptyMap<String, Any?>(), entity.facts)
        assertEquals(0, entity.configurationVersion)
        assertEquals(emptyList<String>(), entity.actions)
        assertEquals(emptyMap<String, Any?>(), entity.explanation)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves data with all fields populated")
    fun roundTripWithAllFields() {
        val now = Instant.now()
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val domain = Alert(
            id = AlertId(id),
            transactionId = TransactionId("TX-RT"),
            ruleId = RuleId(ruleId),
            customerId = CustomerId("CUST-RT"),
            facts = mapOf("key" to "value"),
            configurationVersion = 5,
            traceId = TraceId("trace-rt"),
            actions = listOf("GENERATE_ALERT"),
            explanation = mapOf("detail" to "explained"),
            status = AlertStatus.CLOSED,
            createdAt = now,
            updatedAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(domain.id, result.id)
        assertEquals(domain.transactionId, result.transactionId)
        assertEquals(domain.ruleId, result.ruleId)
        assertEquals(domain.customerId, result.customerId)
        assertEquals(domain.facts, result.facts)
        assertEquals(domain.configurationVersion, result.configurationVersion)
        assertEquals(domain.traceId, result.traceId)
        assertEquals(domain.actions, result.actions)
        assertEquals(domain.explanation, result.explanation)
        assertEquals(domain.status, result.status)
        assertEquals(domain.createdAt, result.createdAt)
        assertEquals(domain.updatedAt, result.updatedAt)
    }

    @Test
    @DisplayName("maps all AlertStatus values correctly")
    fun mapsAllStatuses() {
        val now = Instant.now()
        AlertStatus.entries.forEach { status ->
            val entity = AlertEntity(
                id = UUID.randomUUID(),
                transactionId = "TX-STATUS",
                ruleId = UUID.randomUUID(),
                customerId = "CUST-STATUS",
                facts = emptyMap(),
                configurationVersion = 1,
                traceId = "trace-s",
                actions = emptyList(),
                explanation = emptyMap(),
                status = status.name,
                createdAt = now,
                updatedAt = now
            )

            val domain = mapper.toDomain(entity)

            assertEquals(status, domain.status)
        }
    }
}
