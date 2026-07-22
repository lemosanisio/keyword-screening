package br.com.decision.domain.event

import br.com.decision.domain.model.RuleEvaluationOutcome
import br.com.evaluation.domain.TransactionEvaluation
import br.com.shared.domain.DomainEvent
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import java.time.Instant

/**
 * Evento publicado após uma avaliação multi-regra (ruleset congelado).
 * Carrega a avaliação agregada e o resultado por regra para que a outbox
 * emita um completion v2, um sinal por regra acionada e no máximo um pedido de revisão.
 */
data class RuleSetEvaluatedEvent(
    override val eventId: EventId,
    override val traceId: TraceId,
    override val timestamp: Instant,
    val evaluation: TransactionEvaluation,
    val ruleOutcomes: List<RuleEvaluationOutcome>,
    val correlationId: String,
    val causationId: String?,
    val executionTimeMs: Long,
) : DomainEvent
