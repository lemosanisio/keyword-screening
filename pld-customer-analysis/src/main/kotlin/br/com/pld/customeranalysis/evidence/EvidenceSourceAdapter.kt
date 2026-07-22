package br.com.pld.customeranalysis.evidence

import java.time.Instant

/**
 * Interface que cada adapter de fonte de evidência implementa.
 * Cada adapter produz executions com evidence records e facts para um requirement.
 */
interface EvidenceSourceAdapter {
    /** Código único do adapter (ex: CREDIT_BUREAU, SANCTIONS_LISTS) */
    val sourceCode: String
    /** Nome legível para exibição */
    val sourceName: String

    /**
     * Executa a consulta à fonte para o party fornecido.
     * Retorna o resultado com status, evidências e fatos.
     * Pode simular latência e falhas.
     */
    fun execute(partyId: String, requirementCode: String, attempt: Int): SourceExecutionResult
}

data class SourceExecutionResult(
    val status: SourceExecutionStatus,
    val summary: String,
    val errorCode: String? = null,
    val validUntil: Instant? = null,
    val durationMs: Long = 0,
    val evidence: List<EvidenceResult> = emptyList(),
)

data class EvidenceResult(
    val evidenceType: String,
    val title: String,
    val summary: String,
    val classification: EvidenceClassification = EvidenceClassification.CONFIDENTIAL,
    val structuredData: Map<String, Any?> = emptyMap(),
    val facts: List<FactResult> = emptyList(),
)

data class FactResult(
    val code: String,
    val label: String,
    val value: Any?,
    val quality: FactQuality,
)
