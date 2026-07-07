package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.*
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.ConfigurationVersionEntity
import br.com.decision.infrastructure.output.persistence.entity.RuleConfigurationEntity
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Component
class RuleConfigurationMapper {

    fun toDomain(entity: RuleConfigurationEntity): RuleConfiguration =
        RuleConfiguration(
            id = entity.id,
            ruleId = RuleId(entity.ruleId),
            expressions = entity.expressions.map { mapToExpression(it) },
            actions = entity.actions.map { Action.valueOf(it) },
            active = entity.active,
            draft = entity.draft,
            currentVersion = ConfigurationVersion(entity.currentVersion),
            versions = entity.versions.map { versionToDomain(it) },
            createdBy = entity.createdBy,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    fun toEntity(domain: RuleConfiguration): RuleConfigurationEntity =
        RuleConfigurationEntity(
            id = domain.id,
            ruleId = domain.ruleId.value,
            expressions = domain.expressions.map { mapFromExpression(it) },
            actions = domain.actions.map { it.name },
            active = domain.active,
            draft = domain.draft,
            currentVersion = domain.currentVersion.value,
            createdBy = domain.createdBy,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )

    fun versionToDomain(entity: ConfigurationVersionEntity): ConfigurationVersionEntry =
        ConfigurationVersionEntry(
            version = ConfigurationVersion(entity.version),
            expressions = entity.expressions.map { mapToExpression(it) },
            actions = entity.actions.map { Action.valueOf(it) },
            active = entity.active,
            createdBy = entity.createdBy,
            createdAt = entity.createdAt
        )

    fun versionToEntity(domain: ConfigurationVersionEntry, configurationId: UUID): ConfigurationVersionEntity =
        ConfigurationVersionEntity(
            id = UUID.randomUUID(),
            configurationId = configurationId,
            version = domain.version.value,
            expressions = domain.expressions.map { mapFromExpression(it) },
            actions = domain.actions.map { it.name },
            active = domain.active,
            createdBy = domain.createdBy,
            createdAt = domain.createdAt
        )

    // --- Expression serialization/deserialization ---

    private fun mapToExpression(map: Map<String, Any?>): Expression {
        val type = map["type"] as? String ?: "CONDITION"
        return when (type) {
            "CONDITION" -> Condition(
                factName = FactName(map["factName"] as String),
                operator = ComparisonOperator.valueOf(map["operator"] as String),
                expectedValue = mapToFactValue(map["expectedValue"])
            )
            "GROUP" -> Group(
                logicalOperator = LogicalOperator.valueOf(map["logicalOperator"] as String),
                expressions = (map["expressions"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any?>>()
                    ?.map { mapToExpression(it) }
                    ?: emptyList()
            )
            else -> throw IllegalArgumentException("Unknown expression type: $type")
        }
    }

    private fun mapFromExpression(expression: Expression): Map<String, Any?> =
        when (expression) {
            is Condition -> mapOf(
                "type" to "CONDITION",
                "factName" to expression.factName.value,
                "operator" to expression.operator.name,
                "expectedValue" to mapFromFactValue(expression.expectedValue)
            )
            is Group -> mapOf(
                "type" to "GROUP",
                "logicalOperator" to expression.logicalOperator.name,
                "expressions" to expression.expressions.map { mapFromExpression(it) }
            )
        }

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
}
