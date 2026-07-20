package br.com.decision.domain.resolver

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.CustomerId

class ScreeningResolverTest {

    private val resolver = ScreeningResolver()

    @Test
    fun `producedFacts contains only keywordMatched`() {
        assertEquals(setOf(FactName("keywordMatched")), resolver.producedFacts)
    }

    @Test
    fun `entity is Screening`() {
        assertEquals("Screening", resolver.entity)
    }

    @Test
    fun `resolve returns keywordMatched true when detection matched`() {
        val event = detectionEvent(matched = true)

        val facts = resolver.resolve(event)

        assertEquals(1, facts.size)
        val fact = facts.first()
        assertEquals(FactName("keywordMatched"), fact.name)
        assertEquals(FactValue.BooleanValue(true), fact.value)
        assertEquals("Screening", fact.entity)
    }

    @Test
    fun `resolve returns keywordMatched false when detection did not match`() {
        val event = detectionEvent(matched = false)

        val facts = resolver.resolve(event)

        assertEquals(1, facts.size)
        val fact = facts.first()
        assertEquals(FactName("keywordMatched"), fact.name)
        assertEquals(FactValue.BooleanValue(false), fact.value)
        assertEquals("Screening", fact.entity)
    }

    @Test
    fun `resolve sets resolvedAt to current time`() {
        val before = Instant.now()
        val event = detectionEvent(matched = true)

        val facts = resolver.resolve(event)

        val after = Instant.now()
        val resolvedAt = facts.first().resolvedAt
        assertTrue(resolvedAt >= before && resolvedAt <= after)
    }

    private fun detectionEvent(matched: Boolean) = DetectionEvent(
        eventId = EventId("evt-001"),
        traceId = TraceId("trace-001"),
        timestamp = Instant.now(), transactionId = TransactionId("TX-001"), customerId = CustomerId("CUST-42"), ruleCode = RuleCode("KEYWORD_SCREENING"),
        detectionResult = DetectionResult(
            matched = matched,
            matches = if (matched) listOf(DetectionMatch("lavagem", "AML")) else emptyList()
        )
    )
}
