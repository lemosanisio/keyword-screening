package br.com.pld.customeranalysis.evidence

import org.springframework.stereotype.Component

/**
 * Política de evidência versionada por riskLevel.
 * Define quais requirements são obrigatórios para cada nível de risco.
 *
 * LOW:    CREDIT_BUREAU + SANCTIONS_LISTS (2 obrigatórios)
 * MEDIUM: + LEGAL_PROCEEDINGS (3 obrigatórios)
 * HIGH:   + NEGATIVE_MEDIA (4 obrigatórios, todos)
 */
@Component
class EvidencePolicy {

    val policyVersion: String = "evidence-policy-v2"

    fun requirementsFor(riskLevel: String): List<PolicyRequirement> {
        val base = listOf(
            PolicyRequirement(
                code = "CREDIT_BUREAU_CHECK",
                title = "Bureau de crédito consultado",
                category = "FINANCIAL",
                sourceCode = "CREDIT_BUREAU",
                mandatory = true,
            ),
            PolicyRequirement(
                code = "SANCTIONS_LISTS_CHECK",
                title = "Listas e sanções consultadas",
                category = "LISTS",
                sourceCode = "SANCTIONS_LISTS",
                mandatory = true,
            ),
        )

        val medium = listOf(
            PolicyRequirement(
                code = "LEGAL_PROCEEDINGS_CHECK",
                title = "Processos judiciais consultados",
                category = "LEGAL",
                sourceCode = "LEGAL_PROCEEDINGS",
                mandatory = true,
            ),
        )

        val high = listOf(
            PolicyRequirement(
                code = "NEGATIVE_MEDIA_CHECK",
                title = "Mídia negativa consultada",
                category = "MEDIA",
                sourceCode = "NEGATIVE_MEDIA",
                mandatory = true,
            ),
        )

        return when (riskLevel.uppercase()) {
            "HIGH" -> base + medium + high
            "MEDIUM" -> base + medium
            else -> base
        }
    }
}

data class PolicyRequirement(
    val code: String,
    val title: String,
    val category: String,
    val sourceCode: String,
    val mandatory: Boolean,
)
