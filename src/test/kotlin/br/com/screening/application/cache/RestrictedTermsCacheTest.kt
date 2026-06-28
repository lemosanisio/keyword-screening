package br.com.screening.application.cache

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.repository.RestrictedTermRepository
import br.com.screening.domain.service.TextNormalizer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

class RestrictedTermsCacheTest : StringSpec({

    val normalizer = TextNormalizer()

    "initialize loads normalized terms from repository" {
        val repo = mockk<RestrictedTermRepository>()
        val terms = listOf(
            RestrictedTerm(1L, "Lavagem", Category.AML, true, Instant.now(), Instant.now()),
            RestrictedTerm(2L, "TERRORISMO", Category.TERRORISM, true, Instant.now(), Instant.now())
        )
        every { repo.findAllActive() } returns terms

        val cache = RestrictedTermsCache(repo, normalizer)
        cache.initialize()

        val activeTerms = cache.getActiveTerms()
        activeTerms.size shouldBe 2
        activeTerms.all { it.term == normalizer.normalize(it.term) } shouldBe true
        activeTerms.map { it.term }.toSet() shouldBe setOf("lavagem", "terrorismo")
    }

    "reload replaces cache with new terms" {
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
        cache.getActiveTerms().size shouldBe 1

        cache.reload()
        cache.getActiveTerms().size shouldBe 2
        cache.getActiveTerms().map { it.term }.toSet() shouldBe setOf("lavagem", "fraude")
    }

    "failure on reload preserves previous cache" {
        val repo = mockk<RestrictedTermRepository>()
        val initialTerms = listOf(
            RestrictedTerm(1L, "lavagem", Category.AML, true, Instant.now(), Instant.now())
        )
        every { repo.findAllActive() } returns initialTerms andThenThrows RuntimeException("DB unavailable")

        val cache = RestrictedTermsCache(repo, normalizer)
        cache.initialize()
        cache.getActiveTerms().size shouldBe 1

        cache.reload()
        cache.getActiveTerms().size shouldBe 1
        cache.getActiveTerms().first().term shouldBe "lavagem"
    }

    "unavailable DB on startup throws exception" {
        val repo = mockk<RestrictedTermRepository>()
        every { repo.findAllActive() } throws RuntimeException("DB unavailable")

        val cache = RestrictedTermsCache(repo, normalizer)

        shouldThrow<RuntimeException> {
            cache.initialize()
        }
    }
})
