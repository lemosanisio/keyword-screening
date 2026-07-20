package br.com.decision.application.service

import br.com.decision.application.usecase.DryRunResult
import br.com.decision.application.usecase.ExecuteDryRunCommand
import br.com.decision.application.usecase.ExecuteDryRunUseCase
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.exception.RuleConfigurationNotFoundException
import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.model.DryRunLogResult
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.port.DryRunLogRepository
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.service.RuleEngine
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Serviço de Dry-Run — executa o mesmo RuleEngine do fluxo produtivo
 * sem side effects de persistência de DecisionExecution, publicação de eventos,
 * ou invocação de FactResolvers.
 *
 * Facts são fornecidos manualmente pelo usuário e validados contra o catálogo (FactDefinition).
 * Funciona tanto para configurações draft quanto active.
 */
@Service
class DryRunService(
    private val ruleConfigurationRepository: RuleConfigurationRepository,
    private val factDefinitionRepository: FactDefinitionRepository,
    private val dryRunLogRepository: DryRunLogRepository,
    private val ruleEngine: RuleEngine
) : ExecuteDryRunUseCase {

    override fun execute(command: ExecuteDryRunCommand): DryRunResult {
        // 1. Buscar configuração (draft ou active)
        val configuration = ruleConfigurationRepository.findById(command.configurationId)
            ?: throw RuleConfigurationNotFoundException(
                "Configuração não encontrada: ${command.configurationId}"
            )

        // 2. Validar facts de entrada contra FactDefinition
        validateFacts(command.facts)

        // 3. Avaliar usando o mesmo RuleEngine do fluxo produtivo
        val evaluationResult = ruleEngine.evaluate(command.facts, configuration.expressions)

        // 4. Determinar decisão
        val decision = if (evaluationResult.allSatisfied) Decision.ALERT else Decision.IGNORE
        val actions = if (evaluationResult.allSatisfied) configuration.actions else emptyList()

        val matchedExpressions = evaluationResult.evaluations.filter { it.satisfied }
        val failedExpressions = evaluationResult.evaluations.filter { !it.satisfied }

        val result = DryRunResult(
            decision = decision,
            actions = actions,
            matchedExpressions = matchedExpressions,
            failedExpressions = failedExpressions,
            configurationVersion = configuration.currentVersion
        )

        // 5. Persistir DryRunLog (registro leve para rastreabilidade e pré-requisito de ativação)
        val logResult = DryRunLogResult(
            decision = decision,
            actions = actions
        )
        val dryRunLog = DryRunLog(
            id = UUID.randomUUID(),
            configurationId = command.configurationId,
            version = configuration.currentVersion,
            facts = command.facts,
            result = logResult,
            executedBy = command.executedBy,
            createdAt = Instant.now()
        )
        dryRunLogRepository.save(dryRunLog)

        return result
    }

    private fun validateFacts(facts: Map<FactName, FactValue>) {
        val errors = mutableListOf<String>()

        for ((factName, factValue) in facts) {
            val definition = factDefinitionRepository.findByName(factName)

            if (definition == null) {
                errors.add("Fact '${factName.value}' não existe no catálogo")
                continue
            }

            if (!definition.enabled) {
                errors.add("Fact '${factName.value}' está desabilitado")
                continue
            }

            if (!isTypeCompatible(definition.type, factValue)) {
                errors.add(
                    "Fact '${factName.value}' espera tipo ${definition.type}, " +
                        "mas recebeu ${factValue.typeName()}"
                )
            }
        }

        if (errors.isNotEmpty()) {
            throw InvalidConfigurationException(
                "Validação de facts falhou: ${errors.joinToString("; ")}"
            )
        }
    }

    private fun isTypeCompatible(type: FactType, value: FactValue): Boolean {
        return when (type) {
            FactType.BOOLEAN -> value is FactValue.BooleanValue
            FactType.ENUM -> value is FactValue.EnumValue
            FactType.NUMBER -> value is FactValue.NumberValue
            FactType.STRING -> value is FactValue.StringValue
            FactType.MONEY -> value is FactValue.MoneyValue
        }
    }

    private fun FactValue.typeName(): String = when (this) {
        is FactValue.BooleanValue -> "BOOLEAN"
        is FactValue.EnumValue -> "ENUM"
        is FactValue.NumberValue -> "NUMBER"
        is FactValue.StringValue -> "STRING"
        is FactValue.MoneyValue -> "MONEY"
    }
}
