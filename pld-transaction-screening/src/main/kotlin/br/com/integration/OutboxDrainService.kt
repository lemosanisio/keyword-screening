package br.com.integration

import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Service
class OutboxDrainService(
    private val jdbcTemplate: JdbcTemplate,
    private val publisherProvider: ObjectProvider<IntegrationEventPublisher>,
) {
    @Transactional
    fun publishPending(limit: Int): Int {
        val publisher = publisherProvider.getIfAvailable() ?: return 0
        if (!acquireDrainLock()) return 0
        val messages = pendingMessages(limit)
        var published = 0

        messages.forEach { message ->
            val publishedAt = Instant.now()
            try {
                publisher.publish(message.eventId, message.eventType, message.envelope, publishedAt)
                markPublished(message.eventId, publishedAt)
                published++
            } catch (exception: RuntimeException) {
                markFailed(message.eventId, message.attemptCount + 1, exception.message)
            }
        }
        return published
    }

    private fun acquireDrainLock(): Boolean = jdbcTemplate.queryForObject(
        "select pg_try_advisory_xact_lock(?)",
        Boolean::class.java,
        OUTBOX_DRAIN_LOCK,
    ) == true

    private fun pendingMessages(limit: Int): List<PendingOutbox> = jdbcTemplate.query(
        """
        select event_id, event_type, envelope::text, attempt_count
        from integration_outbox
        where status = 'PENDING' and next_attempt_at <= now()
        order by occurred_at, event_id
        limit ?
        """.trimIndent(),
        { rs, _ ->
            PendingOutbox(
                eventId = rs.getString("event_id"),
                eventType = rs.getString("event_type"),
                envelope = rs.getString("envelope"),
                attemptCount = rs.getInt("attempt_count"),
            )
        },
        limit,
    )

    private fun markPublished(eventId: String, publishedAt: Instant) {
        jdbcTemplate.update(
            """
            update integration_outbox
            set status = 'PUBLISHED', published_at = ?, last_error = null
            where event_id = ? and status = 'PENDING'
            """.trimIndent(),
            Timestamp.from(publishedAt),
            eventId,
        )
    }

    private fun markFailed(eventId: String, attemptCount: Int, error: String?) {
        val nextAttemptAt = Instant.now().plusSeconds((attemptCount.coerceAtMost(6) * 10).toLong())
        jdbcTemplate.update(
            """
            update integration_outbox
            set attempt_count = ?, next_attempt_at = ?, last_error = ?
            where event_id = ? and status = 'PENDING'
            """.trimIndent(),
            attemptCount,
            Timestamp.from(nextAttemptAt),
            error?.take(2000),
            eventId,
        )
    }

    companion object {
        private const val OUTBOX_DRAIN_LOCK = 7_106_2026L
    }
}

private data class PendingOutbox(
    val eventId: String,
    val eventType: String,
    val envelope: String,
    val attemptCount: Int,
)
