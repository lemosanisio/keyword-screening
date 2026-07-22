package br.com.pld.customeranalysis.party

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.integration.OutboxService
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Publisher mock de CustomerRiskProfileUpdated.v1.
 * Gera um perfil de risco simulado para cada Party criada.
 *
 * Regras de simulação:
 * - Nome contém "suspicious" ou "alto risco" → HIGH + [PEP_RELATED]
 * - Nome contém "pep" → HIGH + [PEP_RELATED, CROSS_BORDER]
 * - Nome contém "medio" ou "medium" → MEDIUM + [CROSS_BORDER]
 * - Default → LOW + []
 */
@Component
class RiskProfilePublisher(
    private val outboxService: OutboxService,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun publishForParty(partyId: String, officialName: String, correlationId: String) {
        val now = Instant.now(clock)
        val riskProfile = deriveRisk(officialName)

        outboxService.append(
            eventType = "CustomerRiskProfileUpdated",
            aggregateType = "RiskProfile",
            aggregateId = riskProfile.riskProfileId,
            payload = mapOf(
                "riskProfileId" to riskProfile.riskProfileId,
                "profileVersion" to 1,
                "partyId" to partyId,
                "effectiveFrom" to now.toString(),
                "validUntil" to now.plus(riskProfile.validDays, ChronoUnit.DAYS).toString(),
                "riskLevel" to riskProfile.riskLevel,
                "segments" to riskProfile.segments,
                "transactionFacts" to mapOf(
                    "expectedMonthlyIncome" to mapOf(
                        "value" to riskProfile.expectedIncome,
                        "currency" to "BRL",
                        "quality" to "PRESENT",
                    ),
                    "expectedCountries" to mapOf(
                        "value" to riskProfile.expectedCountries,
                        "quality" to "PRESENT",
                    ),
                ),
                "policyVersion" to "customer-risk-mock-v1",
                "assessmentId" to PrefixedUlid.next("asm_"),
                "reasonCodes" to riskProfile.reasonCodes,
                "correlationId" to correlationId,
            ),
        )
    }

    private fun deriveRisk(name: String): SimulatedRisk {
        val lower = name.lowercase()
        return when {
            "pep" in lower -> SimulatedRisk(
                riskLevel = "HIGH",
                segments = listOf("PEP_RELATED", "CROSS_BORDER"),
                expectedIncome = "50000.00",
                expectedCountries = listOf("BR", "US"),
                validDays = 90L,
                reasonCodes = listOf("PEP_EXPOSURE"),
            )
            "suspicious" in lower || "alto risco" in lower || "suspeito" in lower -> SimulatedRisk(
                riskLevel = "HIGH",
                segments = listOf("PEP_RELATED"),
                expectedIncome = "30000.00",
                expectedCountries = listOf("BR"),
                validDays = 90L,
                reasonCodes = listOf("ELEVATED_RISK_INDICATORS"),
            )
            "medio" in lower || "medium" in lower -> SimulatedRisk(
                riskLevel = "MEDIUM",
                segments = listOf("CROSS_BORDER"),
                expectedIncome = "15000.00",
                expectedCountries = listOf("BR", "PT"),
                validDays = 180L,
                reasonCodes = listOf("STANDARD_REVIEW"),
            )
            else -> SimulatedRisk(
                riskLevel = "LOW",
                segments = emptyList(),
                expectedIncome = "8000.00",
                expectedCountries = listOf("BR"),
                validDays = 365L,
                reasonCodes = listOf("STANDARD_ONBOARDING"),
            )
        }
    }

    private data class SimulatedRisk(
        val riskLevel: String,
        val segments: List<String>,
        val expectedIncome: String,
        val expectedCountries: List<String>,
        val validDays: Long,
        val reasonCodes: List<String>,
        val riskProfileId: String = PrefixedUlid.next("rsk_"),
    )
}
