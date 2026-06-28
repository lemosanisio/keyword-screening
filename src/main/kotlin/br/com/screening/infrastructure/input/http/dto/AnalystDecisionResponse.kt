package br.com.screening.infrastructure.input.http.dto

data class AnalystDecisionResponse(
    val transactionId: String,
    val ruleId: String,
    val analystDecision: String,
    val registeredAt: String
)
