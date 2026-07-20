package br.com.alert.infrastructure.input.http.dto

import java.time.Instant
import java.util.UUID

data class AlertResponse(
    val id: UUID,
    val transactionId: String,
    val ruleId: UUID,
    val customerId: String,
    val facts: Map<String, Any?>?,
    val configurationVersion: Int?,
    val traceId: String?,
    val actions: List<String>?,
    val explanation: Map<String, Any?>?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
