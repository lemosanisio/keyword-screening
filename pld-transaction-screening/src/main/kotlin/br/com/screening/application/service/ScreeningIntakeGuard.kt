package br.com.screening.application.service

import br.com.shared.domain.valueobject.PrefixedUlid
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant

@Component
class ScreeningIntakeGuard(private val jdbcTemplate: JdbcTemplate) {
    fun register(transactionId: String, customerId: String, description: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$customerId\u0000$description".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        jdbcTemplate.update(
            """
                INSERT INTO screening_intake (transaction_id, payload_hash, input_event_id, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (transaction_id) DO NOTHING
            """.trimIndent(),
            transactionId,
            hash,
            PrefixedUlid.ulid(),
            Timestamp.from(Instant.now()),
        )
        val persisted = jdbcTemplate.queryForMap(
            "SELECT payload_hash, input_event_id FROM screening_intake WHERE transaction_id = ?",
            transactionId,
        )
        require(persisted["payload_hash"] == hash) { "transactionId already exists with different payload" }
        return persisted["input_event_id"] as String
    }
}
