package br.com.alert.application.usecase

import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId

/**
 * Command para criação de alertas.
 * Utilizado pelo AlertService para criar alertas de forma independente do evento.
 */
data class CreateAlertCommand(
    val transactionId: TransactionId,
    val ruleId: RuleId,
    val customerId: CustomerId,
    val facts: Map<String, Any?>?,
    val configurationVersion: Int?,
    val traceId: TraceId?,
    val actions: List<String>?,
    val explanation: Map<String, Any?>?
)
