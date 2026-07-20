package br.com.alert.application.usecase

import br.com.alert.domain.model.Alert
import br.com.decision.domain.event.DecisionMadeEvent

/**
 * Input port para criação de alertas.
 * Implementado pelo AlertService.
 */
interface CreateAlertUseCase {
    fun createAlertIfNotExists(event: DecisionMadeEvent): Alert?
}
