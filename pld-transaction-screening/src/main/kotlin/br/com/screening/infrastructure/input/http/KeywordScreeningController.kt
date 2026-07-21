package br.com.screening.infrastructure.input.http

import br.com.generated.api.KeywordScreeningApi
import br.com.generated.model.EvaluateKeywordScreeningRequest
import br.com.generated.model.EvaluateKeywordScreeningResponse
import br.com.generated.model.MatchResultResponse
import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class KeywordScreeningController(
    private val evaluateKeywordScreeningUseCase: EvaluateKeywordScreeningUseCase,
) : KeywordScreeningApi {

    override fun evaluateKeywordScreening(
        evaluateKeywordScreeningRequest: EvaluateKeywordScreeningRequest,
        xCorrelationId: String?,
    ): ResponseEntity<EvaluateKeywordScreeningResponse> {
        // Validação de blank (minLength no OpenAPI não cobre whitespace-only)
        require(evaluateKeywordScreeningRequest.transactionId.isNotBlank()) { "transactionId é obrigatório" }
        require(evaluateKeywordScreeningRequest.customerId.isNotBlank()) { "customerId é obrigatório" }
        require(evaluateKeywordScreeningRequest.description.isNotBlank()) { "description é obrigatória" }

        val command = EvaluateKeywordScreeningCommand(
            transactionId = TransactionId(evaluateKeywordScreeningRequest.transactionId),
            customerId = CustomerId(evaluateKeywordScreeningRequest.customerId),
            description = evaluateKeywordScreeningRequest.description,
            correlationId = xCorrelationId?.takeIf(String::isNotBlank),
        )
        val result = evaluateKeywordScreeningUseCase.execute(command)
        val response = EvaluateKeywordScreeningResponse(
            ruleCode = result.ruleCode,
            matched = result.matched,
            matches = result.matches.map {
                MatchResultResponse(
                    term = it.term,
                    category = MatchResultResponse.Category.forValue(it.category.name)
                )
            }
        )
        return ResponseEntity.ok(response)
    }
}
