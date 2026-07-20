package br.com.screening.domain.model

import java.time.Instant

/**
 * Decisão histórica de um analista, usada como exemplo para few-shot learning.
 */
data class HistoricalDecision(
    val id: Long? = null,
    val keyword: String,
    val description: String,
    val analystDecision: Classification,
    val createdAt: Instant
)
