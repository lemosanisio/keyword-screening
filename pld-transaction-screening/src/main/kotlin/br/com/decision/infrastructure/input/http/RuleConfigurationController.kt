package br.com.decision.infrastructure.input.http

import br.com.decision.application.usecase.CreateRuleConfigurationCommand
import br.com.decision.application.usecase.ManageRuleConfigurationUseCase
import br.com.decision.application.usecase.UpdateRuleConfigurationCommand
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ConfigurationVersionEntry
import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.infrastructure.input.http.dto.ConditionDto
import br.com.decision.infrastructure.input.http.dto.ConfigurationVersionResponse
import br.com.decision.infrastructure.input.http.dto.CreateRuleConfigurationRequest
import br.com.decision.infrastructure.input.http.dto.RuleConfigurationResponse
import br.com.decision.infrastructure.input.http.dto.UpdateRuleConfigurationRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST Controller para gerenciamento de Rule Configurations.
 * Permite criar, atualizar, ativar/desativar e consultar configurações de regras.
 */
@RestController
class RuleConfigurationController(
    private val manageRuleConfigurationUseCase: ManageRuleConfigurationUseCase,
    private val ruleConfigurationRepository: RuleConfigurationRepository,
    private val ruleDefinitionRepository: RuleDefinitionRepository
) {

    @PostMapping("/v1/decision/rules/{ruleCode}/configurations")
    fun create(
        @PathVariable ruleCode: String,
        @Valid @RequestBody request: CreateRuleConfigurationRequest
    ): ResponseEntity<RuleConfigurationResponse> {
        val command = CreateRuleConfigurationCommand(
            ruleCode = RuleCode(ruleCode),
            expressions = request.expressions.map { it.toDomain() },
            actions = request.actions.map { Action.valueOf(it) },
            createdBy = request.createdBy
        )

        val config = manageRuleConfigurationUseCase.create(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(config.toResponse(ruleCode))
    }

    @GetMapping("/v1/decision/rules/{ruleCode}/configurations")
    fun listByRuleCode(
        @PathVariable ruleCode: String
    ): ResponseEntity<List<RuleConfigurationResponse>> {
        val ruleDefinition = ruleDefinitionRepository.findByCode(RuleCode(ruleCode))
            ?: return ResponseEntity.ok(emptyList())

        val configurations = ruleConfigurationRepository.findByRuleId(ruleDefinition.id)
        return ResponseEntity.ok(configurations.map { it.toResponse(ruleCode) })
    }

    @GetMapping("/v1/decision/rule-configurations/{id}")
    fun getById(
        @PathVariable id: UUID
    ): ResponseEntity<RuleConfigurationResponse> {
        val config = ruleConfigurationRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        val ruleCode = resolveRuleCode(config)
        return ResponseEntity.ok(config.toResponse(ruleCode))
    }

    @PutMapping("/v1/decision/rule-configurations/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRuleConfigurationRequest
    ): ResponseEntity<RuleConfigurationResponse> {
        val command = UpdateRuleConfigurationCommand(
            expressions = request.expressions.map { it.toDomain() },
            actions = request.actions.map { Action.valueOf(it) },
            updatedBy = request.updatedBy
        )

        val config = manageRuleConfigurationUseCase.update(id, command)
        val ruleCode = resolveRuleCode(config)
        return ResponseEntity.ok(config.toResponse(ruleCode))
    }

    @PostMapping("/v1/decision/rule-configurations/{id}/activate")
    fun activate(
        @PathVariable id: UUID
    ): ResponseEntity<RuleConfigurationResponse> {
        val config = manageRuleConfigurationUseCase.activate(id)
        val ruleCode = resolveRuleCode(config)
        return ResponseEntity.ok(config.toResponse(ruleCode))
    }

    @PostMapping("/v1/decision/rule-configurations/{id}/deactivate")
    fun deactivate(
        @PathVariable id: UUID
    ): ResponseEntity<RuleConfigurationResponse> {
        val config = manageRuleConfigurationUseCase.deactivate(id)
        val ruleCode = resolveRuleCode(config)
        return ResponseEntity.ok(config.toResponse(ruleCode))
    }

    @GetMapping("/v1/decision/rule-configurations/{id}/versions")
    fun getVersionHistory(
        @PathVariable id: UUID
    ): ResponseEntity<List<ConfigurationVersionResponse>> {
        val config = ruleConfigurationRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        val versions = config.versions.map { it.toResponse() }
        return ResponseEntity.ok(versions)
    }

    // --- Conversões ---

    private fun resolveRuleCode(config: RuleConfiguration): String {
        val allDefinitions = ruleDefinitionRepository.findAll()
        val definition = allDefinitions.find { it.id == config.ruleId }
        return definition?.code?.value ?: "UNKNOWN"
    }

    private fun RuleConfiguration.toResponse(ruleCode: String): RuleConfigurationResponse {
        return RuleConfigurationResponse(
            id = id,
            ruleCode = ruleCode,
            version = currentVersion.value,
            active = active,
            draft = draft,
            expressions = expressions.map { it.toDto() },
            actions = actions.map { it.name },
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun ConfigurationVersionEntry.toResponse(): ConfigurationVersionResponse {
        return ConfigurationVersionResponse(
            version = version.value,
            expressions = expressions.map { it.toDto() },
            actions = actions.map { it.name },
            active = active,
            createdBy = createdBy,
            createdAt = createdAt
        )
    }

    private fun Expression.toDto(): ConditionDto {
        return when (this) {
            is Condition -> ConditionDto(
                type = "CONDITION",
                factName = factName.value,
                operator = operator.name,
                expectedValue = expectedValue.toApiValue()
            )
            else -> ConditionDto(
                type = "GROUP",
                factName = "",
                operator = "",
                expectedValue = null
            )
        }
    }

    private fun FactValue.toApiValue(): Any {
        return when (this) {
            is FactValue.BooleanValue -> value
            is FactValue.EnumValue -> value
            is FactValue.NumberValue -> value
            is FactValue.StringValue -> value
            is FactValue.MoneyValue -> mapOf("amount" to amount, "currency" to currency)
        }
    }

    private fun ConditionDto.toDomain(): Expression {
        return Condition(
            factName = FactName(factName),
            operator = ComparisonOperator.valueOf(operator),
            expectedValue = parseExpectedValue(expectedValue)
        )
    }

    private fun parseExpectedValue(value: Any?): FactValue {
        return when (value) {
            is Boolean -> FactValue.BooleanValue(value)
            is Number -> FactValue.NumberValue(value.toBigDecimal())
            is String -> FactValue.EnumValue(value)
            is Map<*, *> -> {
                val amount = (value["amount"] as? Number)?.toBigDecimal()
                val currency = value["currency"] as? String
                if (amount != null && currency != null) {
                    FactValue.MoneyValue(amount, currency)
                } else {
                    FactValue.StringValue(value.toString())
                }
            }
            else -> FactValue.StringValue(value.toString())
        }
    }

    private fun Number.toBigDecimal(): java.math.BigDecimal {
        return when (this) {
            is java.math.BigDecimal -> this
            is Double -> java.math.BigDecimal.valueOf(this)
            is Float -> java.math.BigDecimal.valueOf(this.toDouble())
            is Long -> java.math.BigDecimal.valueOf(this)
            is Int -> java.math.BigDecimal.valueOf(this.toLong())
            else -> java.math.BigDecimal(this.toString())
        }
    }
}
