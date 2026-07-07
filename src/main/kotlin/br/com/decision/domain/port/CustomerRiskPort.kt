package br.com.decision.domain.port

import br.com.decision.domain.model.enums.CustomerRisk
import br.com.shared.domain.valueobject.CustomerId

/**
 * Output port para busca de nível de risco do cliente em sistema externo (Cadastro/PLD).
 * Retorna null quando o risco não pode ser determinado (cliente não encontrado ou falha na integração).
 */
interface CustomerRiskPort {
    fun getCustomerRisk(customerId: CustomerId): CustomerRisk?
}
