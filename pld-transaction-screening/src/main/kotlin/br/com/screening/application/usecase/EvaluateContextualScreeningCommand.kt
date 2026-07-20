package br.com.screening.application.usecase

import br.com.shared.domain.valueobject.TransactionId

data class EvaluateContextualScreeningCommand(
    val transactionId: TransactionId,
    val ruleId: String,
    val description: String,
    val matchedKeyword: String
)
