package br.com.decision.domain.event

import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.DomainEvent
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import br.com.evaluation.domain.TransactionEvaluation
import java.time.Instant

/**
 * Evento publicado pelo Decision Context após uma decisão ser tomada.
 * Auto-contido: carrega todos os dados necessários para que consumidores
 * (ex.: Alert Context) possam reagir sem consultar o Decision Context.
 */
data class DecisionMadeEvent(
    override val eventId: EventId,
    override val traceId: TraceId,
    override val timestamp: Instant,
    val transactionId: TransactionId,
    val customerId: CustomerId,
    val ruleId: RuleId,
    val ruleCode: RuleCode,
    val decision: Decision,
    val actions: List<Action>,
    val facts: Map<FactName, FactValue>,
    val matchedExpressions: List<ExpressionEvaluation>,
    val configurationVersion: ConfigurationVersion,
    val executionTimeMs: Long,
    val explanation: DecisionExplanation,
    val evaluationId: String? = null,
    val correlationId: String? = null,
    val causationId: String? = null,
    val evaluation: TransactionEvaluation? = null,
) : DomainEvent
