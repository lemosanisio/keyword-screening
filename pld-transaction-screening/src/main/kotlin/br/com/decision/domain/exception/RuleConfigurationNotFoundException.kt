package br.com.decision.domain.exception

import br.com.shared.domain.DomainException

/**
 * Lançada quando uma configuração de regra não é encontrada pelo ID ou ruleCode.
 * Mapeia para HTTP 404 nos handlers de entrada.
 */
class RuleConfigurationNotFoundException(
    message: String
) : DomainException(
    code = "CONFIGURACAO_REGRA_NAO_ENCONTRADA",
    message = message
)
