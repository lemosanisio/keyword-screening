package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.infrastructure.output.persistence.entity.RestrictedTermEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("RestrictedTermMapper")
class RestrictedTermMapperTest {

    private val mapper = RestrictedTermMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val now = Instant.now()
        val entity = RestrictedTermEntity(
            id = 42L,
            term = "terrorismo",
            category = Category.TERRORISM,
            active = true,
            createdAt = now,
            updatedAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(42L, domain.id)
        assertEquals("terrorismo", domain.term)
        assertEquals(Category.TERRORISM, domain.category)
        assertEquals(true, domain.active)
        assertEquals(now, domain.createdAt)
        assertEquals(now, domain.updatedAt)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val now = Instant.now()
        val domain = RestrictedTerm(
            id = 7L,
            term = "lavagem",
            category = Category.AML,
            active = false,
            createdAt = now,
            updatedAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(7L, entity.id)
        assertEquals("lavagem", entity.term)
        assertEquals(Category.AML, entity.category)
        assertEquals(false, entity.active)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.updatedAt)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves all data")
    fun roundTripPreservesData() {
        val now = Instant.now()
        val original = RestrictedTerm(
            id = 99L,
            term = "fraude",
            category = Category.FRAUD,
            active = true,
            createdAt = now,
            updatedAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
    }

    @Test
    @DisplayName("maps all category values correctly")
    fun mapsAllCategories() {
        val now = Instant.now()
        Category.entries.forEach { category ->
            val domain = RestrictedTerm(
                id = 1L,
                term = "term",
                category = category,
                active = true,
                createdAt = now,
                updatedAt = now
            )
            val result = mapper.toDomain(mapper.toEntity(domain))
            assertEquals(category, result.category)
        }
    }

    @Test
    @DisplayName("maps inactive term correctly")
    fun mapsInactiveTerm() {
        val now = Instant.now()
        val entity = RestrictedTermEntity(
            id = 1L,
            term = "sancao",
            category = Category.SANCTIONS,
            active = false,
            createdAt = now,
            updatedAt = now
        )

        val domain = mapper.toDomain(entity)
        assertEquals(false, domain.active)
    }
}
