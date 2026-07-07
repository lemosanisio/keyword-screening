package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.RuleDefinitionEntity
import br.com.decision.infrastructure.output.persistence.mapper.RuleDefinitionMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("RuleDefinitionRepositoryImpl")
class RuleDefinitionRepositoryImplTest {

    private val jpaRepository = mockk<RuleDefinitionJpaRepository>()
    private val mapper = mockk<RuleDefinitionMapper>()
    private val repository = RuleDefinitionRepositoryImpl(jpaRepository, mapper)

    private val id = UUID.randomUUID()
    private val now = Instant.now()

    private fun sampleEntity() = RuleDefinitionEntity(
        id = id,
        code = "KEYWORD_SCREENING",
        name = "Keyword Screening",
        description = "Screens keywords",
        context = "SCREENING",
        category = "KEYWORD_SCREENING",
        supportedFacts = listOf("description"),
        supportedActions = listOf("GENERATE_ALERT"),
        status = "ACTIVE",
        createdAt = now
    )

    private fun sampleDomain() = RuleDefinition(
        id = RuleId(id),
        code = RuleCode("KEYWORD_SCREENING"),
        name = "Keyword Screening",
        description = "Screens keywords",
        context = RuleContext.SCREENING,
        category = RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("description")),
        supportedActions = listOf(Action.GENERATE_ALERT),
        status = RuleStatus.ACTIVE,
        createdAt = now
    )

    @Test
    @DisplayName("findByCode returns domain when found")
    fun findByCodeReturnsDomain() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByCode("KEYWORD_SCREENING") } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByCode(RuleCode("KEYWORD_SCREENING"))

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findByCode returns null when not found")
    fun findByCodeReturnsNull() {
        every { jpaRepository.findByCode("UNKNOWN") } returns null

        val result = repository.findByCode(RuleCode("UNKNOWN"))

        assertNull(result)
    }

    @Test
    @DisplayName("findAll returns all mapped entities")
    fun findAllReturnsAllMapped() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findAll() } returns listOf(entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findAll()

        assertEquals(listOf(domain), result)
    }

    @Test
    @DisplayName("findByContextAndCategory returns filtered results")
    fun findByContextAndCategoryReturnsFiltered() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByContextAndCategory("SCREENING", "KEYWORD_SCREENING") } returns listOf(entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByContextAndCategory(RuleContext.SCREENING, RuleCategory.KEYWORD_SCREENING)

        assertEquals(listOf(domain), result)
    }

    @Test
    @DisplayName("findByContextAndCategory with null parameters")
    fun findByContextAndCategoryWithNulls() {
        every { jpaRepository.findByContextAndCategory(null, null) } returns emptyList()

        val result = repository.findByContextAndCategory(null, null)

        assertEquals(emptyList<RuleDefinition>(), result)
    }
}
