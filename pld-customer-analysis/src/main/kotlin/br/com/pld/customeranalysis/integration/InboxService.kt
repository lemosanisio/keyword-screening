package br.com.pld.customeranalysis.integration

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

data class InboxMessage(
    val consumerName: String,
    val eventId: String,
    val eventType: String,
    val eventVersion: Int,
    val payload: String,
)

enum class InboxProcessingResult {
    PROCESSED,
    DUPLICATE,
}

@Service
class InboxService(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional
    fun processOnce(message: InboxMessage, effect: () -> Unit): InboxProcessingResult {
        val now = Instant.now()
        try {
            jdbcTemplate.update(
                """
                insert into inbox_event (
                    consumer_name,
                    event_id,
                    event_type,
                    event_version,
                    payload,
                    received_at,
                    status
                ) values (?, ?, ?, ?, ?::jsonb, ?, ?)
                """.trimIndent(),
                message.consumerName,
                message.eventId,
                message.eventType,
                message.eventVersion,
                message.payload,
                Timestamp.from(now),
                IntegrationRecordStatus.PENDING.name,
            )
        } catch (_: DuplicateKeyException) {
            return InboxProcessingResult.DUPLICATE
        }

        effect()
        mark(message, IntegrationRecordStatus.PROCESSED, Instant.now())

        return InboxProcessingResult.PROCESSED
    }

    private fun mark(message: InboxMessage, status: IntegrationRecordStatus, processedAt: Instant?) {
        jdbcTemplate.update(
            """
            update inbox_event
            set status = ?, processed_at = ?
            where consumer_name = ? and event_id = ?
            """.trimIndent(),
            status.name,
            processedAt?.let(Timestamp::from),
            message.consumerName,
            message.eventId,
        )
    }
}
