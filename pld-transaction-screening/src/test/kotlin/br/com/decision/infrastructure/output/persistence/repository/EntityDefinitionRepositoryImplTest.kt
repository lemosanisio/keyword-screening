package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.EntityDefinition
import br.com.decision.domain.model.vo.FactName
import br.com.decision.infrastructure.output.persistence.entity.EntityDefinitionEntity
import br.com.decision.infrastructure.output.persistence.mapper.EntityDefinitionMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("EntityDefinitionRepositoryImpl")
class EntityDefinitionRepositoryImplTest {

    private val jpaRepository = mockk<EntityDefinitionJpaRepository>()
    private val mapper = mockk<EntityDefinitionMapper>()
    private val repository = EntityDefinitionRepositoryImpl(jpaRepository, mapper)

    private val id = UUID.randomUUID()

    private fun sampleEntity() = EntityDefinitionEntity(
        id = id,
        name = "transaction",
        displayName = "Transaction",
        sourceSystem = "PIX",
        factNames = listOf("amount", "description")
    )

    private fun sampleDomain() = EntityDefinition(
        id = id,
        name = "transaction",
        displayName = "Transaction",
        sourceSystem = "PIX",
        factNames = listOf(FactName("amount"), FactName("description"))
    )

    @Test
    @DisplayName("findByName returns domain when found")
    fun findByNameReturnsDomain() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByName("transaction") } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByName("transaction")

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findByName returns null when not found")
    fun findByNameReturnsNull() {
        every { jpaRepository.findByName("unknown") } returns null

        val result = repository.findByName("unknown")

        assertNull(result)
    }

    @Test
    @DisplayName("findAll returns all mapped entities")
    fun findAllReturnsAllMappedEntities() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findAll() } returns listOf(entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findAll()

        assertEquals(listOf(domain), result)
    }

    @Test
    @DisplayName("findAll returns empty list when no entities")
    fun findAllReturnsEmptyList() {
        every { jpaRepository.findAll() } returns emptyList()

        val result = repository.findAll()

        assertEquals(emptyList<EntityDefinition>(), result)
    }
}
