package br.com.decision.infrastructure.input.http.dto

import java.time.Instant
import java.util.UUID

data class ExpressionEvaluationResponse(
    val factName: String,
    val operator: String,
    val expectedValue: Any?,
    val actualValue: Any?,
    val satisfied: Boolean,
    val justification: String
)

data class DecisionExecutionResponse(
    val id: UUID,
    val transactionId: String,
    val ruleId: UUID,
    val decision: String,
    val actions: List<String>,
    val facts: Map<String, Any?>,
    val matchedExpressions: List<ExpressionEvaluationResponse>,
    val failedExpressions: List<ExpressionEvaluationResponse>,
    val configurationVersion: Int,
    val executionTimeMs: Long,
    val traceId: String,
    val timestamp: Instant
)
