package br.com.screening.application.usecase

interface EvaluateKeywordScreeningUseCase {
    fun execute(command: EvaluateKeywordScreeningCommand): EvaluateKeywordScreeningResult
}
