package br.com.screening.domain.model

import java.time.Instant

/**
 * Registro de auditoria completo de uma avaliação contextual.
 */
data class ContextualScreeningAudit(
    val id: Long? = null,
    val transactionId: String,
    val ruleId: String,
    val keyword: String,
    val prompt: String,
    val modelResponse: String?,
    val llmClassification: String?,
    val llmConfidence: Double?,
    val finalClassification: Classification,
    val finalConfidence: Double,
    val requiresAnalystReview: Boolean,
    val reason: String,
    val analystDecision: Classification? = null,
    val createdAt: Instant
)
