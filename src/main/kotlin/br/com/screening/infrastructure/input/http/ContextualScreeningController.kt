package br.com.screening.infrastructure.input.http

import br.com.screening.application.usecase.EvaluateContextualScreeningCommand
import br.com.screening.application.usecase.EvaluateContextualScreeningUseCase
import br.com.screening.application.usecase.RegisterAnalystDecisionCommand
import br.com.screening.application.usecase.RegisterAnalystDecisionUseCase
import br.com.screening.infrastructure.input.http.dto.AnalystDecisionRequest
import br.com.screening.infrastructure.input.http.dto.AnalystDecisionResponse
import br.com.screening.infrastructure.input.http.dto.ContextualScreeningRequest
import br.com.screening.infrastructure.input.http.dto.ContextualScreeningResponse
import br.com.screening.infrastructure.input.http.dto.toResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/rules/contextual-screening")
class ContextualScreeningController(
    private val evaluateUseCase: EvaluateContextualScreeningUseCase,
    private val registerDecisionUseCase: RegisterAnalystDecisionUseCase
) {

    @PostMapping("/evaluate")
    fun evaluate(
        @Valid @RequestBody request: ContextualScreeningRequest
    ): ResponseEntity<ContextualScreeningResponse> {
        val command = EvaluateContextualScreeningCommand(
            transactionId = request.transactionId!!,
            ruleId = request.ruleId ?: "CONTEXTUAL_SCREENING",
            description = request.description!!,
            matchedKeyword = request.matchedKeyword!!
        )
        val result = evaluateUseCase.execute(command)
        return ResponseEntity.ok(result.toResponse())
    }

    @PostMapping("/decisions")
    fun registerDecision(
        @Valid @RequestBody request: AnalystDecisionRequest
    ): ResponseEntity<AnalystDecisionResponse> {
        val command = RegisterAnalystDecisionCommand(
            transactionId = request.transactionId!!,
            ruleId = request.ruleId ?: "CONTEXTUAL_SCREENING",
            analystDecision = request.analystDecision!!
        )
        val result = registerDecisionUseCase.execute(command)
        return ResponseEntity.ok(result.toResponse())
    }
}
