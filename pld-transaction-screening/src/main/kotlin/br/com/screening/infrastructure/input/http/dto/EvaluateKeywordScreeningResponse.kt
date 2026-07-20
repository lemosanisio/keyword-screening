package br.com.screening.infrastructure.input.http.dto

data class EvaluateKeywordScreeningResponse(
    val ruleCode: String,
    val matched: Boolean,
    val matches: List<MatchResultResponse>
)
