package br.com.alert.domain.model.enums

/**
 * Status do alerta com state machine.
 * Transições válidas:
 *   OPEN → UNDER_REVIEW
 *   UNDER_REVIEW → CLOSED | FALSE_POSITIVE
 *   CLOSED → (terminal)
 *   FALSE_POSITIVE → (terminal)
 */
enum class AlertStatus {
    OPEN,
    UNDER_REVIEW,
    CLOSED,
    FALSE_POSITIVE;

    fun canTransitionTo(target: AlertStatus): Boolean = when (this) {
        OPEN -> target == UNDER_REVIEW
        UNDER_REVIEW -> target == CLOSED || target == FALSE_POSITIVE
        CLOSED -> false
        FALSE_POSITIVE -> false
    }
}
