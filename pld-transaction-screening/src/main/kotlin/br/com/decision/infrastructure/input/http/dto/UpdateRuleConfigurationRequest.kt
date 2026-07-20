package br.com.decision.infrastructure.input.http.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/**
 * DTO de request para atualização de uma Rule Configuration.
 * PUT /v1/decision/rule-configurations/{id}
 */
data class UpdateRuleConfigurationRequest(
    @field:NotEmpty(message = "expressions não pode ser vazio")
    @field:Size(max = 10, message = "Máximo 10 expressions por configuração")
    @field:Valid
    val expressions: List<ConditionDto> = emptyList(),

    @field:NotEmpty(message = "actions não pode ser vazio")
    val actions: List<String> = emptyList(),

    @field:NotBlank(message = "updatedBy é obrigatório")
    @field:Size(max = 100, message = "updatedBy deve ter no máximo 100 caracteres")
    val updatedBy: String = ""
)
