package br.com.decision.application.usecase

import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion

data class DryRunResult(
    val decision: Decision,
    val actions: List<Action>,
    val matchedExpressions: List<ExpressionEvaluation>,
    val failedExpressions: List<ExpressionEvaluation>,
    val configurationVersion: ConfigurationVersion
)
