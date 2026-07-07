package br.com.screening.domain.service

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import kotlin.random.Random

/**
 * Testes de propriedade para [KeywordMatcher].
 *
 * Property 3 — Validates: Requirements 1.1, 1.2, 2.6, 3.2, 3.3
 * Property 4 — Validates: Requirements 1.3, 3.1
 * Property 5 — Validates: Requirements 3.5
 */
class KeywordMatcherPropertyTest {

    private val matcher = KeywordMatcher()
    private val normalizer = TextNormalizer()
    private val now = Instant.now()

    private fun randomCategory(): Category = Category.entries[Random.nextInt(Category.entries.size)]

    private fun randomNormalizedTerm(): String {
        val length = Random.nextInt(5, 16)
        return buildString { repeat(length) { append(('a'..'z').random()) } }
    }

    private fun activeTerm(id: Long, term: String, category: Category) =
        RestrictedTerm(id = id, term = term, category = category, active = true, createdAt = now, updatedAt = now)

    private fun inactiveTerm(id: Long, term: String, category: Category) =
        RestrictedTerm(id = id, term = term, category = category, active = false, createdAt = now, updatedAt = now)

    @RepeatedTest(200)
    @DisplayName("Property 3: termo normalizado embutido na descrição é sempre detectado após normalização")
    fun normalizedTermEmbeddedInDescriptionIsAlwaysDetected() {
        val term = randomNormalizedTerm()
        val category = randomCategory()
        val description = "prefix $term suffix"
        val normalizedDescription = normalizer.normalize(description)
        val restrictedTerm = activeTerm(1L, term, category)
        val result = matcher.findMatches(normalizedDescription, setOf(restrictedTerm))

        assertTrue(result.isNotEmpty()) { "Expected match for term '$term' in '$normalizedDescription'" }
        assertTrue(result.any { it.term == term }) { "Expected to find term '$term' in results" }
    }

    @RepeatedTest(200)
    @DisplayName("Property 4: ausência de termos restritos na descrição implica matches vazio")
    fun absenceOfRestrictedTermsImpliesEmptyMatches() {
        val descPart = randomNormalizedTerm()
        val termPart = randomNormalizedTerm()
        val description = "aaa${descPart}bbb"
        val term = "zzz${termPart}yyy"

        if (description.contains(term)) return // skip case where accidentally contained

        val restrictedTerm = activeTerm(1L, term, randomCategory())
        val result = matcher.findMatches(description, setOf(restrictedTerm))

        assertTrue(result.isEmpty()) { "Expected no matches for term '$term' in '$description'" }
    }

    @RepeatedTest(200)
    @DisplayName("Property 5: termos inativos não produzem matches mesmo que presentes na descrição")
    fun inactiveTermsDoNotProduceMatches() {
        val term = randomNormalizedTerm()
        val description = "prefix $term suffix"
        val restrictedTerm = inactiveTerm(1L, term, randomCategory())
        val result = matcher.findMatches(description, setOf(restrictedTerm))

        assertTrue(result.isEmpty()) { "Expected no matches for inactive term '$term'" }
    }
}
