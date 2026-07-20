package br.com.screening.infrastructure.output.persistence.repository

import br.com.screening.infrastructure.output.persistence.entity.RuleExecutionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RuleExecutionJpaRepository : JpaRepository<RuleExecutionEntity, Long> {
    fun findByTransactionIdAndRuleCode(transactionId: String, ruleCode: String): RuleExecutionEntity?
}
