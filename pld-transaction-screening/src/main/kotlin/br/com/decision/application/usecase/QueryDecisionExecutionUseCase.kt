package br.com.decision.application.usecase

import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import java.util.UUID

/**
 * Input port: consulta de execuções de decisão (auditoria).
 * Implementado por DecisionQueryService.
 */
interface QueryDecisionExecutionUseCase {
    fun findByTransactionId(transactionId: TransactionId, page: Int, size: Int): Page<DecisionExecution>
    fun findByRuleId(ruleId: RuleId, page: Int, size: Int): Page<DecisionExecution>
    fun findByDecision(decision: Decision, page: Int, size: Int): Page<DecisionExecution>
    fun findByTraceId(traceId: TraceId): DecisionExecution?
    fun findById(id: UUID): DecisionExecution?
}
