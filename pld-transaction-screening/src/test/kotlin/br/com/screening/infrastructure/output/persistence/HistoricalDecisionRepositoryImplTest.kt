package br.com.screening.infrastructure.output.persistence

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.infrastructure.output.persistence.entity.HistoricalDecisionEntity
import br.com.screening.infrastructure.output.persistence.mapper.HistoricalDecisionMapper
import br.com.screening.infrastructure.output.persistence.repository.HistoricalDecisionJpaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("HistoricalDecisionRepositoryImpl")
class HistoricalDecisionRepositoryImplTest {

    private val jpaRepository = mockk<HistoricalDecisionJpaRepository>()
    private val mapper = mockk<HistoricalDecisionMapper>()
    private val repository = HistoricalDecisionRepositoryImpl(jpaRepository, mapper)

    private val now = Instant.now()

    @Test
    @DisplayName("findByKeyword returns mapped domain list")
    fun findByKeywordReturnsMappedDomainList() {
        val entity1 = HistoricalDecisionEntity(id = 1L, keyword = "terrorismo", description = "desc1", analystDecision = "SUSPICIOUS", createdAt = now)
        val entity2 = HistoricalDecisionEntity(id = 2L, keyword = "terrorismo", description = "desc2", analystDecision = "FALSE_POSITIVE", createdAt = now)
        val domain1 = HistoricalDecision(id = 1L, keyword = "terrorismo", description = "desc1", analystDecision = Classification.SUSPICIOUS, createdAt = now)
        val domain2 = HistoricalDecision(id = 2L, keyword = "terrorismo", description = "desc2", analystDecision = Classification.FALSE_POSITIVE, createdAt = now)

        every { jpaRepository.findByKeywordOrderByCreatedAtDesc("terrorismo") } returns listOf(entity1, entity2)
        every { mapper.toDomain(entity1) } returns domain1
        every { mapper.toDomain(entity2) } returns domain2

        val result = repository.findByKeyword("terrorismo")

        assertEquals(listOf(domain1, domain2), result)
        verify(exactly = 1) { jpaRepository.findByKeywordOrderByCreatedAtDesc("terrorismo") }
    }

    @Test
    @DisplayName("findByKeyword returns empty list when no decisions found")
    fun findByKeywordReturnsEmptyList() {
        every { jpaRepository.findByKeywordOrderByCreatedAtDesc("unknown") } returns emptyList()

        val result = repository.findByKeyword("unknown")

        assertEquals(emptyList<HistoricalDecision>(), result)
    }

    @Test
    @DisplayName("save persists and returns mapped domain")
    fun savePersistsAndReturnsMappedDomain() {
        val domain = HistoricalDecision(id = null, keyword = "fraude", description = "desc", analystDecision = Classification.SUSPICIOUS, createdAt = now)
        val entity = HistoricalDecisionEntity(id = 0L, keyword = "fraude", description = "desc", analystDecision = "SUSPICIOUS", createdAt = now)
        val savedEntity = HistoricalDecisionEntity(id = 5L, keyword = "fraude", description = "desc", analystDecision = "SUSPICIOUS", createdAt = now)
        val savedDomain = domain.copy(id = 5L)

        every { mapper.toEntity(domain) } returns entity
        every { jpaRepository.save(entity) } returns savedEntity
        every { mapper.toDomain(savedEntity) } returns savedDomain

        val result = repository.save(domain)

        assertEquals(savedDomain, result)
        verify(exactly = 1) { jpaRepository.save(entity) }
    }
}
