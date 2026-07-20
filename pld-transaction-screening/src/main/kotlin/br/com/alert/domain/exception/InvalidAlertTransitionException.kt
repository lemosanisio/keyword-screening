package br.com.alert.domain.exception

import br.com.shared.domain.DomainException

/**
 * Lançada quando uma transição de status do alerta viola a state machine.
 * Mapeia para HTTP 422 nos handlers de entrada.
 */
class InvalidAlertTransitionException(
    message: String
) : DomainException(
    code = "TRANSICAO_ALERTA_INVALIDA",
    message = message
)
