package br.com.screening.application.usecase

data class ContextualScreeningResultDto(
    val classification: String,
    val confidence: Double,
    val reason: String,
    val requiresAnalystReview: Boolean
)
