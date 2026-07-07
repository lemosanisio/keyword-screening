package br.com.decision.application.usecase

import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.enums.Action

data class UpdateRuleConfigurationCommand(
    val expressions: List<Expression>,
    val actions: List<Action>,
    val updatedBy: String
)
