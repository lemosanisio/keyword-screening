package br.com.screening.application.service

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.RuleExecution
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.domain.repository.RuleExecutionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

class IdempotencyServiceTest : StringSpec({

    val transactionId = "TX-001"
    val ruleCode = "KEYWORD_SCREENING"

    "findExisting returns result when execution exists" {
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

        result shouldBe expectedResult
    }

    "findExisting returns null when no execution exists" {
        val repo = mockk<RuleExecutionRepository>()
        every { repo.findByTransactionIdAndRuleCode(transactionId, ruleCode) } returns null

        val service = IdempotencyService(repo)
        val result = service.findExisting(transactionId, ruleCode)

        result.shouldBeNull()
    }

    "persist saves and returns result" {
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

        result shouldBe screeningResult
    }

    "persist handles race condition - returns whatever repo returned" {
        val repo = mockk<RuleExecutionRepository>()
        val originalResult = ScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = listOf(MatchResult("lavagem", Category.AML))
        )
        // Simulates the repo returning a different result (existing record from race condition)
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

        // Service returns whatever the repo returned, even if different from the original
        result shouldBe existingResult
    }
})
