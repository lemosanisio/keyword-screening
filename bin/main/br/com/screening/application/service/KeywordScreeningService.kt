package br.com.screening.application.service

import br.com.screening.application.cache.RestrictedTermsCache
import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningResult
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import br.com.screening.domain.ScreeningRule
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.domain.service.KeywordMatcher
import br.com.screening.domain.service.TextNormalizer
import org.springframework.stereotype.Service

@Service
class KeywordScreeningService(
    private val textNormalizer: TextNormalizer,
    private val keywordMatcher: KeywordMatcher,
    private val restrictedTermsCache: RestrictedTermsCache,
    private val idempotencyService: IdempotencyService
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
        return EvaluateKeywordScreeningResult(persisted.ruleCode, persisted.matched, persisted.matches)
    }

    override fun evaluate(transactionId: String, description: String): ScreeningResult {
        val result = execute(EvaluateKeywordScreeningCommand(transactionId, description))
        return ScreeningResult(ruleCode = result.ruleCode, matched = result.matched, matches = result.matches)
    }
}
