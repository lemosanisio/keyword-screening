package br.com.decision.application.service

import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.port.DecisionExecutionRepository
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TraceId
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
import org.springframework.data.domain.Sort
import java.util.UUID

class DecisionQueryServiceTest {

    private val repository = mockk<DecisionExecutionRepository>()
    private val service = DecisionQueryService(repository)

    @Test
    @DisplayName("findByTransactionId delegates to repository with descending timestamp sort")
    fun `findByTransactionId delegates to repository with descending timestamp sort`() {
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<DecisionExecution> = PageImpl(emptyList())
        every { repository.findByTransactionId(TransactionId("TX-001"), capture(pageableSlot)) } returns emptyPage

        val result = service.findByTransactionId(TransactionId("TX-001"), 0, 20)

        assertEquals(emptyPage, result)
        assertEquals(0, pageableSlot.captured.pageNumber)
        assertEquals(20, pageableSlot.captured.pageSize)
        assertEquals(Sort.Direction.DESC, pageableSlot.captured.sort.getOrderFor("timestamp")?.direction)
    }

    @Test
    @DisplayName("findByRuleId delegates to repository with correct pagination")
    fun `findByRuleId delegates to repository with correct pagination`() {
        val ruleId = RuleId(UUID.randomUUID())
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<DecisionExecution> = PageImpl(emptyList())
        every { repository.findByRuleId(ruleId, capture(pageableSlot)) } returns emptyPage

        val result = service.findByRuleId(ruleId, 1, 50)

        assertEquals(emptyPage, result)
        assertEquals(1, pageableSlot.captured.pageNumber)
        assertEquals(50, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("findByDecision delegates to repository with correct pagination")
    fun `findByDecision delegates to repository with correct pagination`() {
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<DecisionExecution> = PageImpl(emptyList())
        every { repository.findByDecision(Decision.ALERT, capture(pageableSlot)) } returns emptyPage

        val result = service.findByDecision(Decision.ALERT, 0, 30)

        assertEquals(emptyPage, result)
        assertEquals(0, pageableSlot.captured.pageNumber)
        assertEquals(30, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("findByTraceId delegates to repository")
    fun `findByTraceId delegates to repository`() {
        every { repository.findByTraceId(TraceId("trace-123")) } returns null

        val result = service.findByTraceId(TraceId("trace-123"))

        assertNull(result)
        verify { repository.findByTraceId(TraceId("trace-123")) }
    }

    @Test
    @DisplayName("findById delegates to repository")
    fun `findById delegates to repository`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns null

        val result = service.findById(id)

        assertNull(result)
        verify { repository.findById(id) }
    }

    // --- Pagination clamping tests ---

    @Test
    @DisplayName("size <= 0 defaults to 20")
    fun `size lte 0 defaults to 20`() {
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<DecisionExecution> = PageImpl(emptyList())
        every { repository.findByTransactionId(TransactionId("TX-001"), capture(pageableSlot)) } returns emptyPage

        service.findByTransactionId(TransactionId("TX-001"), 0, 0)

        assertEquals(20, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("negative size defaults to 20")
    fun `negative size defaults to 20`() {
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<DecisionExecution> = PageImpl(emptyList())
        every { repository.findByTransactionId(TransactionId("TX-001"), capture(pageableSlot)) } returns emptyPage

        service.findByTransactionId(TransactionId("TX-001"), 0, -5)

        assertEquals(20, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("size > 100 is clamped to 100")
    fun `size gt 100 is clamped to 100`() {
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<DecisionExecution> = PageImpl(emptyList())
        every { repository.findByTransactionId(TransactionId("TX-001"), capture(pageableSlot)) } returns emptyPage

        service.findByTransactionId(TransactionId("TX-001"), 0, 200)

        assertEquals(100, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("size exactly 100 is kept as-is")
    fun `size exactly 100 is kept as-is`() {
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<DecisionExecution> = PageImpl(emptyList())
        every { repository.findByRuleId(any(), capture(pageableSlot)) } returns emptyPage

        service.findByRuleId(RuleId(UUID.randomUUID()), 0, 100)

        assertEquals(100, pageableSlot.captured.pageSize)
    }

    @Test
    @DisplayName("size 1 is kept as-is (minimum valid)")
    fun `size 1 is kept as-is minimum valid`() {
        val pageableSlot = slot<Pageable>()
        val emptyPage: Page<DecisionExecution> = PageImpl(emptyList())
        every { repository.findByDecision(Decision.IGNORE, capture(pageableSlot)) } returns emptyPage

        service.findByDecision(Decision.IGNORE, 0, 1)

        assertEquals(1, pageableSlot.captured.pageSize)
    }
}
