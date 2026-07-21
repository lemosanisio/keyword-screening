package br.com.decision.infrastructure.input.event

import br.com.decision.application.usecase.ExecuteDecisionCommand
import br.com.decision.application.usecase.ExecuteDecisionUseCase
import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.port.RuleDefinitionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Input adapter: consome DetectionEvent publicado pelo Screening Context
 * e invoca o use case de decisão após validação dos campos.
 */
@Component
class DetectionEventListener(
    private val executeDecisionUseCase: ExecuteDecisionUseCase,
    private val ruleDefinitionRepository: RuleDefinitionRepository
) {

    private val logger = LoggerFactory.getLogger(DetectionEventListener::class.java)

    @EventListener
    fun handle(event: DetectionEvent) {
        // Validate ruleCode (business rule — VO can't enforce catalog existence)
        if (event.ruleCode.value.isBlank()) {
            logger.warn(
                "DetectionEvent descartado — ruleCode é obrigatório | eventId={}, transactionId={}, customerId={}",
                event.eventId.value,
                event.transactionId.value,
                event.customerId.value
            )
            return
        }

        if (ruleDefinitionRepository.findByCode(event.ruleCode) == null) {
            logger.warn(
                "DetectionEvent descartado — ruleCode '{}' não encontrado no Rule Catalog | eventId={}, transactionId={}, customerId={}",
                event.ruleCode.value,
                event.eventId.value,
                event.transactionId.value,
                event.customerId.value
            )
            return
        }

        val command = ExecuteDecisionCommand(
            transactionId = event.transactionId,
            customerId = event.customerId,
            ruleCode = event.ruleCode,
            detectionResult = event.detectionResult,
            correlationId = event.traceId.value,
            causationId = event.eventId.value,
        )

        executeDecisionUseCase.execute(command)
    }
}
