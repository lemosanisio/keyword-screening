package br.com.shared.domain

import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import java.time.Instant

/**
 * Interface base para todos os eventos de domínio publicados entre bounded contexts.
 * Puro domínio — sem dependência de framework.
 */
interface DomainEvent {
    val eventId: EventId
    val traceId: TraceId
    val timestamp: Instant
}
