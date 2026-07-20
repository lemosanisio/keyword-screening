package br.com.screening.application.usecase

interface RegisterAnalystDecisionUseCase {
    fun execute(command: RegisterAnalystDecisionCommand): AnalystDecisionResultDto
}
