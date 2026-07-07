package br.com.decision.domain.resolver

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.port.CustomerRiskPort
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.CustomerId

class CustomerResolverTest {

    private val customerRiskPort: CustomerRiskPort = mockk()
    private val resolver = CustomerResolver(customerRiskPort)

    @Test
    fun `producedFacts contains only customerRisk`() {
        assertEquals(setOf(FactName("customerRisk")), resolver.producedFacts)
    }

    @Test
    fun `entity is Risk`() {
        assertEquals("Risk", resolver.entity)
    }

    @Test
    fun `resolve returns customerRisk AR when port returns AR`() {
        every { customerRiskPort.getCustomerRisk(CustomerId("CUST-42")) } returns CustomerRisk.AR
        val event = detectionEvent()

        val facts = resolver.resolve(event)

        assertEquals(1, facts.size)
        val fact = facts.first()
        assertEquals(FactName("customerRisk"), fact.name)
        assertEquals(FactValue.EnumValue("AR"), fact.value)
        assertEquals("Risk", fact.entity)
    }

    @Test
    fun `resolve returns customerRisk MR when port returns MR`() {
        every { customerRiskPort.getCustomerRisk(CustomerId("CUST-42")) } returns CustomerRisk.MR
        val event = detectionEvent()

        val facts = resolver.resolve(event)

        assertEquals(1, facts.size)
        assertEquals(FactValue.EnumValue("MR"), facts.first().value)
    }

    @Test
    fun `resolve returns customerRisk BR when port returns BR`() {
        every { customerRiskPort.getCustomerRisk(CustomerId("CUST-42")) } returns CustomerRisk.BR
        val event = detectionEvent()

        val facts = resolver.resolve(event)

        assertEquals(1, facts.size)
        assertEquals(FactValue.EnumValue("BR"), facts.first().value)
    }

    @Test
    fun `resolve returns emptyList when port returns null`() {
        every { customerRiskPort.getCustomerRisk(CustomerId("CUST-42")) } returns null
        val event = detectionEvent()

        val facts = resolver.resolve(event)

        assertTrue(facts.isEmpty())
    }

    @Test
    fun `resolve sets resolvedAt to current time`() {
        every { customerRiskPort.getCustomerRisk(CustomerId("CUST-42")) } returns CustomerRisk.AR
        val before = Instant.now()
        val event = detectionEvent()

        val facts = resolver.resolve(event)

        val after = Instant.now()
        val resolvedAt = facts.first().resolvedAt
        assertTrue(resolvedAt >= before && resolvedAt <= after)
    }

    private fun detectionEvent() = DetectionEvent(
        eventId = EventId("evt-001"),
        traceId = TraceId("trace-001"),
        timestamp = Instant.now(), transactionId = TransactionId("TX-001"), customerId = CustomerId("CUST-42"), ruleCode = RuleCode("KEYWORD_SCREENING"),
        detectionResult = DetectionResult(
            matched = true,
            matches = listOf(DetectionMatch("lavagem", "AML"))
        )
    )
}
