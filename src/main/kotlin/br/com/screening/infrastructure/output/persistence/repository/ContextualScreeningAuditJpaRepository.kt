package br.com.screening.infrastructure.output.persistence.repository

import br.com.screening.infrastructure.output.persistence.entity.ContextualScreeningAuditEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContextualScreeningAuditJpaRepository : JpaRepository<ContextualScreeningAuditEntity, Long> {

    fun findByTransactionIdAndRuleId(transactionId: String, ruleId: String): ContextualScreeningAuditEntity?

    @Modifying
    @Query("UPDATE ContextualScreeningAuditEntity e SET e.analystDecision = :decision WHERE e.transactionId = :transactionId AND e.ruleId = :ruleId")
    fun updateAnalystDecision(
        @Param("transactionId") transactionId: String,
        @Param("ruleId") ruleId: String,
        @Param("decision") decision: String
    )
}
