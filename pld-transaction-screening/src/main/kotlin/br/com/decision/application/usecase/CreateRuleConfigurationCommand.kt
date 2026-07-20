package br.com.decision.application.usecase

import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.vo.RuleCode

data class CreateRuleConfigurationCommand(
    val ruleCode: RuleCode,
    val expressions: List<Expression>,
    val actions: List<Action>,
    val createdBy: String
)
