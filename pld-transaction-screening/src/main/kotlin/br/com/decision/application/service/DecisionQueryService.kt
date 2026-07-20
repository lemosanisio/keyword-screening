package br.com.decision.application.service

import br.com.decision.application.usecase.QueryDecisionExecutionUseCase
import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.port.DecisionExecutionRepository
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Serviço de consulta de execuções de decisão (auditoria).
 * Read-only — sem modificações de estado.
 */
@Service
@Transactional(readOnly = true)
class DecisionQueryService(
    private val decisionExecutionRepository: DecisionExecutionRepository
) : QueryDecisionExecutionUseCase {

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 100
    }

    override fun findByTransactionId(transactionId: TransactionId, page: Int, size: Int): Page<DecisionExecution> {
        val pageable = buildPageable(page, size, Sort.by(Sort.Direction.DESC, "timestamp"))
        return decisionExecutionRepository.findByTransactionId(transactionId, pageable)
    }

    override fun findByRuleId(ruleId: RuleId, page: Int, size: Int): Page<DecisionExecution> {
        val pageable = buildPageable(page, size)
        return decisionExecutionRepository.findByRuleId(ruleId, pageable)
    }

    override fun findByDecision(decision: Decision, page: Int, size: Int): Page<DecisionExecution> {
        val pageable = buildPageable(page, size)
        return decisionExecutionRepository.findByDecision(decision, pageable)
    }

    override fun findByTraceId(traceId: TraceId): DecisionExecution? {
        return decisionExecutionRepository.findByTraceId(traceId)
    }

    override fun findById(id: UUID): DecisionExecution? {
        return decisionExecutionRepository.findById(id)
    }

    private fun buildPageable(page: Int, size: Int, sort: Sort = Sort.unsorted()): PageRequest {
        val clampedSize = clampSize(size)
        return PageRequest.of(page, clampedSize, sort)
    }

    private fun clampSize(size: Int): Int {
        return if (size <= 0) DEFAULT_PAGE_SIZE else minOf(size, MAX_PAGE_SIZE)
    }
}
