package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.domain.model.vo.FactName
import br.com.decision.infrastructure.output.persistence.mapper.FactDefinitionMapper
import org.springframework.stereotype.Repository

@Repository
class FactDefinitionRepositoryImpl(
    private val jpaRepository: FactDefinitionJpaRepository,
    private val mapper: FactDefinitionMapper
) : FactDefinitionRepository {

    override fun findByName(name: FactName): FactDefinition? =
        jpaRepository.findByName(name.value)?.let { mapper.toDomain(it) }

    override fun findAll(): List<FactDefinition> =
        jpaRepository.findAll().map { mapper.toDomain(it) }

    override fun findEnabled(): List<FactDefinition> =
        jpaRepository.findByEnabledTrue().map { mapper.toDomain(it) }
}
