package br.com.decision.domain.model

import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.RuleId
import java.time.Instant
import java.util.UUID

data class RuleConfiguration(
    val id: UUID,
    val ruleId: RuleId,
    val expressions: List<Expression>,
    val actions: List<Action>,
    val active: Boolean,
    val draft: Boolean,
    val currentVersion: ConfigurationVersion,
    val versions: List<ConfigurationVersionEntry>,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(expressions.size <= 10) { "Máximo 10 expressions por configuração" }
    }
}

data class ConfigurationVersionEntry(
    val version: ConfigurationVersion,
    val expressions: List<Expression>,
    val actions: List<Action>,
    val active: Boolean,
    val createdBy: String,
    val createdAt: Instant
)
