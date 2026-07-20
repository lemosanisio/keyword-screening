package br.com.screening.infrastructure.input.http.dto

import jakarta.validation.constraints.NotBlank

data class AnalystDecisionRequest(
    @field:NotBlank(message = "transactionId é obrigatório")
    val transactionId: String?,

    val ruleId: String?,

    @field:NotBlank(message = "analystDecision é obrigatório")
    val analystDecision: String?
)
