package br.com.screening.application.service

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.RuleExecution
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.domain.repository.RuleExecutionRepository
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class IdempotencyServiceTest {

    private val transactionId = TransactionId("TX-001")
    private val ruleCode = "KEYWORD_SCREENING"

    @Test
    @DisplayName("findExisting returns result when execution exists")
    fun findExistingReturnsResultWhenExists() {
        val repo = mockk<RuleExecutionRepository>()
        val expectedResult = ScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = listOf(MatchResult("lavagem", Category.AML))
        )
        val execution = RuleExecution(
            id = 1L,
            transactionId = transactionId,
            ruleCode = ruleCode,
            result = expectedResult,
            createdAt = Instant.now()
        )
        every { repo.findByTransactionIdAndRuleCode(transactionId, ruleCode) } returns execution

        val service = IdempotencyService(repo)
        val result = service.findExisting(transactionId, ruleCode)

        assertEquals(expectedResult, result)
    }

    @Test
    @DisplayName("findExisting returns null when no execution exists")
    fun findExistingReturnsNullWhenNotExists() {
        val repo = mockk<RuleExecutionRepository>()
        every { repo.findByTransactionIdAndRuleCode(transactionId, ruleCode) } returns null

        val service = IdempotencyService(repo)
        val result = service.findExisting(transactionId, ruleCode)

        assertNull(result)
    }

    @Test
    @DisplayName("persist saves and returns result")
    fun persistSavesAndReturnsResult() {
        val repo = mockk<RuleExecutionRepository>()
        val screeningResult = ScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
        )
        every { repo.save(any()) } answers {
            val input = firstArg<RuleExecution>()
            input.copy(id = 1L)
        }

        val service = IdempotencyService(repo)
        val result = service.persist(transactionId, ruleCode, screeningResult)

        assertEquals(screeningResult, result)
    }

    @Test
    @DisplayName("persist handles race condition - returns whatever repo returned")
    fun persistHandlesRaceCondition() {
        val repo = mockk<RuleExecutionRepository>()
        val originalResult = ScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = listOf(MatchResult("lavagem", Category.AML))
        )
        val existingResult = ScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = listOf(MatchResult("lavagem", Category.AML), MatchResult("fraude", Category.FRAUD))
        )
        every { repo.save(any()) } returns RuleExecution(
            id = 1L,
            transactionId = transactionId,
            ruleCode = ruleCode,
            result = existingResult,
            createdAt = Instant.now()
        )

        val service = IdempotencyService(repo)
        val result = service.persist(transactionId, ruleCode, originalResult)

        assertEquals(existingResult, result)
    }
}
