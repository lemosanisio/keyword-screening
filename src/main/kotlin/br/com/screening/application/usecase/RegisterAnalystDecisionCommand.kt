package br.com.screening.application.usecase

import br.com.shared.domain.valueobject.TransactionId

data class RegisterAnalystDecisionCommand(
    val transactionId: TransactionId,
    val ruleId: String,
    val analystDecision: String
)
