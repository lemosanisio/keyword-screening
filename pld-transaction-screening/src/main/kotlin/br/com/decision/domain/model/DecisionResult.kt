package br.com.decision.domain.model

import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue

data class DecisionResult(
    val decision: Decision,
    val actions: List<Action>,
    val matchedExpressions: List<ExpressionEvaluation>,
    val failedExpressions: List<ExpressionEvaluation>,
    val executionTimeMs: Long,
    val configurationVersion: ConfigurationVersion,
    val facts: Map<FactName, FactValue>,
    val explanation: DecisionExplanation? = null,
    val factResults: List<FactResult> = facts.map { (name, value) ->
        FactResult(name, FactQuality.PRESENT, value, "DECISION_CONTEXT")
    },
    val evaluationStatus: EvaluationStatus = EvaluationStatus.COMPLETED,
    val evaluationOutcome: EvaluationOutcome = if (Action.GENERATE_ALERT in actions) {
        EvaluationOutcome.SIGNAL_RAISED
    } else {
        EvaluationOutcome.NO_SIGNAL
    },
    val reviewRequired: Boolean = Action.REVIEW in actions,
    val recommendedRoute: RecommendedRoute? = if (reviewRequired) RecommendedRoute.DERIVED_TO_ANALYST else null,
)
