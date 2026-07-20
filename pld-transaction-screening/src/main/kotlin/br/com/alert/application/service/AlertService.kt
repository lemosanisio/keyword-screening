package br.com.alert.application.service

import br.com.alert.application.usecase.CreateAlertCommand
import br.com.alert.application.usecase.CreateAlertUseCase
import br.com.alert.application.usecase.UpdateAlertStatusUseCase
import br.com.alert.domain.exception.AlertNotFoundException
import br.com.alert.domain.exception.InvalidAlertTransitionException
import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.port.AlertRepository
import br.com.alert.domain.model.vo.AlertId
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.model.vo.FactValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class AlertService(
    private val alertRepository: AlertRepository
) : CreateAlertUseCase, UpdateAlertStatusUseCase {

    private val log = LoggerFactory.getLogger(AlertService::class.java)

    /**
     * Cria alerta a partir de um DecisionMadeEvent (usado pelo listener).
     * Idempotente por (transactionId, ruleId).
     */
    override fun createAlertIfNotExists(event: DecisionMadeEvent): Alert? {
        val command = CreateAlertCommand(
            transactionId = event.transactionId,
            ruleId = event.ruleId,
            customerId = event.customerId,
            facts = event.facts.entries.associate { (k, v) -> k.value to factValueToSerializable(v) },
            configurationVersion = event.configurationVersion.value,
            traceId = event.traceId,
            actions = event.actions.map { it.name },
            explanation = mapOf(
                "traceId" to event.explanation.traceId.value,
                "decision" to event.decision.name,
                "matchedExpressions" to event.matchedExpressions.map { eval ->
                    mapOf(
                        "factName" to eval.factName.value,
                        "operator" to eval.operator.name,
                        "satisfied" to eval.satisfied,
                        "justification" to eval.justification
                    )
                }
            )
        )
        return createIfNotExists(command)
    }

    /**
     * Cria alerta a partir de um comando.
     * Idempotente por (transactionId, ruleId).
     */
    fun createIfNotExists(command: CreateAlertCommand): Alert {
        // Idempotência: verifica se já existe alerta para (transactionId, ruleId)
        val existing = alertRepository.findByTransactionIdAndRuleId(
            command.transactionId,
            command.ruleId
        )
        if (existing != null) {
            log.debug(
                "Alerta já existe para transactionId={}, ruleId={}",
                command.transactionId.value, command.ruleId.value
            )
            return existing
        }

        val now = Instant.now()
        val alert = Alert(
            id = AlertId(UUID.randomUUID()),
            transactionId = command.transactionId,
            ruleId = command.ruleId,
            customerId = command.customerId,
            facts = command.facts,
            configurationVersion = command.configurationVersion,
            traceId = command.traceId,
            actions = command.actions,
            explanation = command.explanation,
            status = AlertStatus.OPEN,
            createdAt = now,
            updatedAt = now
        )

        val saved = alertRepository.save(alert)
        log.info(
            "Alerta criado: id={}, transactionId={}, ruleId={}",
            saved.id.value, saved.transactionId.value, saved.ruleId.value
        )
        return saved
    }

    override fun updateStatus(id: AlertId, newStatus: AlertStatus): Alert {
        val alert = alertRepository.findById(id)
            ?: throw AlertNotFoundException("Alerta não encontrado: ${id.value}")

        if (!alert.status.canTransitionTo(newStatus)) {
            throw InvalidAlertTransitionException(
                "Transição inválida: ${alert.status} → $newStatus"
            )
        }

        val updated = alert.transitionTo(newStatus)
        return alertRepository.save(updated)
    }

    private fun factValueToSerializable(value: FactValue): Any? {
        return when (value) {
            is FactValue.BooleanValue -> value.value
            is FactValue.EnumValue -> value.value
            is FactValue.NumberValue -> value.value
            is FactValue.StringValue -> value.value
            is FactValue.MoneyValue -> mapOf("amount" to value.amount, "currency" to value.currency)
        }
    }
}
