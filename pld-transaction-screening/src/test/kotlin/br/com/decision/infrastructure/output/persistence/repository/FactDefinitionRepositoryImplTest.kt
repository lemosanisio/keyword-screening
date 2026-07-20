package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.vo.FactName
import br.com.decision.infrastructure.output.persistence.entity.FactDefinitionEntity
import br.com.decision.infrastructure.output.persistence.mapper.FactDefinitionMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("FactDefinitionRepositoryImpl")
class FactDefinitionRepositoryImplTest {

    private val jpaRepository = mockk<FactDefinitionJpaRepository>()
    private val mapper = mockk<FactDefinitionMapper>()
    private val repository = FactDefinitionRepositoryImpl(jpaRepository, mapper)

    private val id = UUID.randomUUID()

    private fun sampleEntity() = FactDefinitionEntity(
        id = id,
        name = "amount",
        displayName = "Amount",
        entity = "transaction",
        type = "MONEY",
        context = "SCREENING",
        source = "PIX",
        supportedOperators = listOf("GREATER_THAN"),
        enabled = true
    )

    private fun sampleDomain() = FactDefinition(
        id = id,
        name = FactName("amount"),
        displayName = "Amount",
        entity = "transaction",
        type = FactType.MONEY,
        context = RuleContext.SCREENING,
        source = "PIX",
        supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
        enabled = true
    )

    @Test
    @DisplayName("findByName returns domain when found")
    fun findByNameReturnsDomain() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByName("amount") } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByName(FactName("amount"))

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findByName returns null when not found")
    fun findByNameReturnsNull() {
        every { jpaRepository.findByName("unknown") } returns null

        val result = repository.findByName(FactName("unknown"))

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
    @DisplayName("findEnabled returns only enabled facts")
    fun findEnabledReturnsOnlyEnabled() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByEnabledTrue() } returns listOf(entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findEnabled()

        assertEquals(listOf(domain), result)
    }

    @Test
    @DisplayName("findEnabled returns empty list when none enabled")
    fun findEnabledReturnsEmpty() {
        every { jpaRepository.findByEnabledTrue() } returns emptyList()

        val result = repository.findEnabled()

        assertEquals(emptyList<FactDefinition>(), result)
    }
}
