package br.com.screening.infrastructure.output.persistence

import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.HistoricalDecisionRepository
import br.com.screening.infrastructure.output.persistence.mapper.HistoricalDecisionMapper
import br.com.screening.infrastructure.output.persistence.repository.HistoricalDecisionJpaRepository
import org.springframework.stereotype.Repository

@Repository
class HistoricalDecisionRepositoryImpl(
    private val jpaRepository: HistoricalDecisionJpaRepository,
    private val mapper: HistoricalDecisionMapper
) : HistoricalDecisionRepository {

    override fun findByKeyword(keyword: String): List<HistoricalDecision> =
        jpaRepository.findByKeywordOrderByCreatedAtDesc(keyword).map(mapper::toDomain)

    override fun save(decision: HistoricalDecision): HistoricalDecision {
        val entity = mapper.toEntity(decision)
        return mapper.toDomain(jpaRepository.save(entity))
    }
}
