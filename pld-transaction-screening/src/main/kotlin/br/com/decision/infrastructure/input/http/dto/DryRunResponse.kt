package br.com.decision.infrastructure.input.http.dto

import br.com.decision.application.usecase.DryRunResult
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.vo.FactValue

/**
 * Response do dry-run contendo o resultado completo da avaliação.
 */
data class DryRunResponse(
    val decision: String,
    val actions: List<String>,
    val matchedExpressions: List<ExpressionEvaluationResponse>,
    val failedExpressions: List<ExpressionEvaluationResponse>,
    val configurationVersion: Int
) {
    companion object {
        fun from(result: DryRunResult): DryRunResponse {
            return DryRunResponse(
                decision = result.decision.name,
                actions = result.actions.map { it.name },
                matchedExpressions = result.matchedExpressions.map { it.toResponse() },
                failedExpressions = result.failedExpressions.map { it.toResponse() },
                configurationVersion = result.configurationVersion.value
            )
        }

        private fun ExpressionEvaluation.toResponse(): ExpressionEvaluationResponse {
            return ExpressionEvaluationResponse(
                factName = factName.value,
                operator = operator.name,
                expectedValue = expectedValue.toSerializable(),
                actualValue = actualValue?.toSerializable(),
                satisfied = satisfied,
                justification = justification
            )
        }

        private fun FactValue.toSerializable(): Any = when (this) {
            is FactValue.BooleanValue -> value
            is FactValue.EnumValue -> value
            is FactValue.NumberValue -> value
            is FactValue.StringValue -> value
            is FactValue.MoneyValue -> mapOf("amount" to amount, "currency" to currency)
        }
    }
}
