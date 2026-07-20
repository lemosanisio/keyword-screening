package br.com.alert.domain.exception

import br.com.shared.domain.DomainException

/**
 * Lançada quando um alerta não é encontrado pelo ID.
 * Mapeia para HTTP 404 nos handlers de entrada.
 */
class AlertNotFoundException(
    message: String
) : DomainException(
    code = "ALERTA_NAO_ENCONTRADO",
    message = message
)
