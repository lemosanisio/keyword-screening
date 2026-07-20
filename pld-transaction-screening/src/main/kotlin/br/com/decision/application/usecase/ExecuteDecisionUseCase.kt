package br.com.decision.application.usecase

import br.com.decision.domain.model.DecisionResult

/**
 * Input port: execução de decisão (disparada por evento de detecção).
 * Implementado por DecisionService.
 */
interface ExecuteDecisionUseCase {
    fun execute(command: ExecuteDecisionCommand): DecisionResult
}
