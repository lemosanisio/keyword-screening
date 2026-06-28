package br.com.screening.infrastructure.input.http

import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import br.com.screening.infrastructure.input.http.dto.EvaluateKeywordScreeningRequest
import br.com.screening.infrastructure.input.http.dto.EvaluateKeywordScreeningResponse
import br.com.screening.infrastructure.input.http.dto.MatchResultResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/rules/keyword-screening")
class KeywordScreeningController(
    private val evaluateKeywordScreeningUseCase: EvaluateKeywordScreeningUseCase
) {
    @PostMapping("/evaluate")
    fun evaluate(
        @Valid @RequestBody request: EvaluateKeywordScreeningRequest
    ): ResponseEntity<EvaluateKeywordScreeningResponse> {
        val command = EvaluateKeywordScreeningCommand(
            transactionId = request.transactionId!!,
            description = request.description!!
        )
        val result = evaluateKeywordScreeningUseCase.execute(command)
        val response = EvaluateKeywordScreeningResponse(
            ruleCode = result.ruleCode,
            matched = result.matched,
            matches = result.matches.map { MatchResultResponse(it.term, it.category.name) }
        )
        return ResponseEntity.ok(response)
    }
}
