package br.com.evaluation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Valida se um intake possui identidade estável e snapshot válido antes de
 * permitir a criação de um TransactionEvaluation. Intakes inválidos são
 * enviados para quarentena com reason code e não geram avaliação nem evento.
 */
@Component
class IntakeValidator(
    private val quarantineRepository: ScreeningQuarantineJpaRepository,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(IntakeValidator::class.java)

    data class IntakeInput(
        val sourceSystem: String,
        val externalTransactionId: String,
        val transactionVersion: Int,
        val purpose: String,
        val snapshot: Map<String, Any?>,
        val correlationId: String?,
    )

    sealed class IntakeResult {
        data object Valid : IntakeResult()
        data class Quarantined(val reasonCode: String, val reasonDetail: String) : IntakeResult()
    }

    /**
     * Valida o intake e retorna Valid ou Quarantined.
     * Em caso de quarentena, persiste o registro automaticamente.
     */
    fun validate(input: IntakeInput): IntakeResult {
        val reasons = mutableListOf<String>()

        // 1. Identidade estável: sourceSystem e externalTransactionId não podem ser vazios
        if (input.sourceSystem.isBlank()) {
            reasons += "MISSING_SOURCE_SYSTEM"
        }
        if (input.externalTransactionId.isBlank()) {
            reasons += "MISSING_TRANSACTION_ID"
        }

        // 2. TransactionVersion deve ser positivo
        if (input.transactionVersion < 1) {
            reasons += "INVALID_TRANSACTION_VERSION"
        }

        // 3. Snapshot deve ser não-vazio para formar hash canônico
        if (input.snapshot.isEmpty()) {
            reasons += "EMPTY_SNAPSHOT"
        }

        if (reasons.isEmpty()) return IntakeResult.Valid

        val reasonCode = reasons.first()
        val reasonDetail = reasons.joinToString("; ")
        logger.warn(
            "Intake quarantined: sourceSystem={}, externalId={}, reasons={}",
            input.sourceSystem.take(20), input.externalTransactionId.take(30), reasonDetail
        )

        quarantineRepository.save(
            ScreeningQuarantineEntity(
                id = UUID.randomUUID(),
                sourceSystem = input.sourceSystem.take(50),
                externalId = input.externalTransactionId.take(255).ifBlank { null },
                reasonCode = reasonCode,
                reasonDetail = reasonDetail,
                purpose = input.purpose,
                rawPayload = input.snapshot.ifEmpty { mapOf("_empty" to true) },
                correlationId = input.correlationId,
                receivedAt = Instant.now(),
            )
        )

        return IntakeResult.Quarantined(reasonCode, reasonDetail)
    }
}
