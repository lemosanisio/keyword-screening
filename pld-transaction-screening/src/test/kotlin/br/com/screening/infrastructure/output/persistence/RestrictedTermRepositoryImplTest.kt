package br.com.screening.infrastructure.output.persistence

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.infrastructure.output.persistence.entity.RestrictedTermEntity
import br.com.screening.infrastructure.output.persistence.mapper.RestrictedTermMapper
import br.com.screening.infrastructure.output.persistence.repository.RestrictedTermJpaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("RestrictedTermRepositoryImpl")
class RestrictedTermRepositoryImplTest {

    private val jpaRepository = mockk<RestrictedTermJpaRepository>()
    private val mapper = mockk<RestrictedTermMapper>()
    private val repository = RestrictedTermRepositoryImpl(jpaRepository, mapper)

    @Test
    @DisplayName("findAllActive delegates to JPA and maps results")
    fun findAllActiveDelegatesAndMaps() {
        val now = Instant.now()
        val entity1 = RestrictedTermEntity(id = 1L, term = "terrorismo", category = Category.TERRORISM, active = true, createdAt = now, updatedAt = now)
        val entity2 = RestrictedTermEntity(id = 2L, term = "lavagem", category = Category.AML, active = true, createdAt = now, updatedAt = now)
        val domain1 = RestrictedTerm(id = 1L, term = "terrorismo", category = Category.TERRORISM, active = true, createdAt = now, updatedAt = now)
        val domain2 = RestrictedTerm(id = 2L, term = "lavagem", category = Category.AML, active = true, createdAt = now, updatedAt = now)

        every { jpaRepository.findAllByActiveTrue() } returns listOf(entity1, entity2)
        every { mapper.toDomain(entity1) } returns domain1
        every { mapper.toDomain(entity2) } returns domain2

        val result = repository.findAllActive()

        assertEquals(listOf(domain1, domain2), result)
        verify(exactly = 1) { jpaRepository.findAllByActiveTrue() }
        verify(exactly = 1) { mapper.toDomain(entity1) }
        verify(exactly = 1) { mapper.toDomain(entity2) }
    }

    @Test
    @DisplayName("findAllActive returns empty list when no active terms")
    fun findAllActiveReturnsEmptyList() {
        every { jpaRepository.findAllByActiveTrue() } returns emptyList()

        val result = repository.findAllActive()

        assertEquals(emptyList<RestrictedTerm>(), result)
    }
}
