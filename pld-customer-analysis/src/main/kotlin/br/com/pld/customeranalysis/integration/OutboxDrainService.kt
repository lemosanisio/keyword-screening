package br.com.pld.customeranalysis.integration

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Service
class OutboxDrainService(
    private val jdbcTemplate: JdbcTemplate,
    private val publisherProvider: ObjectProvider<OutboxPublisher>,
    private val meterRegistry: MeterRegistry,
) {
    @Transactional
    fun publishPending(limit: Int): Int {
        val publisher = publisherProvider.getIfAvailable() ?: return 0
        val messages = pendingMessages(limit)

        messages.forEach { message ->
            publisher.publish(message)
            markProcessed(message.id, Instant.now())
            meterRegistry.counter("pld.outbox.published", "eventType", message.eventType).increment()
        }

        return messages.size
    }

    private fun pendingMessages(limit: Int): List<OutboxMessage> = jdbcTemplate.query(
        """
        select
            id,
            event_type,
            event_version,
            aggregate_type,
            aggregate_id,
            payload::text as payload,
            occurred_at
        from outbox_event
        where status = ?
        order by occurred_at, id
        limit ?
        """.trimIndent(),
        { rs, _ ->
            OutboxMessage(
                id = rs.getString("id"),
                eventType = rs.getString("event_type"),
                eventVersion = rs.getInt("event_version"),
                aggregateType = rs.getString("aggregate_type"),
                aggregateId = rs.getString("aggregate_id"),
                payload = rs.getString("payload"),
                occurredAt = rs.getTimestamp("occurred_at").toInstant(),
            )
        },
        IntegrationRecordStatus.PENDING.name,
        limit,
    )

    private fun markProcessed(eventId: String, publishedAt: Instant) {
        jdbcTemplate.update(
            """
            update outbox_event
            set status = ?, published_at = ?
            where id = ? and status = ?
            """.trimIndent(),
            IntegrationRecordStatus.PROCESSED.name,
            Timestamp.from(publishedAt),
            eventId,
            IntegrationRecordStatus.PENDING.name,
        )
    }
}
