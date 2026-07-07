package br.com.alert.application.usecase

import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.vo.AlertId
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Input port para consulta de alertas.
 * Implementado pelo AlertQueryService.
 */
interface QueryAlertUseCase {
    fun findByTransactionId(transactionId: TransactionId): List<Alert>
    fun findByRuleId(ruleId: RuleId, pageable: Pageable): Page<Alert>
    fun findById(id: AlertId): Alert?
}
