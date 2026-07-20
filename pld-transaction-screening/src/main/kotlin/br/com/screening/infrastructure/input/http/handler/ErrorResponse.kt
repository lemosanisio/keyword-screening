package br.com.screening.infrastructure.input.http.handler

import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String
)
