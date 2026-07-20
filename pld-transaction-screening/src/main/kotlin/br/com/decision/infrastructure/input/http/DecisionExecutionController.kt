package br.com.decision.infrastructure.input.http

import br.com.decision.application.usecase.QueryDecisionExecutionUseCase
import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.input.http.dto.DecisionExecutionResponse
import br.com.decision.infrastructure.input.http.dto.ExpressionEvaluationResponse
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/decision/executions")
class DecisionExecutionController(
    private val queryDecisionExecutionUseCase: QueryDecisionExecutionUseCase
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) transactionId: String?,
        @RequestParam(required = false) ruleId: UUID?,
        @RequestParam(required = false) decision: Decision?,
        @RequestParam(required = false) traceId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        // traceId returns a single result (not paginated)
        if (traceId != null) {
            val execution = queryDecisionExecutionUseCase.findByTraceId(TraceId(traceId))
                ?: return ResponseEntity.ok(emptyPageResponse())
            return ResponseEntity.ok(execution.toResponse())
        }

        val result: Page<DecisionExecution> = when {
            transactionId != null -> queryDecisionExecutionUseCase.findByTransactionId(TransactionId(transactionId), page, size)
            ruleId != null -> queryDecisionExecutionUseCase.findByRuleId(RuleId(ruleId), page, size)
            decision != null -> queryDecisionExecutionUseCase.findByDecision(decision, page, size)
            else -> return ResponseEntity.badRequest().build()
        }

        return ResponseEntity.ok(result.toPageResponse())
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: UUID): ResponseEntity<DecisionExecutionResponse> {
        val execution = queryDecisionExecutionUseCase.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(execution.toResponse())
    }

    private fun Page<DecisionExecution>.toPageResponse(): Map<String, Any> {
        return mapOf(
            "content" to content.map { it.toResponse() },
            "page" to number,
            "size" to size,
            "totalElements" to totalElements,
            "totalPages" to totalPages
        )
    }

    private fun emptyPageResponse(): Map<String, Any> {
        return mapOf(
            "content" to emptyList<DecisionExecutionResponse>(),
            "page" to 0,
            "size" to 20,
            "totalElements" to 0L,
            "totalPages" to 0
        )
    }

    private fun DecisionExecution.toResponse(): DecisionExecutionResponse {
        return DecisionExecutionResponse(
            id = id,
            transactionId = transactionId.value,
            ruleId = ruleId.value,
            decision = result.decision.name,
            actions = result.actions.map { it.name },
            facts = facts.entries.associate { (name, value) -> name.value to value.toSerializable() },
            matchedExpressions = result.matchedExpressions.map { it.toResponse() },
            failedExpressions = result.failedExpressions.map { it.toResponse() },
            configurationVersion = configurationVersion.value,
            executionTimeMs = executionTimeMs,
            traceId = traceId.value,
            timestamp = timestamp
        )
    }

    private fun ExpressionEvaluation.toResponse(): ExpressionEvaluationResponse {
        return ExpressionEvaluationResponse(
            factName = factName.value,
            operator = operator.name,
            expectedValue = expectedValue.toSerializable(),
            actualValue = actualValue?.toSerializable(),
            satisfied = satisfied,
            justification = justification
        )
    }

    private fun FactValue.toSerializable(): Any? {
        return when (this) {
            is FactValue.BooleanValue -> value
            is FactValue.EnumValue -> value
            is FactValue.NumberValue -> value
            is FactValue.StringValue -> value
            is FactValue.MoneyValue -> mapOf("amount" to amount, "currency" to currency)
        }
    }
}
