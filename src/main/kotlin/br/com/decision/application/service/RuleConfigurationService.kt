package br.com.decision.application.service

import br.com.decision.application.usecase.CreateRuleConfigurationCommand
import br.com.decision.application.usecase.ManageRuleConfigurationUseCase
import br.com.decision.application.usecase.UpdateRuleConfigurationCommand
import br.com.decision.domain.exception.DuplicateActiveConfigException
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.exception.RuleConfigurationNotFoundException
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ConfigurationVersionEntry
import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.port.DryRunLogRepository
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Implementação do caso de uso de gerenciamento de configurações de regras.
 * Responsável por criar, atualizar, ativar e desativar configurações,
 * aplicando validações cruzadas contra FactDefinition e RuleDefinition.
 */
@Service
@Transactional
class RuleConfigurationService(
    private val ruleConfigurationRepository: RuleConfigurationRepository,
    private val ruleDefinitionRepository: RuleDefinitionRepository,
    private val factDefinitionRepository: FactDefinitionRepository,
    private val dryRunLogRepository: DryRunLogRepository
) : ManageRuleConfigurationUseCase {

    override fun create(command: CreateRuleConfigurationCommand): RuleConfiguration {
        val ruleDefinition = ruleDefinitionRepository.findByCode(command.ruleCode)
            ?: throw RuleConfigurationNotFoundException(
                "RuleDefinition não encontrada para o código '${command.ruleCode.value}'"
            )

        validateExpressions(command.expressions, ruleDefinition)

        val now = Instant.now()
        val initialVersion = ConfigurationVersion(1)

        val versionEntry = ConfigurationVersionEntry(
            version = initialVersion,
            expressions = command.expressions,
            actions = command.actions,
            active = false,
            createdBy = command.createdBy,
            createdAt = now
        )

        val configuration = RuleConfiguration(
            id = UUID.randomUUID(),
            ruleId = ruleDefinition.id,
            expressions = command.expressions,
            actions = command.actions,
            active = false,
            draft = true,
            currentVersion = initialVersion,
            versions = listOf(versionEntry),
            createdBy = command.createdBy,
            createdAt = now,
            updatedAt = now
        )

        return ruleConfigurationRepository.save(configuration)
    }

    override fun update(id: UUID, command: UpdateRuleConfigurationCommand): RuleConfiguration {
        val existing = ruleConfigurationRepository.findById(id)
            ?: throw RuleConfigurationNotFoundException(
                "Configuração não encontrada para o id '$id'"
            )

        val ruleDefinition = ruleDefinitionRepository.findByCode(
            findRuleCodeByRuleId(existing)
        ) ?: throw RuleConfigurationNotFoundException(
            "RuleDefinition não encontrada para a configuração '$id'"
        )

        validateExpressions(command.expressions, ruleDefinition)

        val now = Instant.now()
        val newVersionNumber = ConfigurationVersion(existing.currentVersion.value + 1)

        val newVersionEntry = ConfigurationVersionEntry(
            version = newVersionNumber,
            expressions = command.expressions,
            actions = command.actions,
            active = existing.active,
            createdBy = command.updatedBy,
            createdAt = now
        )

        val updated = existing.copy(
            expressions = command.expressions,
            actions = command.actions,
            currentVersion = newVersionNumber,
            versions = existing.versions + newVersionEntry,
            updatedAt = now
        )

        return ruleConfigurationRepository.save(updated)
    }

    override fun activate(id: UUID): RuleConfiguration {
        val config = ruleConfigurationRepository.findById(id)
            ?: throw RuleConfigurationNotFoundException(
                "Configuração não encontrada para o id '$id'"
            )

        val dryRunLogs = dryRunLogRepository.findByConfigurationIdAndVersion(
            config.id, config.currentVersion
        )
        if (dryRunLogs.isEmpty()) {
            throw InvalidConfigurationException(
                "Dry-run obrigatório antes da ativação"
            )
        }

        val existingActive = ruleConfigurationRepository.findActiveByRuleId(config.ruleId)
        if (existingActive != null && existingActive.id != config.id) {
            throw DuplicateActiveConfigException(
                "Já existe uma configuração ativa para a regra '${config.ruleId.value}'"
            )
        }

        val activated = config.copy(
            active = true,
            draft = false,
            updatedAt = Instant.now()
        )

        return ruleConfigurationRepository.save(activated)
    }

    override fun deactivate(id: UUID): RuleConfiguration {
        val config = ruleConfigurationRepository.findById(id)
            ?: throw RuleConfigurationNotFoundException(
                "Configuração não encontrada para o id '$id'"
            )

        val deactivated = config.copy(
            active = false,
            updatedAt = Instant.now()
        )

        return ruleConfigurationRepository.save(deactivated)
    }

    /**
     * Valida cada expression (Condition no MVP) contra:
     * - FactDefinition existe e está enabled
     * - FactName está nos supportedFacts da RuleDefinition
     * - Operador está nos supportedOperators da FactDefinition
     * - Tipo do expectedValue é compatível com FactDefinition.type
     */
    private fun validateExpressions(expressions: List<Expression>, ruleDefinition: RuleDefinition) {
        if (expressions.size > 10) {
            throw InvalidConfigurationException("Máximo 10 expressions por configuração")
        }

        expressions.forEach { expression ->
            when (expression) {
                is Condition -> validateCondition(expression, ruleDefinition)
                else -> { /* Group não validado no MVP */ }
            }
        }
    }

    private fun validateCondition(condition: Condition, ruleDefinition: RuleDefinition) {
        val factDefinition = factDefinitionRepository.findByName(condition.factName)
            ?: throw InvalidConfigurationException(
                "Fato '${condition.factName.value}' não encontrado no catálogo"
            )

        if (!factDefinition.enabled) {
            throw InvalidConfigurationException(
                "Fato '${condition.factName.value}' está desabilitado"
            )
        }

        if (condition.factName !in ruleDefinition.supportedFacts) {
            throw InvalidConfigurationException(
                "Fato '${condition.factName.value}' não é suportado pela regra '${ruleDefinition.code.value}'"
            )
        }

        if (condition.operator !in factDefinition.supportedOperators) {
            throw InvalidConfigurationException(
                "Operador '${condition.operator}' não é suportado para o fato '${condition.factName.value}'"
            )
        }

        validateTypeCompatibility(condition.expectedValue, factDefinition)
    }

    private fun validateTypeCompatibility(value: FactValue, factDefinition: FactDefinition) {
        val compatible = when (factDefinition.type) {
            FactType.BOOLEAN -> value is FactValue.BooleanValue
            FactType.ENUM -> value is FactValue.EnumValue
            FactType.NUMBER -> value is FactValue.NumberValue
            FactType.STRING -> value is FactValue.StringValue
            FactType.MONEY -> value is FactValue.MoneyValue
        }

        if (!compatible) {
            throw InvalidConfigurationException(
                "Tipo do valor incompatível com o fato '${factDefinition.name.value}': " +
                    "esperado ${factDefinition.type}, recebido ${value::class.simpleName}"
            )
        }
    }

    /**
     * Encontra o RuleCode a partir da RuleDefinition associada à configuração.
     * Como RuleConfiguration guarda ruleId (UUID), precisamos buscar todas as RuleDefinitions.
     */
    private fun findRuleCodeByRuleId(config: RuleConfiguration): RuleCode {
        val allDefinitions = ruleDefinitionRepository.findAll()
        val definition = allDefinitions.find { it.id == config.ruleId }
            ?: throw RuleConfigurationNotFoundException(
                "RuleDefinition não encontrada para ruleId '${config.ruleId.value}'"
            )
        return definition.code
    }
}
