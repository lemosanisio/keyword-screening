package br.com.decision.domain.event

import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.DomainEvent
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import java.time.Instant

/**
 * Evento publicado pelo Screening Context indicando que uma regra detectou uma condição.
 * Consumido pelo Decision Context para iniciar o fluxo de decisão.
 */
data class DetectionEvent(
    override val eventId: EventId,
    override val traceId: TraceId,
    override val timestamp: Instant,
    val transactionId: TransactionId,
    val customerId: CustomerId,
    val ruleCode: RuleCode,
    val detectionResult: DetectionResult
) : DomainEvent

/**
 * Resultado da detecção contendo flag de match e lista de matches encontrados.
 */
data class DetectionResult(
    val matched: Boolean,
    val matches: List<DetectionMatch>
)

/**
 * Match individual encontrado durante a detecção (termo + categoria).
 */
data class DetectionMatch(
    val term: String,
    val category: String
)
