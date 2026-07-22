package br.com.evaluation.infrastructure

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class TransactionEvaluationLock(private val jdbcTemplate: JdbcTemplate) {
    fun acquire(sourceSystem: String, externalTransactionId: String, purpose: String) {
        jdbcTemplate.queryForObject(
            "SELECT pg_advisory_xact_lock(hashtext(?)) IS NULL",
            Boolean::class.java,
            "$sourceSystem:$externalTransactionId:$purpose",
        )
    }
}
