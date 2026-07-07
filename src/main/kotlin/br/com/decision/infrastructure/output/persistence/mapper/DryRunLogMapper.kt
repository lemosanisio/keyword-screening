package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.model.DryRunLogResult
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.infrastructure.output.persistence.entity.DryRunLogEntity
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DryRunLogMapper {

    fun toDomain(entity: DryRunLogEntity): DryRunLog =
        DryRunLog(
            id = entity.id,
            configurationId = entity.configurationId,
            version = ConfigurationVersion(entity.version),
            facts = mapToFacts(entity.facts),
            result = mapToResult(entity.result),
            executedBy = entity.executedBy,
            createdAt = entity.createdAt
        )

    fun toEntity(domain: DryRunLog): DryRunLogEntity =
        DryRunLogEntity(
            id = domain.id,
            configurationId = domain.configurationId,
            version = domain.version.value,
            facts = mapFromFacts(domain.facts),
            result = mapFromResult(domain.result),
            executedBy = domain.executedBy,
            createdAt = domain.createdAt
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

    // --- Result serialization ---

    private fun mapToResult(raw: Map<String, Any?>): DryRunLogResult =
        DryRunLogResult(
            decision = Decision.valueOf(raw["decision"] as? String ?: "IGNORE"),
            actions = (raw["actions"] as? List<*>)?.filterIsInstance<String>()?.map { Action.valueOf(it) } ?: emptyList()
        )

    private fun mapFromResult(result: DryRunLogResult): Map<String, Any?> = mapOf(
        "decision" to result.decision.name,
        "actions" to result.actions.map { it.name }
    )
}
