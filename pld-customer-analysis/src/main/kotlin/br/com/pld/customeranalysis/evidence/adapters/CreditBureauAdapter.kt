package br.com.pld.customeranalysis.evidence.adapters

import br.com.pld.customeranalysis.evidence.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Adapter simulado: Bureau de crédito.
 * Retorna score, renda presumida, alertas de crédito.
 * Simula latência 200-800ms, timeout 10%.
 */
@Component
class CreditBureauAdapter : EvidenceSourceAdapter {
    override val sourceCode = "CREDIT_BUREAU"
    override val sourceName = "Bureau de Crédito (simulado)"

    override fun execute(partyId: String, requirementCode: String, attempt: Int): SourceExecutionResult {
        val seed = partyId.hashCode()
        val latency = 200L + abs(seed % 600)
        Thread.sleep(latency.coerceAtMost(800))

        // 10% timeout simulado
        if (abs(seed * attempt) % 10 == 0 && attempt == 1) {
            return SourceExecutionResult(
                status = SourceExecutionStatus.UNAVAILABLE,
                summary = "Timeout na consulta ao bureau de crédito.",
                errorCode = "SIMULATED_TIMEOUT",
                durationMs = latency,
            )
        }

        val score = 300 + abs(seed % 600)
        val income = (5000 + abs(seed % 45000)).toLong()
        val alerts = abs(seed % 4)

        return SourceExecutionResult(
            status = SourceExecutionStatus.SUCCESS_WITH_DATA,
            summary = "Score $score, renda presumida R$ $income, $alerts alertas.",
            validUntil = Instant.now().plus(90, ChronoUnit.DAYS),
            durationMs = latency,
            evidence = listOf(
                EvidenceResult(
                    evidenceType = "CREDIT_REPORT",
                    title = "Relatório de crédito",
                    summary = "Score $score com $alerts alertas ativos.",
                    structuredData = mapOf(
                        "creditScore" to score,
                        "presumedIncome" to mapOf("value" to "$income.00", "currency" to "BRL"),
                        "creditAlerts" to alerts,
                        "consultationCount" to abs(seed % 12),
                    ),
                    facts = listOf(
                        FactResult("creditScore", "Score de crédito", score, FactQuality.PRESENT),
                        FactResult("presumedIncome", "Renda presumida", mapOf("value" to "$income.00", "currency" to "BRL"), FactQuality.PRESENT),
                        FactResult("creditAlerts", "Alertas de crédito", alerts, FactQuality.PRESENT),
                    ),
                ),
            ),
        )
    }
}
