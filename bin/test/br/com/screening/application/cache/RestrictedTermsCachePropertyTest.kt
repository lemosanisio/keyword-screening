package br.com.screening.application.cache

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.repository.RestrictedTermRepository
import br.com.screening.domain.service.TextNormalizer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import kotlin.random.Random

/**
 * Property test para [RestrictedTermsCache].
 *
 * Property 6 — Validates: Requirements 4.6
 */
class RestrictedTermsCachePropertyTest {

    private val normalizer = TextNormalizer()

    private fun randomTerms(): List<RestrictedTerm> {
        val count = Random.nextInt(1, 21)
        return (1..count).map {
            val length = Random.nextInt(1, 51)
            val term = buildString { repeat(length) { append(Char(Random.nextInt(32, 127))) } }
            RestrictedTerm(
                id = it.toLong(),
                term = term,
                category = Category.AML,
                active = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }
    }

    @RepeatedTest(200)
    @DisplayName("todos os termos no cache estão normalizados após initialize")
    fun allTermsInCacheAreNormalizedAfterInitialize() {
        val terms = randomTerms()
        val repo = mockk<RestrictedTermRepository>()
        every { repo.findAllActive() } returns terms

        val cache = RestrictedTermsCache(repo, normalizer)
        cache.initialize()

        assertTrue(cache.getActiveTerms().all { t -> t.term == normalizer.normalize(t.term) }) {
            "All terms in cache should be normalized"
        }
    }
}
