package br.com.decision.domain.exception

import br.com.shared.domain.DomainException

/**
 * Lançada quando se tenta ativar uma configuração de regra enquanto
 * já existe outra configuração ativa para a mesma ruleId.
 * Mapeia para HTTP 409 nos handlers de entrada.
 */
class DuplicateActiveConfigException(
    message: String
) : DomainException(
    code = "CONFIGURACAO_ATIVA_DUPLICADA",
    message = message
)
