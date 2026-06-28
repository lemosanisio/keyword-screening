package br.com.screening.infrastructure.input.http.dto

import br.com.screening.application.usecase.AnalystDecisionResultDto
import br.com.screening.application.usecase.ContextualScreeningResultDto

fun ContextualScreeningResultDto.toResponse() = ContextualScreeningResponse(
    classification = classification,
    confidence = confidence,
    reason = reason,
    requiresAnalystReview = requiresAnalystReview
)

fun AnalystDecisionResultDto.toResponse() = AnalystDecisionResponse(
    transactionId = transactionId,
    ruleId = ruleId,
    analystDecision = analystDecision,
    registeredAt = registeredAt
)
