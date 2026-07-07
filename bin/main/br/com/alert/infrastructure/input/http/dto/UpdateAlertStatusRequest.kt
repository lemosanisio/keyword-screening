package br.com.alert.infrastructure.input.http.dto

import jakarta.validation.constraints.NotBlank

data class UpdateAlertStatusRequest(
    @field:NotBlank(message = "Status é obrigatório")
    val status: String
)
