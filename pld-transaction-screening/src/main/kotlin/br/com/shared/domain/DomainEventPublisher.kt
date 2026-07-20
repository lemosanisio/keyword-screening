package br.com.shared.domain

/**
 * Port de publicação de eventos de domínio.
 * Adapters (infrastructure) implementam esta interface — o domínio nunca depende de Spring.
 */
interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
