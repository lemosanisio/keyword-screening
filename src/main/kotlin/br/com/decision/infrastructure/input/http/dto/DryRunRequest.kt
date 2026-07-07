package br.com.decision.infrastructure.input.http.dto

import jakarta.validation.constraints.NotEmpty

/**
 * Request para execução de dry-run.
 * O analista fornece manualmente os facts tipados para testar a configuração.
 *
 * Exemplo:
 * ```json
 * {
 *   "facts": {
 *     "keywordMatched": true,
 *     "customerRisk": "MR"
 *   }
 * }
 * ```
 */
data class DryRunRequest(
    @field:NotEmpty(message = "facts é obrigatório e não pode ser vazio")
    val facts: Map<String, Any>
)
