package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.infrastructure.output.persistence.mapper.RuleDefinitionMapper
import org.springframework.stereotype.Repository

@Repository
class RuleDefinitionRepositoryImpl(
    private val jpaRepository: RuleDefinitionJpaRepository,
    private val mapper: RuleDefinitionMapper
) : RuleDefinitionRepository {

    override fun findByCode(code: RuleCode): RuleDefinition? =
        jpaRepository.findByCode(code.value)?.let { mapper.toDomain(it) }

    override fun findAll(): List<RuleDefinition> =
        jpaRepository.findAll().map { mapper.toDomain(it) }

    override fun findByContextAndCategory(context: RuleContext?, category: RuleCategory?): List<RuleDefinition> =
        jpaRepository.findByContextAndCategory(context?.name, category?.name)
            .map { mapper.toDomain(it) }
}
