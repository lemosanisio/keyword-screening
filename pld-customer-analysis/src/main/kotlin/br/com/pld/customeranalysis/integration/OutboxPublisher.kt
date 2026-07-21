package br.com.pld.customeranalysis.integration

import java.time.Instant

data class OutboxMessage(
    val id: String,
    val eventType: String,
    val eventVersion: Int,
    val aggregateType: String,
    val aggregateId: String,
    val payload: String,
    val occurredAt: Instant,
)

fun interface OutboxPublisher {
    fun publish(message: OutboxMessage)
}
