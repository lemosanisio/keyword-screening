package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.infrastructure.output.persistence.entity.RuleDefinitionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RuleDefinitionJpaRepository : JpaRepository<RuleDefinitionEntity, UUID> {
    fun findByCode(code: String): RuleDefinitionEntity?
    fun findByContextAndCategory(context: String?, category: String?): List<RuleDefinitionEntity>
}
