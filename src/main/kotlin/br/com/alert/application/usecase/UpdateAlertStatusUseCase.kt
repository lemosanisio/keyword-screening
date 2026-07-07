package br.com.alert.application.usecase

import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.model.vo.AlertId

/**
 * Input port para atualização de status de alertas.
 * Implementado pelo AlertService.
 */
interface UpdateAlertStatusUseCase {
    fun updateStatus(id: AlertId, newStatus: AlertStatus): Alert
}
