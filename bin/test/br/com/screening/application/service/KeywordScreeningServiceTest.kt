package br.com.screening.application.service

import br.com.screening.application.cache.RestrictedTermsCache
import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningResult
import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.domain.service.KeywordMatcher
import br.com.screening.domain.service.TextNormalizer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class KeywordScreeningServiceTest : StringSpec({

    val textNormalizer = mockk<TextNormalizer>()
    val keywordMatcher = mockk<KeywordMatcher>()
    val restrictedTermsCache = mockk<RestrictedTermsCache>()
    val idempotencyService = mockk<IdempotencyService>()

    val service = KeywordScreeningService(
        textNormalizer = textNormalizer,
        keywordMatcher = keywordMatcher,
        restrictedTermsCache = restrictedTermsCache,
        idempotencyService = idempotencyService
    )

    val transactionId = "TX-001"
    val description = "pagamento terrorismo"
    val ruleCode = "KEYWORD_SCREENING"
    val command = EvaluateKeywordScreeningCommand(transactionId, description)

    "when idempotencyService.findExisting returns a result, service returns it without calling normalizer/matcher/persist" {
        val existingResult = ScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
        )
        every { idempotencyService.findExisting(transactionId, ruleCode) } returns existingResult

        val result = service.execute(command)

        result shouldBe EvaluateKeywordScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
        )

        verify(exactly = 0) { textNormalizer.normalize(any()) }
        verify(exactly = 0) { keywordMatcher.findMatches(any(), any()) }
        verify(exactly = 0) { restrictedTermsCache.getActiveTerms() }
        verify(exactly = 0) { idempotencyService.persist(any(), any(), any()) }
    }

    "new execution (no idempotency hit): normalizes, matches, persists, returns result" {
        val normalizedDescription = "pagamento terrorismo"
        val now = Instant.now()
        val activeTerms = setOf(
            RestrictedTerm(1L, "terrorismo", Category.TERRORISM, true, now, now)
        )
        val matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
        val screeningResult = ScreeningResult(ruleCode = ruleCode, matched = true, matches = matches)

        every { idempotencyService.findExisting(transactionId, ruleCode) } returns null
        every { textNormalizer.normalize(description) } returns normalizedDescription
        every { restrictedTermsCache.getActiveTerms() } returns activeTerms
        every { keywordMatcher.findMatches(normalizedDescription, activeTerms) } returns matches
        every { idempotencyService.persist(transactionId, ruleCode, screeningResult) } returns screeningResult

        val result = service.execute(command)

        result shouldBe EvaluateKeywordScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = matches
        )

        verify(exactly = 1) { textNormalizer.normalize(description) }
        verify(exactly = 1) { keywordMatcher.findMatches(normalizedDescription, activeTerms) }
        verify(exactly = 1) { restrictedTermsCache.getActiveTerms() }
        verify(exactly = 1) { idempotencyService.persist(transactionId, ruleCode, screeningResult) }
    }

    "result with matches: verifies matched=true and matches list populated" {
        val normalizedDescription = "deposito lavagem dinheiro"
        val now = Instant.now()
        val activeTerms = setOf(
            RestrictedTerm(1L, "lavagem", Category.AML, true, now, now),
            RestrictedTerm(2L, "fraude", Category.FRAUD, true, now, now)
        )
        val matches = listOf(MatchResult("lavagem", Category.AML))
        val screeningResult = ScreeningResult(ruleCode = ruleCode, matched = true, matches = matches)

        every { idempotencyService.findExisting(transactionId, ruleCode) } returns null
        every { textNormalizer.normalize(description) } returns normalizedDescription
        every { restrictedTermsCache.getActiveTerms() } returns activeTerms
        every { keywordMatcher.findMatches(normalizedDescription, activeTerms) } returns matches
        every { idempotencyService.persist(transactionId, ruleCode, screeningResult) } returns screeningResult

        val result = service.execute(command)

        result.matched shouldBe true
        result.matches shouldHaveSize 1
        result.matches[0].term shouldBe "lavagem"
        result.matches[0].category shouldBe Category.AML
        result.ruleCode shouldBe ruleCode
    }

    "result without matches: verifies matched=false and matches list empty" {
        val normalizedDescription = "transferencia para conta corrente"
        val now = Instant.now()
        val activeTerms = setOf(
            RestrictedTerm(1L, "lavagem", Category.AML, true, now, now),
            RestrictedTerm(2L, "terrorismo", Category.TERRORISM, true, now, now)
        )
        val emptyMatches = emptyList<MatchResult>()
        val screeningResult = ScreeningResult(ruleCode = ruleCode, matched = false, matches = emptyMatches)

        every { idempotencyService.findExisting(transactionId, ruleCode) } returns null
        every { textNormalizer.normalize(description) } returns normalizedDescription
        every { restrictedTermsCache.getActiveTerms() } returns activeTerms
        every { keywordMatcher.findMatches(normalizedDescription, activeTerms) } returns emptyMatches
        every { idempotencyService.persist(transactionId, ruleCode, screeningResult) } returns screeningResult

        val result = service.execute(command)

        result.matched shouldBe false
        result.matches.shouldBeEmpty()
        result.ruleCode shouldBe ruleCode
    }
})
