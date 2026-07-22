package br.com.screening.application.service

import br.com.screening.application.cache.RestrictedTermsCache
import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.domain.service.KeywordMatcher
import br.com.screening.domain.service.TextNormalizer
import br.com.shared.domain.DomainEventPublisher
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import kotlin.random.Random

/**
 * Property-based test para [KeywordScreeningService].
 *
 * Property 7 — Validates: Requirements 5.1, 5.2, 5.3
 */
class KeywordScreeningServicePropertyTest {

    @RepeatedTest(200)
    @DisplayName("Property 7: para qualquer transactionId e description, duas chamadas retornam resultados idênticos")
    fun idempotencyPropertyForAnyInput() {
        val rawTransactionId = buildString {
            val len = Random.nextInt(1, 51)
            repeat(len) { append(('a'..'z').random()) }
        }.ifBlank { "TX-DEFAULT" }
        val description = buildString {
            val len = Random.nextInt(1, 141)
            repeat(len) { append(('a'..'z').random()) }
        }

        val transactionId = TransactionId(rawTransactionId)
        val store = mutableMapOf<String, ScreeningResult>()

        val textNormalizer = TextNormalizer()
        val keywordMatcher = KeywordMatcher()
        val restrictedTermsCache = mockk<RestrictedTermsCache>()
        val idempotencyService = mockk<IdempotencyService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        every { domainEventPublisher.publish(any()) } just runs

        val ruleCode = "KEYWORD_SCREENING"
        val now = Instant.now()

        val activeTerms = setOf(
            RestrictedTerm(1L, "lavagem", Category.AML, true, now, now),
            RestrictedTerm(2L, "terrorismo", Category.TERRORISM, true, now, now),
            RestrictedTerm(3L, "fraude", Category.FRAUD, true, now, now)
        )
        every { restrictedTermsCache.getActiveTerms() } returns activeTerms

        every { idempotencyService.findExisting(transactionId, ruleCode) } answers {
            store["${transactionId.value}:$ruleCode"]
        }
        every { idempotencyService.persist(transactionId, ruleCode, any()) } answers {
            val result = thirdArg<ScreeningResult>()
            store["${transactionId.value}:$ruleCode"] = result
            result
        }

        val intakeGuard = mockk<ScreeningIntakeGuard>()
        every { intakeGuard.register(any(), any(), any()) } returns "01J6ZK7Q3W8K0M2N4P6R8T0V6A"
        val service = KeywordScreeningService(
            textNormalizer = textNormalizer,
            keywordMatcher = keywordMatcher,
            restrictedTermsCache = restrictedTermsCache,
            idempotencyService = idempotencyService,
            domainEventPublisher = domainEventPublisher,
            screeningIntakeGuard = intakeGuard,
        )

        val command = EvaluateKeywordScreeningCommand(transactionId, CustomerId("CUST-PBT"), description)

        val firstResult = service.execute(command)
        val secondResult = service.execute(command)

        assertEquals(firstResult, secondResult)
    }
}
