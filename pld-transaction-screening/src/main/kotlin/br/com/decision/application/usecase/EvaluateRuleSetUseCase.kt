package br.com.decision.application.usecase

import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.EvaluationOutcome
import br.com.decision.domain.model.EvaluationStatus
import br.com.decision.domain.model.RecommendedRoute
import br.com.decision.domain.model.RuleEvaluationOutcome
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId

/**
 * Entrada para avaliação multi-regra: a mesma transação/contexto é avaliada
 * contra todas as regras ativas com configuração ativa (ruleset congelado).
 */
data class EvaluateRuleSetCommand(
    val transactionId: TransactionId,
    val customerId: CustomerId,
    val detectionResult: DetectionResult,
    val sourceSystem: String = "LEGACY_HTTP",
    val purpose: String = "LIVE",
    val inputEventId: String? = null,
    val inputEventSchemaVersion: Int = 1,
    val transactionVersion: Int = 1,
    val transactionSnapshot: Map<String, Any?> = emptyMap(),
    val evaluationRequestId: String? = null,
    val correlationId: String? = null,
    val causationId: String? = null,
)

data class RuleSetEvaluationResult(
    val evaluationId: String?,
    val executionStatus: EvaluationStatus?,
    val evaluationOutcome: EvaluationOutcome?,
    val reviewRequired: Boolean?,
    val recommendedRoute: RecommendedRoute?,
    val rulesetVersion: String?,
    val ruleOutcomes: List<RuleEvaluationOutcome>,
)

interface EvaluateRuleSetUseCase {
    fun execute(command: EvaluateRuleSetCommand): RuleSetEvaluationResult
}
