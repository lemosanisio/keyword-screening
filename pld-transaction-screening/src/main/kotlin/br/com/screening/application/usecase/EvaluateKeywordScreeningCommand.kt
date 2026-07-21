package br.com.screening.application.usecase

import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId

data class EvaluateKeywordScreeningCommand(
    val transactionId: TransactionId,
    val customerId: CustomerId,
    val description: String,
    val correlationId: String? = null,
)
