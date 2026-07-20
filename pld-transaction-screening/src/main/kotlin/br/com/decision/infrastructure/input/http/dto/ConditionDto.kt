package br.com.decision.infrastructure.input.http.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * DTO representando uma expressão atômica (Condition) na API REST.
 * No MVP, apenas type=CONDITION é suportado.
 */
data class ConditionDto(
    @field:NotBlank(message = "type é obrigatório")
    val type: String = "CONDITION",

    @field:NotBlank(message = "factName é obrigatório")
    val factName: String = "",

    @field:NotBlank(message = "operator é obrigatório")
    val operator: String = "",

    @field:NotNull(message = "expectedValue é obrigatório")
    val expectedValue: Any? = null
)
