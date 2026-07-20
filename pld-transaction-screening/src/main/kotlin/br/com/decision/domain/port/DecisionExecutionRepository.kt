package br.com.decision.domain.port

import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

/**
 * Output port para persistência de execuções de decisão.
 * Cada DecisionExecution é imutável — nunca atualizada ou removida após criação.
 */
interface DecisionExecutionRepository {
    fun save(execution: DecisionExecution): DecisionExecution
    fun findById(id: UUID): DecisionExecution?
    fun findByTransactionIdAndRuleId(transactionId: TransactionId, ruleId: RuleId): DecisionExecution?
    fun findByTransactionId(transactionId: TransactionId, pageable: Pageable): Page<DecisionExecution>
    fun findByRuleId(ruleId: RuleId, pageable: Pageable): Page<DecisionExecution>
    fun findByDecision(decision: Decision, pageable: Pageable): Page<DecisionExecution>
    fun findByTraceId(traceId: TraceId): DecisionExecution?
}
