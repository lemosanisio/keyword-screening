package br.com.decision.domain.exception

import br.com.shared.domain.DomainException

/**
 * Lançada quando ocorre um erro irrecuperável na resolução de fatos
 * pelo ContextBuilder ou FactResolver.
 * Mapeia para HTTP 422 nos handlers de entrada.
 */
class FactResolutionException(
    message: String
) : DomainException(
    code = "ERRO_RESOLUCAO_FATO",
    message = message
)
