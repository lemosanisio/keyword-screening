package br.com.decision.application.usecase

/**
 * Input port: execução de dry-run para testar configurações.
 * Implementado por DryRunService.
 */
interface ExecuteDryRunUseCase {
    fun execute(command: ExecuteDryRunCommand): DryRunResult
}
