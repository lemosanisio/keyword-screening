package br.com.screening.application.usecase

data class RegisterAnalystDecisionCommand(
    val transactionId: String,
    val ruleId: String,
    val analystDecision: String
)
