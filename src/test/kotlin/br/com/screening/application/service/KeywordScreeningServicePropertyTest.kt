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
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

/**
 * Property-based test para [KeywordScreeningService].
 *
 * Property 7 — Validates: Requirements 5.1, 5.2, 5.3
 */
// Feature: mf09-keyword-screening, Property 7: Idempotência de avaliação
class KeywordScreeningServicePropertyTest : StringSpec({

    // Feature: mf09-keyword-screening, Property 7: Idempotência de avaliação
    /**
     * For any transactionId and description, two calls with the same params return identical results.
     *
     * Implementation: uses a fake in-memory store for idempotency via MockK slot/capture.
     * First call: idempotencyService.findExisting returns null → executes rule → persist stores result
     * Second call: idempotencyService.findExisting returns the stored result
     * Verify: both results are identical.
     *
     * Validates: Requirements 5.1, 5.2, 5.3
     */
    "Property 7: para qualquer transactionId e description, duas chamadas retornam resultados identicos" {
        forAll(Arb.string(1..50), Arb.string(1..140)) { transactionId, description ->
            // In-memory idempotency store
            val store = mutableMapOf<String, ScreeningResult>()

            val textNormalizer = TextNormalizer()
            val keywordMatcher = KeywordMatcher()
            val restrictedTermsCache = mockk<RestrictedTermsCache>()
            val idempotencyService = mockk<IdempotencyService>()

            val ruleCode = "KEYWORD_SCREENING"
            val now = Instant.now()

            // Provide some active terms in the cache
            val activeTerms = setOf(
                RestrictedTerm(1L, "lavagem", Category.AML, true, now, now),
                RestrictedTerm(2L, "terrorismo", Category.TERRORISM, true, now, now),
                RestrictedTerm(3L, "fraude", Category.FRAUD, true, now, now)
            )
            every { restrictedTermsCache.getActiveTerms() } returns activeTerms

            // Mock idempotencyService to use in-memory store
            every { idempotencyService.findExisting(transactionId, ruleCode) } answers {
                store["$transactionId:$ruleCode"]
            }
            every { idempotencyService.persist(transactionId, ruleCode, any()) } answers {
                val result = thirdArg<ScreeningResult>()
                store["$transactionId:$ruleCode"] = result
                result
            }

            val service = KeywordScreeningService(
                textNormalizer = textNormalizer,
                keywordMatcher = keywordMatcher,
                restrictedTermsCache = restrictedTermsCache,
                idempotencyService = idempotencyService
            )

            val command = EvaluateKeywordScreeningCommand(transactionId, description)

            // First call — should execute the rule and persist
            val firstResult = service.execute(command)

            // Second call — should return cached result via idempotency
            val secondResult = service.execute(command)

            // Both results must be identical
            firstResult == secondResult
        }
    }
})
