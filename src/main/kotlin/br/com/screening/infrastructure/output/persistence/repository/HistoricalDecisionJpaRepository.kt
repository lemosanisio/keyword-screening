package br.com.screening.infrastructure.output.persistence.repository

import br.com.screening.infrastructure.output.persistence.entity.HistoricalDecisionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HistoricalDecisionJpaRepository : JpaRepository<HistoricalDecisionEntity, Long> {

    fun findByKeywordOrderByCreatedAtDesc(keyword: String): List<HistoricalDecisionEntity>
}
