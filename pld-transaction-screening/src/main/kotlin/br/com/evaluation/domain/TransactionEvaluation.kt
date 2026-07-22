package br.com.evaluation.domain

import br.com.decision.domain.model.EvaluationOutcome
import br.com.decision.domain.model.EvaluationStatus
import br.com.decision.domain.model.FactResult
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
)
