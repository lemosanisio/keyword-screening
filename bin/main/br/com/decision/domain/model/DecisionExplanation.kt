package br.com.decision.domain.model

import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import java.time.Instant
import java.util.UUID

data class DecisionExplanation(
    val traceId: TraceId,
    val steps: List<ExplanationStep>
)

sealed interface ExplanationStep {
    val stepNumber: Int
    val stepName: String
    val timestamp: Instant
}

data class ReceptionStep(
    override val stepNumber: Int = 1,
    override val stepName: String = "RECEPTION",
    override val timestamp: Instant,
    val transactionId: TransactionId,
    val customerId: CustomerId,
    val ruleCode: RuleCode,
    val eventPayload: Map<String, Any>
) : ExplanationStep

data class RuleIdentificationStep(
    override val stepNumber: Int = 2,
    override val stepName: String = "RULE_IDENTIFICATION",
    override val timestamp: Instant,
    val ruleDefinition: RuleCode,
    val ruleName: String,
    val configurationVersion: ConfigurationVersion,
    val expressions: List<Expression>,
    val actions: List<Action>
) : ExplanationStep

data class ContextBuildingStep(
    override val stepNumber: Int = 3,
    override val stepName: String = "CONTEXT_BUILDING",
    override val timestamp: Instant,
    val resolverResults: List<ResolverResult>
) : ExplanationStep

data class ResolverResult(
    val resolverName: String,
    val entity: String,
    val sourceSystem: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    val durationMs: Long,
    val result: ResolverOutcome
)

sealed interface ResolverOutcome {
    data class Success(val factName: FactName, val value: FactValue) : ResolverOutcome
    data class Failure(val factName: FactName, val error: String, val reason: String) : ResolverOutcome
}

data class EvaluationStep(
    override val stepNumber: Int = 4,
    override val stepName: String = "EVALUATION",
    override val timestamp: Instant,
    val evaluations: List<ExpressionEvaluation>
) : ExplanationStep

data class DecisionStep(
    override val stepNumber: Int = 5,
    override val stepName: String = "DECISION",
    override val timestamp: Instant,
    val decision: Decision,
    val actions: List<Action>,
    val justification: String
) : ExplanationStep

data class PersistenceStep(
    override val stepNumber: Int = 6,
    override val stepName: String = "PERSISTENCE",
    override val timestamp: Instant,
    val executionId: UUID
) : ExplanationStep

data class PublicationStep(
    override val stepNumber: Int = 7,
    override val stepName: String = "PUBLICATION",
    override val timestamp: Instant,
    val eventId: EventId
) : ExplanationStep
