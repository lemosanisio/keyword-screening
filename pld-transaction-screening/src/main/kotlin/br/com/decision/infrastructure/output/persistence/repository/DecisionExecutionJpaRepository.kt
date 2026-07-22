package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.infrastructure.output.persistence.entity.DecisionExecutionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DecisionExecutionJpaRepository : JpaRepository<DecisionExecutionEntity, UUID> {
    fun findTopByTransactionIdAndRuleIdOrderByCreatedAtDesc(transactionId: String, ruleId: UUID): DecisionExecutionEntity?
    fun findByTransactionId(transactionId: String, pageable: Pageable): Page<DecisionExecutionEntity>
    fun findByRuleId(ruleId: UUID, pageable: Pageable): Page<DecisionExecutionEntity>
    fun findByDecision(decision: String, pageable: Pageable): Page<DecisionExecutionEntity>
    fun findByTraceId(traceId: String): DecisionExecutionEntity?
    fun findByEvaluationId(evaluationId: String): List<DecisionExecutionEntity>
}
