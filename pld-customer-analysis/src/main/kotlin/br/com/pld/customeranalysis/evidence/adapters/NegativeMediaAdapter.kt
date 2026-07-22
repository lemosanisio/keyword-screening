package br.com.pld.customeranalysis.evidence.adapters

import br.com.pld.customeranalysis.evidence.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Adapter simulado: Mídia negativa.
 * Retorna menções com veículo, data, resumo, relevância.
 * Simula 5% de resultados conflitantes.
 */
@Component
class NegativeMediaAdapter : EvidenceSourceAdapter {
    override val sourceCode = "NEGATIVE_MEDIA"
    override val sourceName = "Mídia Negativa (simulado)"

    private val vehicles = listOf("Folha de S.Paulo", "O Globo", "Valor Econômico", "Reuters", "Bloomberg", "G1")
    private val topics = listOf("investigação", "fraude", "lavagem", "corrupção", "evasão fiscal", "insider trading")

    override fun execute(partyId: String, requirementCode: String, attempt: Int): SourceExecutionResult {
        val seed = partyId.hashCode()
        val latency = 300L + abs(seed % 700)
        Thread.sleep(latency.coerceAtMost(1000))

        // 5% conflito simulado
        val hasConflict = abs(seed * 13) % 20 == 0

        val mediaCount = abs(seed % 5) // 0-4 menções
        val highRelevance = if (mediaCount > 0) abs(seed % mediaCount.coerceAtLeast(1)) else 0

        if (mediaCount == 0) {
            return SourceExecutionResult(
                status = SourceExecutionStatus.SUCCESS_NO_RESULTS,
                summary = "Nenhuma mídia negativa relevante encontrada.",
                validUntil = Instant.now().plus(30, ChronoUnit.DAYS),
                durationMs = latency,
                evidence = listOf(
                    EvidenceResult(
                        evidenceType = "MEDIA_CHECK",
                        title = "Consulta de mídia negativa",
                        summary = "Nenhum item relevante encontrado.",
                        structuredData = mapOf("items" to emptyList<Any>()),
                        facts = listOf(
                            FactResult("negativeMediaCount", "Menções negativas", 0, FactQuality.PRESENT),
                            FactResult("highRelevanceMediaCount", "Menções de alta relevância", 0, FactQuality.PRESENT),
                        ),
                    ),
                ),
            )
        }

        val items = (1..mediaCount).map { i ->
            mapOf(
                "vehicle" to vehicles[abs(seed + i) % vehicles.size],
                "date" to "2024-${1 + abs(seed + i) % 12}-${1 + abs(seed + i * 2) % 28}",
                "title" to "Menção em contexto de ${topics[abs(seed + i) % topics.size]}",
                "summary" to "[Extração auxiliar por IA] Citação em matéria sobre ${topics[abs(seed + i) % topics.size]}.",
                "relevance" to if (i <= highRelevance) "HIGH" else "MEDIUM",
                "entityResolved" to true,
                "conflict" to (hasConflict && i == 1),
            )
        }

        return SourceExecutionResult(
            status = if (hasConflict) SourceExecutionStatus.PARTIAL else SourceExecutionStatus.SUCCESS_WITH_DATA,
            summary = "$mediaCount menção(ões) encontrada(s), $highRelevance de alta relevância.${if (hasConflict) " CONFLITO: veículos divergentes." else ""}",
            validUntil = Instant.now().plus(14, ChronoUnit.DAYS),
            durationMs = latency,
            evidence = listOf(
                EvidenceResult(
                    evidenceType = "MEDIA_CHECK",
                    title = "Mídia negativa",
                    summary = "$mediaCount menção(ões). Alta relevância: $highRelevance.",
                    structuredData = mapOf("items" to items, "conflict" to hasConflict),
                    facts = listOf(
                        FactResult("negativeMediaCount", "Menções negativas", mediaCount, FactQuality.PRESENT),
                        FactResult("highRelevanceMediaCount", "Menções de alta relevância", highRelevance, FactQuality.PRESENT),
                    ),
                ),
            ),
        )
    }
}
