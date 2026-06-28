package br.com.screening.domain.service

import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.RestrictedTerm

class KeywordMatcher {

    /**
     * Retorna todos os MatchResults para termos encontrados na descrição normalizada.
     * Considera apenas termos com active=true.
     * Complexidade: O(n * m) onde n = len(normalizedDescription), m = |activeTerms|
     */
    fun findMatches(
        normalizedDescription: String,
        activeTerms: Set<RestrictedTerm>
    ): List<MatchResult> =
        activeTerms
            .filter { it.active }
            .filter { normalizedDescription.contains(it.term) }
            .map { MatchResult(it.term, it.category) }
}
