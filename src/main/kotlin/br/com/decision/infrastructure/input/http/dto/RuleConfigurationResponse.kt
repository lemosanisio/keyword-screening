package br.com.decision.infrastructure.input.http.dto

import java.time.Instant
import java.util.UUID

/**
 * DTO de response para Rule Configuration.
 * Usado em todas as operações que retornam uma configuração.
 */
data class RuleConfigurationResponse(
    val id: UUID,
    val ruleCode: String,
    val version: Int,
    val active: Boolean,
    val draft: Boolean,
    val expressions: List<ConditionDto>,
    val actions: List<String>,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * DTO para um entry de versão no histórico de configurações.
 */
data class ConfigurationVersionResponse(
    val version: Int,
    val expressions: List<ConditionDto>,
    val actions: List<String>,
    val active: Boolean,
    val createdBy: String,
    val createdAt: Instant
)
