package br.com.evaluation.domain

import br.com.decision.domain.model.EvaluationOutcome
import br.com.decision.domain.model.EvaluationStatus
import br.com.decision.domain.model.FactResult
import br.com.decision.domain.model.FailureStage
import br.com.decision.domain.model.RecommendedRoute
import java.time.Instant
import java.util.UUID

data class RuleEvaluationReference(
    val ruleCode: String,
    val ruleVersion: Int,
    val explanationCode: String? = null,
)

data class RiskContext(
    val source: String,
    val quality: String,
    val reasonCode: String? = null,
    val riskProfileVersion: Int? = null,
)

/** Vínculo entre a avaliação e uma execução de regra persistida. */
data class ExecutionLink(
    val decisionExecutionId: UUID,
    val ruleCode: String,
)

data class TransactionEvaluation(
    val evaluationId: String,
    val decisionExecutionId: UUID,
    val transactionId: String,
    val sourceSystem: String,
    val externalTransactionId: String,
    val transactionVersion: Int,
    val purpose: String,
    val evaluationRequestId: String?,
    val inputEventId: String,
    val inputEventSchemaVersion: Int,
    val snapshot: Map<String, Any?>,
    val snapshotRef: String,
    val snapshotFormatVersion: String,
    val snapshotHash: String,
    val rulesetVersion: String,
    val riskContext: RiskContext,
    val facts: List<FactResult>,
    val rulesExecuted: List<RuleEvaluationReference>,
    val rulesTriggered: List<RuleEvaluationReference>,
    val executionStatus: EvaluationStatus,
    val evaluationOutcome: EvaluationOutcome?,
    val reviewRequired: Boolean?,
    val recommendedRoute: RecommendedRoute?,
    val explanation: List<Map<String, String>>,
    val partyId: String?,
    val correlationId: String,
    val causationId: String?,
    val evaluatedAt: Instant,
    val failureStage: FailureStage? = null,
    val failureCode: String? = null,
    val executions: List<ExecutionLink> = listOf(ExecutionLink(decisionExecutionId, "")),
) {
    init {
        require(executions.isNotEmpty() && executions.any { it.decisionExecutionId == decisionExecutionId }) {
            "executions deve conter a execução principal"
        }
        if (executionStatus == EvaluationStatus.FAILED) {
            require(failureStage != null && !failureCode.isNullOrBlank()) {
                "failureStage e failureCode são obrigatórios para avaliação FAILED"
            }
            require(evaluationOutcome == null && reviewRequired == null && recommendedRoute == null) {
                "avaliação FAILED não possui outcome nem roteamento"
            }
        } else {
            require(failureStage == null && failureCode == null) {
                "failureStage/failureCode só existem em avaliação FAILED"
            }
        }
    }
}
