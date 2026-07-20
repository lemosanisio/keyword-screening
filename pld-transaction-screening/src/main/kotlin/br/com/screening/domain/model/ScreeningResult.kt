package br.com.screening.domain.model

data class ScreeningResult(
    val ruleCode: String,
    val matched: Boolean,
    val matches: List<MatchResult>
)
