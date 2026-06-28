package br.com.screening.domain.model

/**
 * Resultado da avaliação contextual de uma transação.
 */
data class ContextualScreeningResult(
    val classification: Classification,
    val confidence: Double,
    val reason: String,
    val requiresAnalystReview: Boolean
)
