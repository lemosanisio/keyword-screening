package br.com.screening.domain.model

import java.time.Instant

data class RuleExecution(
    val id: Long? = null,
    val transactionId: String,
    val ruleCode: String,
    val result: ScreeningResult,
    val createdAt: Instant
) {
    /** Chave de idempotência: KEYWORD_SCREENING:{transactionId} */
    val idempotencyKey: String get() = "$ruleCode:$transactionId"
}
