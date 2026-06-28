package br.com.screening.infrastructure.input.http.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EvaluateKeywordScreeningRequest(
    @field:NotBlank(message = "transactionId é obrigatório")
    val transactionId: String?,

    @field:NotBlank(message = "description é obrigatória")
    @field:Size(max = 140, message = "description deve ter no máximo 140 caracteres")
    val description: String?
)
