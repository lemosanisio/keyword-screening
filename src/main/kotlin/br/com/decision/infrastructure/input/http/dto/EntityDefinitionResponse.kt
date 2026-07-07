package br.com.decision.infrastructure.input.http.dto

data class EntityDefinitionResponse(
    val id: String,
    val name: String,
    val displayName: String,
    val sourceSystem: String,
    val factNames: List<String>
)
