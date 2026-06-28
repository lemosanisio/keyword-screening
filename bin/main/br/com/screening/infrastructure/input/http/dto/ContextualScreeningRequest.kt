package br.com.screening.infrastructure.input.http.dto

import jakarta.validation.constraints.NotBlank

data class ContextualScreeningRequest(
    @field:NotBlank(message = "transactionId é obrigatório")
    val transactionId: String?,

    val ruleId: String?,

    @field:NotBlank(message = "description é obrigatória")
    val description: String?,

    @field:NotBlank(message = "matchedKeyword é obrigatório")
    val matchedKeyword: String?
)
