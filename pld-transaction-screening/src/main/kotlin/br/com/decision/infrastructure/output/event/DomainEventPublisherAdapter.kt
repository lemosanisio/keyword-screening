package br.com.decision.infrastructure.output.event

import br.com.shared.domain.DomainEvent
import br.com.shared.domain.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class DomainEventPublisherAdapter(
    private val applicationEventPublisher: ApplicationEventPublisher
) : DomainEventPublisher {

    private val logger = LoggerFactory.getLogger(DomainEventPublisherAdapter::class.java)

    override fun publish(event: DomainEvent) {
        logger.debug(
            "Publicando evento: {} (eventId={}, traceId={})",
            event::class.simpleName,
            event.eventId,
            event.traceId
        )
        applicationEventPublisher.publishEvent(event)
    }
}
