package br.com.decision.infrastructure.input.http.dto

data class FactDefinitionResponse(
    val id: String,
    val name: String,
    val displayName: String,
    val entity: String,
    val type: String,
    val context: String,
    val source: String,
    val supportedOperators: List<String>,
    val enabled: Boolean
)
