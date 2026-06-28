package br.com.screening.application.usecase

data class EvaluateKeywordScreeningCommand(
    val transactionId: String,
    val description: String
)
