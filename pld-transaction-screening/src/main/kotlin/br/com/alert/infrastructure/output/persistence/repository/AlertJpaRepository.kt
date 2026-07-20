package br.com.alert.infrastructure.output.persistence.repository

import br.com.alert.infrastructure.output.persistence.entity.AlertEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AlertJpaRepository : JpaRepository<AlertEntity, UUID> {
    fun findByTransactionId(transactionId: String): List<AlertEntity>
    fun findByTransactionIdAndRuleId(transactionId: String, ruleId: UUID): AlertEntity?
    fun findByRuleId(ruleId: UUID, pageable: Pageable): Page<AlertEntity>
}
