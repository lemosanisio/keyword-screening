package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.infrastructure.output.persistence.entity.FactDefinitionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FactDefinitionJpaRepository : JpaRepository<FactDefinitionEntity, UUID> {
    fun findByName(name: String): FactDefinitionEntity?
    fun findByEnabledTrue(): List<FactDefinitionEntity>
}
