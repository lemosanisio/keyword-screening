package br.com.alert.domain.model

import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.model.vo.AlertId
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import java.time.Instant

/**
 * Aggregate Root do Alert Context.
 * Representa um alerta gerado pelo Decision Engine após decisão GENERATE_ALERT.
 */
data class Alert(
    val id: AlertId,
    val transactionId: TransactionId,
    val ruleId: RuleId,
    val customerId: CustomerId,
    val facts: Map<String, Any?>?,
    val configurationVersion: Int?,
    val traceId: TraceId?,
    val actions: List<String>?,
    val explanation: Map<String, Any?>?,
    val status: AlertStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /**
     * Transiciona o alerta para um novo status, validando a state machine.
     * @throws IllegalArgumentException se a transição for inválida
     */
    fun transitionTo(newStatus: AlertStatus): Alert {
        require(status.canTransitionTo(newStatus)) {
            "Transição inválida: $status → $newStatus"
        }
        return copy(status = newStatus, updatedAt = Instant.now())
    }
}
