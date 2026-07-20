package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.infrastructure.output.persistence.entity.RuleConfigurationEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RuleConfigurationJpaRepository : JpaRepository<RuleConfigurationEntity, UUID> {
    fun findByRuleIdAndActiveTrue(ruleId: UUID): RuleConfigurationEntity?
    fun findByRuleId(ruleId: UUID): List<RuleConfigurationEntity>
}
