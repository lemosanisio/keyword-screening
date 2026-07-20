package br.com.decision.domain.resolver

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.port.CustomerRiskPort
import br.com.decision.domain.service.Fact
import br.com.decision.domain.service.FactResolver
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import java.time.Instant

/**
 * Busca Customer Risk via port externo (sistema de Cadastro/PLD).
 * Entity: Risk, sourceSystem: PLD.
 *
 * Produz o fact "customerRisk" (Enum) a partir do CustomerRiskPort.
 * Retorna emptyList() se o port retornar null (cliente não encontrado ou falha na integração).
 */
class CustomerResolver(
    private val customerRiskPort: CustomerRiskPort
) : FactResolver {

    override val producedFacts: Set<FactName> = setOf(FactName("customerRisk"))

    override val entity: String = "Risk"

    override fun resolve(event: DetectionEvent): List<Fact> {
        val risk = customerRiskPort.getCustomerRisk(event.customerId)
            ?: return emptyList()

        return listOf(
            Fact(
                name = FactName("customerRisk"),
                value = FactValue.EnumValue(risk.name),
                entity = entity,
                resolvedAt = Instant.now()
            )
        )
    }
}
