package br.com.screening.application.usecase

interface EvaluateContextualScreeningUseCase {
    fun execute(command: EvaluateContextualScreeningCommand): ContextualScreeningResultDto
}
