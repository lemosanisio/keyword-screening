package br.com.decision.infrastructure.output.event

import br.com.shared.domain.DomainEvent
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

class DomainEventPublisherAdapterTest {

    private val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val adapter = DomainEventPublisherAdapter(applicationEventPublisher)

    @Test
    fun `publish delegates to ApplicationEventPublisher`() {
        val event = TestDomainEvent(
            eventId = EventId("evt-123"),
            traceId = TraceId("trace-456"),
            timestamp = Instant.now()
        )

        adapter.publish(event)

        verify(exactly = 1) { applicationEventPublisher.publishEvent(event) }
    }

    @Test
    fun `publish passes event object unchanged`() {
        val event = TestDomainEvent(
            eventId = EventId("evt-789"),
            traceId = TraceId("trace-012"),
            timestamp = Instant.parse("2025-01-15T10:30:00Z")
        )

        adapter.publish(event)

        verify { applicationEventPublisher.publishEvent(refEq(event)) }
    }

    private data class TestDomainEvent(
        override val eventId: EventId,
        override val traceId: TraceId,
        override val timestamp: Instant
    ) : DomainEvent
}
