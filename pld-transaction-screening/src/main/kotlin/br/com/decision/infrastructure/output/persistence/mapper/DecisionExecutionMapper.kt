package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.DecisionResult
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.DecisionExecutionEntity
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DecisionExecutionMapper(private val objectMapper: ObjectMapper) {

    fun toDomain(entity: DecisionExecutionEntity): DecisionExecution =
        DecisionExecution(
            id = entity.id,
            transactionId = TransactionId(entity.transactionId),
            ruleId = RuleId(entity.ruleId),
            configurationVersion = ConfigurationVersion(entity.configurationVersion),
            facts = mapToFacts(entity.facts),
            result = DecisionResult(
                decision = Decision.valueOf(entity.decision),
                actions = entity.actions.map { Action.valueOf(it) },
                matchedExpressions = entity.matchedExpressions.map { mapToEvaluation(it) },
                failedExpressions = entity.failedExpressions.map { mapToEvaluation(it) },
                executionTimeMs = entity.executionTimeMs,
                configurationVersion = ConfigurationVersion(entity.configurationVersion),
                facts = mapToFacts(entity.facts),
                factResults = entity.factResults.map(::mapToFactResult),
                evaluationStatus = br.com.decision.domain.model.EvaluationStatus.valueOf(entity.evaluationStatus),
                evaluationOutcome = br.com.decision.domain.model.EvaluationOutcome.valueOf(entity.evaluationOutcome),
                reviewRequired = entity.reviewRequired,
                recommendedRoute = entity.recommendedRoute?.let(br.com.decision.domain.model.RecommendedRoute::valueOf),
            ),
            explanation = mapToExplanation(entity.explanation),
            executionTimeMs = entity.executionTimeMs,
            traceId = TraceId(entity.traceId ?: "unknown"),
            timestamp = entity.createdAt,
            evaluationId = entity.evaluationId,
            partyId = entity.partyId,
            correlationId = entity.correlationId,
            causationId = entity.causationId,
        )

    fun toEntity(domain: DecisionExecution): DecisionExecutionEntity =
        DecisionExecutionEntity(
            id = domain.id,
            transactionId = domain.transactionId.value,
            ruleId = domain.ruleId.value,
            configurationVersion = domain.configurationVersion.value,
            facts = mapFromFacts(domain.facts),
            factResults = domain.result.factResults.map(::mapFromFactResult),
            decision = domain.result.decision.name,
            actions = domain.result.actions.map { it.name },
            matchedExpressions = domain.result.matchedExpressions.map { mapFromEvaluation(it) },
            failedExpressions = domain.result.failedExpressions.map { mapFromEvaluation(it) },
            explanation = mapFromExplanation(domain.explanation),
            executionTimeMs = domain.executionTimeMs,
            traceId = domain.traceId.value,
            evaluationId = domain.evaluationId,
            partyId = domain.partyId,
            correlationId = domain.correlationId,
            causationId = domain.causationId,
            evaluationStatus = domain.result.evaluationStatus.name,
            evaluationOutcome = domain.result.evaluationOutcome.name,
            reviewRequired = domain.result.reviewRequired,
            recommendedRoute = domain.result.recommendedRoute?.name,
            createdAt = domain.timestamp
        )

    // --- Facts serialization ---

    private fun mapToFacts(raw: Map<String, Any?>): Map<FactName, FactValue> =
        raw.mapKeys { FactName(it.key) }
            .mapValues { mapToFactValue(it.value) }

    private fun mapFromFacts(facts: Map<FactName, FactValue>): Map<String, Any?> =
        facts.map { (name, value) -> name.value to mapFromFactValue(value) }.toMap()

    private fun mapToFactValue(raw: Any?): FactValue {
        if (raw is Map<*, *>) {
            val type = raw["type"] as? String
            return when (type) {
                "BOOLEAN" -> FactValue.BooleanValue(raw["value"] as Boolean)
                "ENUM" -> FactValue.EnumValue(raw["value"] as String)
                "NUMBER" -> FactValue.NumberValue(BigDecimal(raw["value"].toString()))
                "STRING" -> FactValue.StringValue(raw["value"] as String)
                "MONEY" -> FactValue.MoneyValue(
                    amount = BigDecimal(raw["amount"].toString()),
                    currency = raw["currency"] as String
                )
                else -> inferFactValue(raw["value"])
            }
        }
        return inferFactValue(raw)
    }

    private fun inferFactValue(raw: Any?): FactValue = when (raw) {
        is Boolean -> FactValue.BooleanValue(raw)
        is Number -> FactValue.NumberValue(BigDecimal(raw.toString()))
        is String -> FactValue.StringValue(raw)
        else -> FactValue.StringValue(raw.toString())
    }

    private fun mapFromFactValue(value: FactValue): Map<String, Any?> = when (value) {
        is FactValue.BooleanValue -> mapOf("type" to "BOOLEAN", "value" to value.value)
        is FactValue.EnumValue -> mapOf("type" to "ENUM", "value" to value.value)
        is FactValue.NumberValue -> mapOf("type" to "NUMBER", "value" to value.value)
        is FactValue.StringValue -> mapOf("type" to "STRING", "value" to value.value)
        is FactValue.MoneyValue -> mapOf("type" to "MONEY", "amount" to value.amount, "currency" to value.currency)
    }

    private fun mapToFactResult(raw: Map<String, Any?>): br.com.decision.domain.model.FactResult =
        br.com.decision.domain.model.FactResult(
            name = FactName(raw["name"] as String),
            quality = br.com.decision.domain.model.FactQuality.valueOf(raw["quality"] as String),
            value = raw["value"]?.let(::mapToFactValue),
            source = raw["source"] as String,
            reasonCode = raw["reasonCode"] as? String,
        )

    private fun mapFromFactResult(result: br.com.decision.domain.model.FactResult): Map<String, Any?> =
        mapOf(
            "name" to result.name.value,
            "quality" to result.quality.name,
            "value" to result.value?.let(::mapFromFactValue),
            "source" to result.source,
            "reasonCode" to result.reasonCode,
        ).filterValues { it != null }

    // --- ExpressionEvaluation serialization ---

    private fun mapToEvaluation(map: Map<String, Any?>): ExpressionEvaluation =
        ExpressionEvaluation(
            factName = FactName(map["factName"] as String),
            operator = ComparisonOperator.valueOf(map["operator"] as String),
            expectedValue = mapToFactValue(map["expectedValue"]),
            actualValue = map["actualValue"]?.let { mapToFactValue(it) },
            satisfied = map["satisfied"] as Boolean,
            justification = map["justification"] as? String ?: "",
            outcome = (map["outcome"] as? String)
                ?.let(br.com.decision.domain.model.ExpressionOutcome::valueOf)
                ?: if (map["satisfied"] as Boolean) br.com.decision.domain.model.ExpressionOutcome.TRUE
                else br.com.decision.domain.model.ExpressionOutcome.FALSE,
        )

    private fun mapFromEvaluation(eval: ExpressionEvaluation): Map<String, Any?> = mapOf(
        "factName" to eval.factName.value,
        "operator" to eval.operator.name,
        "expectedValue" to mapFromFactValue(eval.expectedValue),
        "actualValue" to eval.actualValue?.let { mapFromFactValue(it) },
        "satisfied" to eval.satisfied,
        "outcome" to eval.outcome.name,
        "justification" to eval.justification
    )

    // --- Explanation serialization ---

    private fun mapToExplanation(raw: Map<String, Any?>): DecisionExplanation =
        DecisionExplanation(
            traceId = TraceId(raw["traceId"] as? String ?: "unknown"),
            steps = emptyList() // Steps are complex sealed types; stored as opaque JSON for querying
        )

    private fun mapFromExplanation(explanation: DecisionExplanation): Map<String, Any?> = mapOf(
        "traceId" to explanation.traceId.value,
        "steps" to objectMapper.readValue<List<Map<String, Any?>>>(
            objectMapper.writeValueAsString(explanation.steps)
        )
    )
}
