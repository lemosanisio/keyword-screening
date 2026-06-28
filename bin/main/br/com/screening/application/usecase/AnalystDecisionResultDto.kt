package br.com.screening.application.usecase

data class AnalystDecisionResultDto(
    val transactionId: String,
    val ruleId: String,
    val analystDecision: String,
    val registeredAt: String
)
