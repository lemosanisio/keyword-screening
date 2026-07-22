package br.com.decision.infrastructure.input.event

import br.com.decision.application.usecase.ExecuteDecisionCommand
import br.com.decision.application.usecase.ExecuteDecisionUseCase
import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.port.RuleDefinitionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.security.MessageDigest

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
            inputEventId = event.inputEventId ?: stableInputEventId(event.eventId.value),
            inputEventSchemaVersion = event.inputEventSchemaVersion,
            transactionVersion = event.transactionVersion,
            purpose = event.purpose,
            sourceSystem = event.sourceSystem,
            transactionSnapshot = event.transactionSnapshot,
            evaluationRequestId = event.evaluationRequestId,
        )

        executeDecisionUseCase.execute(command)
    }

    companion object {
        private val INPUT_EVENT_ID_REGEX = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
        private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        private fun stableInputEventId(value: String): String {
            if (INPUT_EVENT_ID_REGEX.matches(value)) return value
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            val output = CharArray(26)
            var buffer = 0
            var bits = 0
            var byteIndex = 0
            for (index in output.indices) {
                while (bits < 5) {
                    buffer = (buffer shl 8) or (digest[byteIndex++].toInt() and 0xff)
                    bits += 8
                }
                bits -= 5
                output[index] = CROCKFORD[(buffer shr bits) and 31]
            }
            output[0] = CROCKFORD[(digest[0].toInt() ushr 5) and 7]
            return String(output)
        }
    }
}
