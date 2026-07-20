package br.com.alert.application.service

import br.com.alert.application.usecase.QueryAlertUseCase
import br.com.alert.domain.model.Alert
import br.com.alert.domain.port.AlertRepository
import br.com.alert.domain.model.vo.AlertId
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AlertQueryService(
    private val alertRepository: AlertRepository
) : QueryAlertUseCase {

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 100
    }

    override fun findByTransactionId(transactionId: TransactionId): List<Alert> =
        alertRepository.findByTransactionId(transactionId)

    override fun findByRuleId(ruleId: RuleId, pageable: Pageable): Page<Alert> =
        alertRepository.findByRuleId(ruleId, pageable)

    /**
     * Overload with page/size integers and clamping logic.
     * Size <= 0 defaults to 20, size > 100 is clamped to 100.
     */
    fun findByRuleId(ruleId: RuleId, page: Int, size: Int): Page<Alert> {
        val clampedSize = when {
            size <= 0 -> DEFAULT_PAGE_SIZE
            size > MAX_PAGE_SIZE -> MAX_PAGE_SIZE
            else -> size
        }
        val pageable = PageRequest.of(page, clampedSize)
        return alertRepository.findByRuleId(ruleId, pageable)
    }

    override fun findById(id: AlertId): Alert? =
        alertRepository.findById(id)
}
