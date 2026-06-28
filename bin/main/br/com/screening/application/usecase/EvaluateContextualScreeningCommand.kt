package br.com.screening.application.usecase

data class EvaluateContextualScreeningCommand(
    val transactionId: String,
    val ruleId: String,
    val description: String,
    val matchedKeyword: String
)
