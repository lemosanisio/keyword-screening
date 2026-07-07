package br.com.decision.infrastructure.input.http.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/**
 * DTO de request para criação de uma Rule Configuration.
 * POST /v1/decision/rules/{ruleCode}/configurations
 */
data class CreateRuleConfigurationRequest(
    @field:NotEmpty(message = "expressions não pode ser vazio")
    @field:Size(max = 10, message = "Máximo 10 expressions por configuração")
    @field:Valid
    val expressions: List<ConditionDto> = emptyList(),

    @field:NotEmpty(message = "actions não pode ser vazio")
    val actions: List<String> = emptyList(),

    @field:NotBlank(message = "createdBy é obrigatório")
    @field:Size(max = 100, message = "createdBy deve ter no máximo 100 caracteres")
    val createdBy: String = ""
)
