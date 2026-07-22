package br.com.pld.customeranalysis.evidence.adapters

import br.com.pld.customeranalysis.evidence.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Adapter simulado: Listas e sanções (OFAC, EU, ONU, PEP nacional).
 * Retorna matches com score de similaridade.
 * Simula 5% false positive, 3% indisponível.
 */
@Component
class SanctionsListsAdapter : EvidenceSourceAdapter {
    override val sourceCode = "SANCTIONS_LISTS"
    override val sourceName = "Listas e Sanções (simulado)"

    override fun execute(partyId: String, requirementCode: String, attempt: Int): SourceExecutionResult {
        val seed = partyId.hashCode()
        val latency = 150L + abs(seed % 400)
        Thread.sleep(latency.coerceAtMost(500))

        // 3% indisponível
        if (abs(seed * 7 + attempt) % 33 == 0 && attempt == 1) {
            return SourceExecutionResult(
                status = SourceExecutionStatus.UNAVAILABLE,
                summary = "Serviço de listas indisponível.",
                errorCode = "SIMULATED_OUTAGE",
                durationMs = latency,
            )
        }

        // 5% false positive (partial match)
        val hasMatch = abs(seed * 3) % 20 == 0
        val pepStatus = if (partyId.lowercase().contains("pep")) "DIRECT" else if (hasMatch) "RELATED" else "NONE"

        val evidence = if (hasMatch || pepStatus != "NONE") {
            listOf(
                EvidenceResult(
                    evidenceType = "SANCTIONS_CHECK",
                    title = "Resultado de consulta em listas",
                    summary = "Match parcial encontrado (score ${70 + abs(seed % 25)}%). PEP: $pepStatus.",
                    structuredData = mapOf(
                        "lists" to listOf("OFAC", "PEP_NACIONAL"),
                        "matchScore" to (70 + abs(seed % 25)),
                        "candidateName" to "Nome Similar ${abs(seed % 100)}",
                        "inclusionDate" to "2023-06-15",
                    ),
                    facts = listOf(
                        FactResult("sanctionsHit", "Match em listas", true, FactQuality.PRESENT),
                        FactResult("pepStatus", "Status PEP", pepStatus, FactQuality.PRESENT),
                        FactResult("sanctionsMatchScore", "Score de similaridade", 70 + abs(seed % 25), FactQuality.PRESENT),
                    ),
                ),
            )
        } else {
            listOf(
                EvidenceResult(
                    evidenceType = "SANCTIONS_CHECK",
                    title = "Resultado de consulta em listas",
                    summary = "Nenhum match encontrado em OFAC, EU, ONU, PEP nacional.",
                    structuredData = mapOf("lists" to listOf("OFAC", "EU", "ONU", "PEP_NACIONAL"), "matchScore" to 0),
                    facts = listOf(
                        FactResult("sanctionsHit", "Match em listas", false, FactQuality.PRESENT),
                        FactResult("pepStatus", "Status PEP", "NONE", FactQuality.PRESENT),
                        FactResult("sanctionsMatchScore", "Score de similaridade", 0, FactQuality.PRESENT),
                    ),
                ),
            )
        }

        return SourceExecutionResult(
            status = if (hasMatch || pepStatus != "NONE") SourceExecutionStatus.SUCCESS_WITH_DATA else SourceExecutionStatus.SUCCESS_NO_RESULTS,
            summary = if (hasMatch || pepStatus != "NONE") "Match encontrado — análise requerida." else "Nenhum match nas listas consultadas.",
            validUntil = Instant.now().plus(30, ChronoUnit.DAYS),
            durationMs = latency,
            evidence = evidence,
        )
    }
}
