package br.com.screening.infrastructure.input.http.dto

data class ContextualScreeningResponse(
    val classification: String,
    val confidence: Double,
    val reason: String,
    val requiresAnalystReview: Boolean
)
