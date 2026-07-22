package br.com.pld.customeranalysis.evidence.adapters

import br.com.pld.customeranalysis.evidence.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Adapter simulado: Processos judiciais.
 * Retorna processos com número, tribunal, classe, papel, status.
 * Simula latência 500-2000ms (consulta mais lenta).
 */
@Component
class LegalProceedingsAdapter : EvidenceSourceAdapter {
    override val sourceCode = "LEGAL_PROCEEDINGS"
    override val sourceName = "Processos Judiciais (simulado)"

    private val tribunals = listOf("TJSP", "TJRJ", "TRF3", "STJ", "TRT2")
    private val classes = listOf("Ação Civil", "Execução Fiscal", "Inquérito Policial", "Ação Penal", "Mandado de Segurança")
    private val roles = listOf("AUTHOR", "DEFENDANT", "THIRD_PARTY", "WITNESS")

    override fun execute(partyId: String, requirementCode: String, attempt: Int): SourceExecutionResult {
        val seed = partyId.hashCode()
        val latency = 500L + abs(seed % 1500)
        Thread.sleep(latency.coerceAtMost(2000))

        // 5% error simulado
        if (abs(seed * 11 + attempt) % 20 == 0 && attempt == 1) {
            return SourceExecutionResult(
                status = SourceExecutionStatus.ERROR,
                summary = "Erro ao consultar processos judiciais.",
                errorCode = "SIMULATED_API_ERROR",
                durationMs = latency,
            )
        }

        val proceedingsCount = abs(seed % 4) // 0-3 processos
        val hasCriminal = abs(seed % 5) == 0
        val hasFinancial = abs(seed % 7) == 0

        if (proceedingsCount == 0) {
            return SourceExecutionResult(
                status = SourceExecutionStatus.SUCCESS_NO_RESULTS,
                summary = "Nenhum processo judicial encontrado.",
                validUntil = Instant.now().plus(60, ChronoUnit.DAYS),
                durationMs = latency,
                evidence = listOf(
                    EvidenceResult(
                        evidenceType = "LEGAL_CHECK",
                        title = "Consulta judicial sem resultados",
                        summary = "Nenhum processo encontrado nos tribunais consultados.",
                        structuredData = mapOf("proceedings" to emptyList<Any>()),
                        facts = listOf(
                            FactResult("activeProceedings", "Processos ativos", 0, FactQuality.PRESENT),
                            FactResult("criminalProceedings", "Processos criminais", false, FactQuality.PRESENT),
                            FactResult("financialProceedings", "Processos financeiros", false, FactQuality.PRESENT),
                        ),
                    ),
                ),
            )
        }

        val proceedings = (1..proceedingsCount).map { i ->
            mapOf(
                "number" to "${abs(seed + i) % 9999999}-${20 + abs(seed % 6)}.8.26.0100",
                "tribunal" to tribunals[abs(seed + i) % tribunals.size],
                "class" to classes[abs(seed + i * 3) % classes.size],
                "role" to roles[abs(seed + i * 7) % roles.size],
                "status" to if (i == 1) "ACTIVE" else "ARCHIVED",
            )
        }

        return SourceExecutionResult(
            status = SourceExecutionStatus.SUCCESS_WITH_DATA,
            summary = "$proceedingsCount processo(s) encontrado(s). Criminal: $hasCriminal. Financeiro: $hasFinancial.",
            validUntil = Instant.now().plus(60, ChronoUnit.DAYS),
            durationMs = latency,
            evidence = listOf(
                EvidenceResult(
                    evidenceType = "LEGAL_CHECK",
                    title = "Processos judiciais",
                    summary = "$proceedingsCount processo(s) encontrado(s).",
                    structuredData = mapOf("proceedings" to proceedings),
                    facts = listOf(
                        FactResult("activeProceedings", "Processos ativos", proceedingsCount, FactQuality.PRESENT),
                        FactResult("criminalProceedings", "Processos criminais", hasCriminal, FactQuality.PRESENT),
                        FactResult("financialProceedings", "Processos financeiros", hasFinancial, FactQuality.PRESENT),
                    ),
                ),
            ),
        )
    }
}
