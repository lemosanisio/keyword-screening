package br.com.alert.infrastructure.input.event

import br.com.alert.application.usecase.CreateAlertUseCase
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.model.enums.Action
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Listener que reage a eventos DecisionMadeEvent publicados pelo Decision Context.
 * Usa AFTER_COMMIT para garantir que a decisão já está persistida antes de criar o alerta.
 *
 * Quando evaluation.case-trigger-mode=MANUAL_REVIEW_LIVE, o Alert legado NÃO é criado —
 * o gatilho de caso passa a ser exclusivamente ManualReviewRequested.v2.
 */
@Component
class DecisionMadeEventListener(
    private val createAlertUseCase: CreateAlertUseCase,
    @Value("\${evaluation.case-trigger-mode:MANUAL_REVIEW_LIVE}")
    private val caseTriggerMode: String,
) {

    private val log = LoggerFactory.getLogger(DecisionMadeEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handle(event: DecisionMadeEvent) {
        if (event.evaluation?.purpose?.let { it != "LIVE" } == true) return

        if (caseTriggerMode == "MANUAL_REVIEW_LIVE") {
            log.debug(
                "case-trigger-mode=MANUAL_REVIEW_LIVE: Alert legado suprimido para transactionId={}, traceId={}",
                event.transactionId.value, event.traceId.value,
            )
            return
        }

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
