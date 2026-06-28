package br.com.screening.application.service

import br.com.screening.domain.model.RuleExecution
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.domain.repository.RuleExecutionRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class IdempotencyService(
    private val ruleExecutionRepository: RuleExecutionRepository
) {
    /**
     * Verifica se já existe uma execução para o par (transactionId, ruleCode).
     * Retorna o resultado persistido ou null se não encontrado.
     */
    fun findExisting(transactionId: String, ruleCode: String): ScreeningResult? =
        ruleExecutionRepository.findByTransactionIdAndRuleCode(transactionId, ruleCode)?.result

    /**
     * Persiste o resultado da execução.
     * Em caso de race condition (UNIQUE constraint violation),
     * o repositório já trata a DataIntegrityViolationException e retorna o registro existente.
     */
    fun persist(transactionId: String, ruleCode: String, result: ScreeningResult): ScreeningResult {
        val execution = RuleExecution(
            transactionId = transactionId,
            ruleCode = ruleCode,
            result = result,
            createdAt = Instant.now()
        )
        return ruleExecutionRepository.save(execution).result
    }
}
