package br.com.pld.customeranalysis.dossier

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Port para submissão de comunicação ao COAF.
 * Em produção seria substituído por adapter real (webservice/lote).
 */
interface CoafSubmissionPort {
    fun submit(communication: CoafCommunicationEntity): SubmissionResult
}

enum class SubmissionOutcome { ACKNOWLEDGED, REJECTED, PENDING }

data class SubmissionResult(
    val outcome: SubmissionOutcome,
    val protocolNumber: String? = null,
    val rejectionReason: String? = null,
)

/**
 * Adapter mock: simula envio ao COAF.
 * - 80% → ACKNOWLEDGED com protocolo gerado
 * - 10% → REJECTED (campos incompletos)
 * - 10% → PENDING (timeout simulado)
 *
 * Latência simulada: 1-3s.
 */
@Component
class MockCoafSubmissionAdapter : CoafSubmissionPort {

    private val sequence = AtomicInteger(0)

    override fun submit(communication: CoafCommunicationEntity): SubmissionResult {
        // Simula latência
        Thread.sleep(1000L + abs(communication.id.hashCode() % 2000L))

        val roll = abs(communication.id.hashCode()) % 10
        return when {
            roll < 8 -> {
                val seq = sequence.incrementAndGet()
                SubmissionResult(
                    outcome = SubmissionOutcome.ACKNOWLEDGED,
                    protocolNumber = "COAF-2026-${seq.toString().padStart(6, '0')}",
                )
            }
            roll < 9 -> SubmissionResult(
                outcome = SubmissionOutcome.REJECTED,
                rejectionReason = "Campos obrigatórios incompletos: operationType e narrative são requeridos.",
            )
            else -> SubmissionResult(outcome = SubmissionOutcome.PENDING)
        }
    }
}
