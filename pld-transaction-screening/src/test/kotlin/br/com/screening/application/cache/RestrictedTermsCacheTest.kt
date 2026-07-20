package br.com.screening.application.cache

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.repository.RestrictedTermRepository
import br.com.screening.domain.service.TextNormalizer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class RestrictedTermsCacheTest {

    private val normalizer = TextNormalizer()

    @Test
    @DisplayName("initialize loads normalized terms from repository")
    fun initializeLoadsNormalizedTerms() {
        val repo = mockk<RestrictedTermRepository>()
        val terms = listOf(
            RestrictedTerm(1L, "Lavagem", Category.AML, true, Instant.now(), Instant.now()),
            RestrictedTerm(2L, "TERRORISMO", Category.TERRORISM, true, Instant.now(), Instant.now())
        )
        every { repo.findAllActive() } returns terms

        val cache = RestrictedTermsCache(repo, normalizer)
        cache.initialize()

        val activeTerms = cache.getActiveTerms()
        assertEquals(2, activeTerms.size)
        assertTrue(activeTerms.all { it.term == normalizer.normalize(it.term) })
        assertEquals(setOf("lavagem", "terrorismo"), activeTerms.map { it.term }.toSet())
    }

    @Test
    @DisplayName("reload replaces cache with new terms")
    fun reloadReplacesCacheWithNewTerms() {
        val repo = mockk<RestrictedTermRepository>()
        val initialTerms = listOf(
            RestrictedTerm(1L, "lavagem", Category.AML, true, Instant.now(), Instant.now())
        )
        val updatedTerms = listOf(
            RestrictedTerm(1L, "lavagem", Category.AML, true, Instant.now(), Instant.now()),
            RestrictedTerm(2L, "fraude", Category.FRAUD, true, Instant.now(), Instant.now())
        )
        every { repo.findAllActive() } returns initialTerms andThen updatedTerms

        val cache = RestrictedTermsCache(repo, normalizer)
        cache.initialize()
        assertEquals(1, cache.getActiveTerms().size)

        cache.reload()
        assertEquals(2, cache.getActiveTerms().size)
        assertEquals(setOf("lavagem", "fraude"), cache.getActiveTerms().map { it.term }.toSet())
    }

    @Test
    @DisplayName("failure on reload preserves previous cache")
    fun failureOnReloadPreservesPreviousCache() {
        val repo = mockk<RestrictedTermRepository>()
        val initialTerms = listOf(
            RestrictedTerm(1L, "lavagem", Category.AML, true, Instant.now(), Instant.now())
        )
        every { repo.findAllActive() } returns initialTerms andThenThrows RuntimeException("DB unavailable")

        val cache = RestrictedTermsCache(repo, normalizer)
        cache.initialize()
        assertEquals(1, cache.getActiveTerms().size)

        cache.reload()
        assertEquals(1, cache.getActiveTerms().size)
        assertEquals("lavagem", cache.getActiveTerms().first().term)
    }

    @Test
    @DisplayName("unavailable DB on startup throws exception")
    fun unavailableDbOnStartupThrowsException() {
        val repo = mockk<RestrictedTermRepository>()
        every { repo.findAllActive() } throws RuntimeException("DB unavailable")

        val cache = RestrictedTermsCache(repo, normalizer)

        assertThrows(RuntimeException::class.java) {
            cache.initialize()
        }
    }
}
