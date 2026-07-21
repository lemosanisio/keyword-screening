package br.com.screening.application.service

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.vo.RuleCode
import br.com.screening.application.cache.RestrictedTermsCache
import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningResult
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import br.com.screening.domain.ScreeningRule
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.domain.service.KeywordMatcher
import br.com.screening.domain.service.TextNormalizer
import br.com.shared.domain.DomainEventPublisher
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.PrefixedUlid
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class KeywordScreeningService(
    private val textNormalizer: TextNormalizer,
    private val keywordMatcher: KeywordMatcher,
    private val restrictedTermsCache: RestrictedTermsCache,
    private val idempotencyService: IdempotencyService,
    private val domainEventPublisher: DomainEventPublisher
) : EvaluateKeywordScreeningUseCase, ScreeningRule {

    override val ruleCode = "KEYWORD_SCREENING"

    override fun execute(command: EvaluateKeywordScreeningCommand): EvaluateKeywordScreeningResult {
        // Check idempotency
        val existing = idempotencyService.findExisting(command.transactionId, ruleCode)
        if (existing != null) {
            return EvaluateKeywordScreeningResult(existing.ruleCode, existing.matched, existing.matches)
        }

        // Execute rule
        val normalizedDescription = textNormalizer.normalize(command.description)
        val activeTerms = restrictedTermsCache.getActiveTerms()
        val matches = keywordMatcher.findMatches(normalizedDescription, activeTerms)
        val result = ScreeningResult(ruleCode = ruleCode, matched = matches.isNotEmpty(), matches = matches)

        // Persist
        val persisted = idempotencyService.persist(command.transactionId, ruleCode, result)

        // Publish DetectionEvent
        val detectionEvent = DetectionEvent(
            eventId = EventId(PrefixedUlid.ulid()),
            traceId = TraceId(command.correlationId ?: PrefixedUlid.ulid()),
            timestamp = Instant.now(),
            transactionId = command.transactionId,
            customerId = command.customerId,
            ruleCode = RuleCode(ruleCode),
            detectionResult = DetectionResult(
                matched = persisted.matched,
                matches = persisted.matches.map {
                    DetectionMatch(term = it.term, category = it.category.name)
                }
            )
        )
        domainEventPublisher.publish(detectionEvent)

        return EvaluateKeywordScreeningResult(persisted.ruleCode, persisted.matched, persisted.matches)
    }

    override fun evaluate(transactionId: TransactionId, description: String): ScreeningResult {
        val result = execute(EvaluateKeywordScreeningCommand(transactionId, CustomerId("anonymous"), description))
        return ScreeningResult(ruleCode = result.ruleCode, matched = result.matched, matches = result.matches)
    }
}
