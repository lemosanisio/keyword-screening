package br.com.screening.application.cache

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.repository.RestrictedTermRepository
import br.com.screening.domain.service.TextNormalizer
import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

/**
 * Property test para [RestrictedTermsCache].
 *
 * Property 6 — Validates: Requirements 4.6
 */
// Feature: mf09-keyword-screening, Property 6: Termos no cache estão normalizados
class RestrictedTermsCachePropertyTest : StringSpec({

    val normalizer = TextNormalizer()

    // Feature: mf09-keyword-screening, Property 6: Termos no cache estão normalizados
    "todos os termos no cache estão normalizados após initialize" {
        val arbRestrictedTerm = Arb.string(1..50).map { term ->
            RestrictedTerm(
                id = 1L,
                term = term,
                category = Category.AML,
                active = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }

        forAll(Arb.list(arbRestrictedTerm, 1..20)) { terms ->
            val repo = mockk<RestrictedTermRepository>()
            every { repo.findAllActive() } returns terms

            val cache = RestrictedTermsCache(repo, normalizer)
            cache.initialize()

            cache.getActiveTerms().all { t ->
                t.term == normalizer.normalize(t.term)
            }
        }
    }
})
