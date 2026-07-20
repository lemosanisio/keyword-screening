package br.com.decision.infrastructure.input.http

import br.com.decision.application.usecase.ExecuteDryRunCommand
import br.com.decision.application.usecase.ExecuteDryRunUseCase
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.infrastructure.input.http.dto.DryRunRequest
import br.com.decision.infrastructure.input.http.dto.DryRunResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

/**
 * Controller para execução de dry-run em Rule Configurations.
 * Permite ao analista testar configurações fornecendo facts manualmente,
 * sem produzir side effects de produção (alertas, eventos, execuções).
 */
@RestController
@RequestMapping("/v1/decision/rule-configurations")
class DryRunController(
    private val executeDryRunUseCase: ExecuteDryRunUseCase,
    private val factDefinitionRepository: FactDefinitionRepository
) {

    @PostMapping("/{id}/dry-run")
    fun executeDryRun(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DryRunRequest
    ): ResponseEntity<DryRunResponse> {
        val facts = try {
            convertFacts(request.facts)
        } catch (ex: IllegalArgumentException) {
            throw InvalidConfigurationException(
                "Validação de facts falhou: ${ex.message}"
            )
        }

        val command = ExecuteDryRunCommand(
            configurationId = id,
            facts = facts,
            executedBy = "analyst"  // TODO: extrair do contexto de autenticação
        )

        val result = executeDryRunUseCase.execute(command)
        return ResponseEntity.ok(DryRunResponse.from(result))
    }

    /**
     * Converte o mapa de facts do request (Map<String, Any>) para Map<FactName, FactValue>
     * baseado nas definições de tipo do FactDefinition.
     *
     * A conversão utiliza o catálogo de FactDefinitions para determinar o tipo esperado
     * de cada fact e realiza a coerção apropriada do valor recebido via JSON.
     */
    private fun convertFacts(rawFacts: Map<String, Any>): Map<FactName, FactValue> {
        return rawFacts.mapNotNull { (name, rawValue) ->
            val factName = FactName(name)
            val definition = factDefinitionRepository.findByName(factName)

            val factValue = if (definition != null) {
                coerceToFactValue(definition.type, rawValue, name)
            } else {
                // Deixa a validação no service rejeitar facts desconhecidos
                inferFactValue(rawValue)
            }

            factName to factValue
        }.toMap()
    }

    /**
     * Converte o valor raw do JSON para FactValue baseado no tipo definido no catálogo.
     */
    private fun coerceToFactValue(type: FactType, rawValue: Any, factName: String): FactValue {
        return when (type) {
            FactType.BOOLEAN -> FactValue.BooleanValue(
                when (rawValue) {
                    is Boolean -> rawValue
                    is String -> rawValue.toBooleanStrictOrNull()
                        ?: throw IllegalArgumentException(
                            "Fact '$factName' espera BOOLEAN, mas recebeu valor não conversível: $rawValue"
                        )
                    else -> throw IllegalArgumentException(
                        "Fact '$factName' espera BOOLEAN, mas recebeu: ${rawValue::class.simpleName}"
                    )
                }
            )

            FactType.ENUM -> FactValue.EnumValue(
                when (rawValue) {
                    is String -> rawValue
                    else -> rawValue.toString()
                }
            )

            FactType.NUMBER -> FactValue.NumberValue(
                when (rawValue) {
                    is Number -> BigDecimal(rawValue.toString())
                    is String -> rawValue.toBigDecimalOrNull()
                        ?: throw IllegalArgumentException(
                            "Fact '$factName' espera NUMBER, mas recebeu valor não numérico: $rawValue"
                        )
                    else -> throw IllegalArgumentException(
                        "Fact '$factName' espera NUMBER, mas recebeu: ${rawValue::class.simpleName}"
                    )
                }
            )

            FactType.STRING -> FactValue.StringValue(
                rawValue.toString()
            )

            FactType.MONEY -> {
                @Suppress("UNCHECKED_CAST")
                val map = rawValue as? Map<String, Any>
                    ?: throw IllegalArgumentException(
                        "Fact '$factName' espera MONEY (objeto com amount e currency), " +
                            "mas recebeu: ${rawValue::class.simpleName}"
                    )
                val amount = map["amount"]?.let { BigDecimal(it.toString()) }
                    ?: throw IllegalArgumentException(
                        "Fact '$factName' (MONEY): campo 'amount' é obrigatório"
                    )
                val currency = map["currency"]?.toString()
                    ?: throw IllegalArgumentException(
                        "Fact '$factName' (MONEY): campo 'currency' é obrigatório"
                    )
                FactValue.MoneyValue(amount, currency)
            }
        }
    }

    /**
     * Inferência de tipo quando o fact não existe no catálogo.
     * Permite que a validação downstream (DryRunService) rejeite com mensagem apropriada.
     */
    private fun inferFactValue(rawValue: Any): FactValue {
        return when (rawValue) {
            is Boolean -> FactValue.BooleanValue(rawValue)
            is Number -> FactValue.NumberValue(BigDecimal(rawValue.toString()))
            is String -> FactValue.StringValue(rawValue)
            is Map<*, *> -> {
                val amount = rawValue["amount"]
                val currency = rawValue["currency"]
                if (amount != null && currency != null) {
                    FactValue.MoneyValue(BigDecimal(amount.toString()), currency.toString())
                } else {
                    FactValue.StringValue(rawValue.toString())
                }
            }
            else -> FactValue.StringValue(rawValue.toString())
        }
    }
}
