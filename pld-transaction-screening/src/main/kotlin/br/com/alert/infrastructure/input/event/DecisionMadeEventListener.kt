package br.com.alert.infrastructure.input.event

import br.com.alert.application.usecase.CreateAlertUseCase
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.model.enums.Action
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Listener que reage a eventos DecisionMadeEvent publicados pelo Decision Context.
 * Usa AFTER_COMMIT para garantir que a decisão já está persistida antes de criar o alerta.
 */
@Component
class DecisionMadeEventListener(
    private val createAlertUseCase: CreateAlertUseCase
) {

    private val log = LoggerFactory.getLogger(DecisionMadeEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handle(event: DecisionMadeEvent) {
        if (Action.GENERATE_ALERT in event.actions) {
            log.info(
                "Processando DecisionMadeEvent GENERATE_ALERT: transactionId={}, ruleId={}, traceId={}",
                event.transactionId.value, event.ruleId.value, event.traceId.value
            )
            createAlertUseCase.createAlertIfNotExists(event)
        } else {
            log.debug(
                "Decision IGNORE para transactionId={}, traceId={}. Nenhum alerta criado.",
                event.transactionId.value, event.traceId.value
            )
        }
    }
}
