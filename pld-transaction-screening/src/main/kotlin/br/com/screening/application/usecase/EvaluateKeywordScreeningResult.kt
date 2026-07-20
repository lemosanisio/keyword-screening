package br.com.screening.application.usecase

import br.com.screening.domain.model.MatchResult

data class EvaluateKeywordScreeningResult(
    val ruleCode: String,
    val matched: Boolean,
    val matches: List<MatchResult>
)
