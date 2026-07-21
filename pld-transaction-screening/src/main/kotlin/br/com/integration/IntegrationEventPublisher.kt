package br.com.integration

import java.time.Instant

fun interface IntegrationEventPublisher {
    fun publish(eventId: String, eventType: String, envelope: String, publishedAt: Instant)
}
