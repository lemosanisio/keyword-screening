package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.infrastructure.output.persistence.entity.EntityDefinitionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EntityDefinitionJpaRepository : JpaRepository<EntityDefinitionEntity, UUID> {
    fun findByName(name: String): EntityDefinitionEntity?
}
