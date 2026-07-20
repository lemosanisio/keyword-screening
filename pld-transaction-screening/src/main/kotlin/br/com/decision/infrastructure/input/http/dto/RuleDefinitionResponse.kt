package br.com.decision.infrastructure.input.http.dto

import java.time.Instant

data class RuleDefinitionResponse(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val context: String,
    val category: String,
    val supportedFacts: List<String>,
    val supportedActions: List<String>,
    val status: String,
    val createdAt: Instant
)
