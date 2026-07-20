package br.com.decision.domain.exception

import br.com.shared.domain.DomainException

/**
 * Lançada quando uma configuração de regra contém dados inválidos
 * (ex.: factName inexistente, operator incompatível, tipo inválido).
 * Mapeia para HTTP 422 nos handlers de entrada.
 */
class InvalidConfigurationException(
    message: String
) : DomainException(
    code = "CONFIGURACAO_INVALIDA",
    message = message
)
