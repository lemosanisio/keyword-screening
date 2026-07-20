package br.com.alert.domain.port

import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.vo.AlertId
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Output port para persistência de alertas.
 * Implementado pelo adapter de infraestrutura (JPA).
 */
interface AlertRepository {
    fun save(alert: Alert): Alert
    fun findById(id: AlertId): Alert?
    fun findByTransactionId(transactionId: TransactionId): List<Alert>
    fun findByTransactionIdAndRuleId(transactionId: TransactionId, ruleId: RuleId): Alert?
    fun findByRuleId(ruleId: RuleId, pageable: Pageable): Page<Alert>
}
