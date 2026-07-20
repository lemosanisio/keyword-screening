package br.com.screening.domain.model

import br.com.shared.domain.valueobject.TransactionId
import java.time.Instant

data class RuleExecution(
    val id: Long? = null,
    val transactionId: TransactionId,
    val ruleCode: String,
    val result: ScreeningResult,
    val createdAt: Instant
) {
    /** Chave de idempotência: KEYWORD_SCREENING:{transactionId} */
    val idempotencyKey: String get() = "$ruleCode:${transactionId.value}"
}
