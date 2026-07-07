package br.com.alert.application.service

import br.com.alert.domain.model.Alert
import br.com.alert.domain.port.AlertRepository
import br.com.alert.domain.model.vo.AlertId
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.UUID

class AlertQueryServiceTest {

    private val alertRepository = mockk<AlertRepository>()
    private val alertQueryService = AlertQueryService(alertRepository)

    @Test
    @DisplayName("findByTransactionId delegates to repository")
    fun `findByTransactionId delegates to repository`() {
        every { alertRepository.findByTransactionId(TransactionId("TX-001")) } returns emptyList()

        val result = alertQueryService.findByTransactionId(TransactionId("TX-001"))

        assertEquals(emptyList<Alert>(), result)
        verify { alertRepository.findByTransactionId(TransactionId("TX-001")) }
    }

    @Test
    @DisplayName("findByRuleId delegates to repository with correct pagination")
    fun `findByRuleId delegates to repository with correct pagination`() {
        val ruleId = RuleId(UUID.randomUUID())
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<Alert> = PageImpl(emptyList())
        every { alertRepository.findByRuleId(any(), capture(pageableSlot)) } returns emptyPage

        val result = alertQueryService.findByRuleId(ruleId, 1, 50)

        assertEquals(emptyPage, result)
        assertEquals(1, pageableSlot.captured.pageNumber)
        assertEquals(50, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("findById delegates to repository")
    fun `findById delegates to repository`() {
        val alertId = AlertId(UUID.randomUUID())
        every { alertRepository.findById(alertId) } returns null

        val result = alertQueryService.findById(alertId)

        assertNull(result)
        verify { alertRepository.findById(alertId) }
    }

    // --- Pagination clamping tests ---

    @Test
    @DisplayName("size <= 0 defaults to 20")
    fun `size lte 0 defaults to 20`() {
        val ruleId = RuleId(UUID.randomUUID())
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<Alert> = PageImpl(emptyList())
        every { alertRepository.findByRuleId(any(), capture(pageableSlot)) } returns emptyPage

        alertQueryService.findByRuleId(ruleId, 0, 0)

        assertEquals(20, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("negative size defaults to 20")
    fun `negative size defaults to 20`() {
        val ruleId = RuleId(UUID.randomUUID())
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<Alert> = PageImpl(emptyList())
        every { alertRepository.findByRuleId(any(), capture(pageableSlot)) } returns emptyPage

        alertQueryService.findByRuleId(ruleId, 0, -10)

        assertEquals(20, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("size > 100 is clamped to 100")
    fun `size gt 100 is clamped to 100`() {
        val ruleId = RuleId(UUID.randomUUID())
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<Alert> = PageImpl(emptyList())
        every { alertRepository.findByRuleId(any(), capture(pageableSlot)) } returns emptyPage

        alertQueryService.findByRuleId(ruleId, 0, 500)

        assertEquals(100, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("size exactly 100 is kept as-is")
    fun `size exactly 100 is kept as-is`() {
        val ruleId = RuleId(UUID.randomUUID())
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<Alert> = PageImpl(emptyList())
        every { alertRepository.findByRuleId(any(), capture(pageableSlot)) } returns emptyPage

        alertQueryService.findByRuleId(ruleId, 0, 100)

        assertEquals(100, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("size 1 is kept as-is (minimum valid)")
    fun `size 1 is kept as-is (minimum valid)`() {
        val ruleId = RuleId(UUID.randomUUID())
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<Alert> = PageImpl(emptyList())
        every { alertRepository.findByRuleId(any(), capture(pageableSlot)) } returns emptyPage

        alertQueryService.findByRuleId(ruleId, 0, 1)

        assertEquals(1, pageableSlot.captured.pageSize)
    }
}
