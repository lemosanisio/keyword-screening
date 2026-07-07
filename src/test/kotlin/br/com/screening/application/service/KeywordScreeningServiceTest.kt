package br.com.screening.application.service

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.model.vo.RuleCode
import br.com.screening.application.cache.RestrictedTermsCache
import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningResult
import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.domain.service.KeywordMatcher
import br.com.screening.domain.service.TextNormalizer
import br.com.shared.domain.DomainEventPublisher
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class KeywordScreeningServiceTest {

    private val textNormalizer = mockk<TextNormalizer>()
    private val keywordMatcher = mockk<KeywordMatcher>()
    private val restrictedTermsCache = mockk<RestrictedTermsCache>()
    private val idempotencyService = mockk<IdempotencyService>()
    private val domainEventPublisher = mockk<DomainEventPublisher>()

    private val service = KeywordScreeningService(
        textNormalizer = textNormalizer,
        keywordMatcher = keywordMatcher,
        restrictedTermsCache = restrictedTermsCache,
        idempotencyService = idempotencyService,
        domainEventPublisher = domainEventPublisher
    )

    private val transactionId = TransactionId("TX-001")
    private val description = "pagamento terrorismo"
    private val ruleCode = "KEYWORD_SCREENING"
    private val command = EvaluateKeywordScreeningCommand(transactionId, CustomerId("CUST-001"), description)

    @BeforeEach
    fun setup() {
        clearMocks(textNormalizer, keywordMatcher, restrictedTermsCache, idempotencyService, domainEventPublisher)
    }

    @Test
    @DisplayName("when idempotencyService.findExisting returns a result, service returns it without calling normalizer/matcher/persist/publish")
    fun idempotencyHitReturnsExistingResult() {
        val existingResult = ScreeningResult(
            ruleCode = ruleCode,
            matched = true,
            matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
        )
        every { idempotencyService.findExisting(transactionId, ruleCode) } returns existingResult

        val result = service.execute(command)

        assertEquals(
            EvaluateKeywordScreeningResult(
                ruleCode = ruleCode,
                matched = true,
                matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
            ),
            result
        )

        verify(exactly = 0) { textNormalizer.normalize(any()) }
        verify(exactly = 0) { keywordMatcher.findMatches(any(), any()) }
        verify(exactly = 0) { restrictedTermsCache.getActiveTerms() }
        verify(exactly = 0) { idempotencyService.persist(any(), any(), any()) }
        verify(exactly = 0) { domainEventPublisher.publish(any()) }
    }

    @Test
    @DisplayName("new execution (no idempotency hit): normalizes, matches, persists, publishes event, returns result")
    fun newExecutionFullFlow() {
        val normalizedDescription = "pagamento terrorismo"
        val now = Instant.now()
        val activeTerms = setOf(
            RestrictedTerm(1L, "terrorismo", Category.TERRORISM, true, now, now)
        )
        val matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
        val screeningResult = ScreeningResult(ruleCode = ruleCode, matched = true, matches = matches)
        val eventSlot = slot<DetectionEvent>()

        every { idempotencyService.findExisting(transactionId, ruleCode) } returns null
        every { textNormalizer.normalize(description) } returns normalizedDescription
        every { restrictedTermsCache.getActiveTerms() } returns activeTerms
        every { keywordMatcher.findMatches(normalizedDescription, activeTerms) } returns matches
        every { idempotencyService.persist(transactionId, ruleCode, screeningResult) } returns screeningResult
        every { domainEventPublisher.publish(capture(eventSlot)) } just runs

        val result = service.execute(command)

        assertEquals(
            EvaluateKeywordScreeningResult(ruleCode = ruleCode, matched = true, matches = matches),
            result
        )

        verify(exactly = 1) { textNormalizer.normalize(description) }
        verify(exactly = 1) { keywordMatcher.findMatches(normalizedDescription, activeTerms) }
        verify(exactly = 1) { restrictedTermsCache.getActiveTerms() }
        verify(exactly = 1) { idempotencyService.persist(transactionId, ruleCode, screeningResult) }
        verify(exactly = 1) { domainEventPublisher.publish(any()) }

        val publishedEvent = eventSlot.captured
        assertEquals(transactionId, publishedEvent.transactionId)
        assertEquals(CustomerId("CUST-001"), publishedEvent.customerId)
        assertEquals(RuleCode(ruleCode), publishedEvent.ruleCode)
        assertEquals(true, publishedEvent.detectionResult.matched)
        assertEquals(1, publishedEvent.detectionResult.matches.size)
        assertEquals("terrorismo", publishedEvent.detectionResult.matches[0].term)
        assertEquals("TERRORISM", publishedEvent.detectionResult.matches[0].category)
    }

    @Test
    @DisplayName("result with matches: verifies matched=true and matches list populated")
    fun resultWithMatches() {
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
        every { domainEventPublisher.publish(any()) } just runs

        val result = service.execute(command)

        assertEquals(true, result.matched)
        assertEquals(1, result.matches.size)
        assertEquals("lavagem", result.matches[0].term)
        assertEquals(Category.AML, result.matches[0].category)
        assertEquals(ruleCode, result.ruleCode)
        verify(exactly = 1) { domainEventPublisher.publish(any()) }
    }

    @Test
    @DisplayName("result without matches: verifies matched=false and matches list empty")
    fun resultWithoutMatches() {
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
        every { domainEventPublisher.publish(any()) } just runs

        val result = service.execute(command)

        assertEquals(false, result.matched)
        assertTrue(result.matches.isEmpty())
        assertEquals(ruleCode, result.ruleCode)
        verify(exactly = 1) { domainEventPublisher.publish(any()) }
    }
}
