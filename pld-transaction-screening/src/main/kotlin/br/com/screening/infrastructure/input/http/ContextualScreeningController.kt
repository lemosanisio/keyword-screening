package br.com.screening.infrastructure.input.http

import br.com.generated.api.ContextualScreeningApi
import br.com.generated.model.AnalystDecisionRequest
import br.com.generated.model.AnalystDecisionResponse
import br.com.generated.model.ContextualScreeningRequest
import br.com.generated.model.ContextualScreeningResponse
import br.com.screening.application.usecase.EvaluateContextualScreeningCommand
import br.com.screening.application.usecase.EvaluateContextualScreeningUseCase
import br.com.screening.application.usecase.RegisterAnalystDecisionCommand
import br.com.screening.application.usecase.RegisterAnalystDecisionUseCase
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RestController
class ContextualScreeningController(
    private val evaluateUseCase: EvaluateContextualScreeningUseCase,
    private val registerDecisionUseCase: RegisterAnalystDecisionUseCase
) : ContextualScreeningApi {

    override fun evaluateContextualScreening(
        contextualScreeningRequest: ContextualScreeningRequest
    ): ResponseEntity<ContextualScreeningResponse> {
        val command = EvaluateContextualScreeningCommand(
            transactionId = TransactionId(contextualScreeningRequest.transactionId),
            ruleId = contextualScreeningRequest.ruleId ?: "CONTEXTUAL_SCREENING",
            description = contextualScreeningRequest.description,
            matchedKeyword = contextualScreeningRequest.matchedKeyword
        )
        val result = evaluateUseCase.execute(command)
        val response = ContextualScreeningResponse(
            classification = mapClassification(result.classification),
            confidence = result.confidence,
            reason = result.reason,
            requiresAnalystReview = result.requiresAnalystReview
        )
        return ResponseEntity.ok(response)
    }

    override fun registerAnalystDecision(
        analystDecisionRequest: AnalystDecisionRequest
    ): ResponseEntity<AnalystDecisionResponse> {
        val command = RegisterAnalystDecisionCommand(
            transactionId = TransactionId(analystDecisionRequest.transactionId),
            ruleId = analystDecisionRequest.ruleId ?: "CONTEXTUAL_SCREENING",
            analystDecision = mapAnalystDecision(analystDecisionRequest.analystDecision.value)
        )
        val result = registerDecisionUseCase.execute(command)
        val response = AnalystDecisionResponse(
            transactionId = result.transactionId,
            ruleId = result.ruleId,
            analystDecision = result.analystDecision,
            registeredAt = OffsetDateTime.parse(result.registeredAt)
        )
        return ResponseEntity.ok(response)
    }

    /**
     * Mapeia a classificação de domínio para a enum da API gerada.
     * Domínio: FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN
     * API:    NOT_SUSPICIOUS, SUSPICIOUS, INCONCLUSIVE
     */
    private fun mapClassification(domainClassification: String): ContextualScreeningResponse.Classification =
        when (domainClassification) {
            "FALSE_POSITIVE" -> ContextualScreeningResponse.Classification.NOT_SUSPICIOUS
            "SUSPICIOUS" -> ContextualScreeningResponse.Classification.SUSPICIOUS
            "UNCERTAIN" -> ContextualScreeningResponse.Classification.INCONCLUSIVE
            else -> ContextualScreeningResponse.Classification.forValue(domainClassification)
        }

    /**
     * Mapeia a decisão do analista da API para o domínio.
     * API:     APPROVE, REJECT
     * Domínio: SUSPICIOUS, FALSE_POSITIVE
     */
    private fun mapAnalystDecision(apiDecision: String): String =
        when (apiDecision) {
            "APPROVE" -> "SUSPICIOUS"
            "REJECT" -> "FALSE_POSITIVE"
            else -> apiDecision
        }
}
