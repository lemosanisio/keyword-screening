package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.EntityDefinition
import br.com.decision.domain.port.EntityDefinitionRepository
import br.com.decision.infrastructure.output.persistence.mapper.EntityDefinitionMapper
import org.springframework.stereotype.Repository

@Repository
class EntityDefinitionRepositoryImpl(
    private val jpaRepository: EntityDefinitionJpaRepository,
    private val mapper: EntityDefinitionMapper
) : EntityDefinitionRepository {

    override fun findByName(name: String): EntityDefinition? =
        jpaRepository.findByName(name)?.let { mapper.toDomain(it) }

    override fun findAll(): List<EntityDefinition> =
        jpaRepository.findAll().map { mapper.toDomain(it) }
}
