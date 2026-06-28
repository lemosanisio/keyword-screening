package br.com.screening.domain.service

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import java.time.Instant

/**
 * Testes de propriedade para [KeywordMatcher].
 *
 * Property 3 — Validates: Requirements 1.1, 1.2, 2.6, 3.2, 3.3
 * Property 4 — Validates: Requirements 1.3, 3.1
 * Property 5 — Validates: Requirements 3.5
 */
class KeywordMatcherPropertyTest : StringSpec({

    val matcher = KeywordMatcher()
    val normalizer = TextNormalizer()
    val now = Instant.now()

    /** Gera um Category aleatório. */
    val arbCategory = Arb.element(Category.entries)

    /**
     * Gera um termo normalizado não vazio — apenas [a-z] para garantir
     * que pode ser embutido e detectado em qualquer descrição ASCII.
     */
    val arbNormalizedTerm: Arb<String> = Arb.string(5..15)
        .map { it.lowercase().filter { c -> c in 'a'..'z' } }
        .filter { it.isNotEmpty() }

    /** Constrói um [RestrictedTerm] ativo a partir de um termo string. */
    fun activeTerm(id: Long, term: String, category: Category) =
        RestrictedTerm(id = id, term = term, category = category, active = true, createdAt = now, updatedAt = now)

    /** Constrói um [RestrictedTerm] inativo a partir de um termo string. */
    fun inactiveTerm(id: Long, term: String, category: Category) =
        RestrictedTerm(id = id, term = term, category = category, active = false, createdAt = now, updatedAt = now)

    // Feature: mf09-keyword-screening, Property 3: Detecção invariante a variações de formatação
    /**
     * Para qualquer termo normalizado embutido em uma descrição,
     * após normalizar a descrição com TextNormalizer, findMatches deve encontrá-lo.
     *
     * Validates: Requirements 1.1, 1.2, 2.6, 3.2, 3.3
     */
    "Property 3: termo normalizado embutido na descrição é sempre detectado após normalização" {
        forAll(arbNormalizedTerm, arbCategory) { term, category ->
            val description = "prefix $term suffix"
            val normalizedDescription = normalizer.normalize(description)
            val restrictedTerm = activeTerm(1L, term, category)
            val result = matcher.findMatches(normalizedDescription, setOf(restrictedTerm))
            result.isNotEmpty() && result.any { it.term == term }
        }
    }

    // Feature: mf09-keyword-screening, Property 4: Ausência de termos implica matched=false
    /**
     * Para qualquer descrição que não contenha nenhum dos termos ativos,
     * findMatches retorna lista vazia.
     *
     * Validates: Requirements 1.3, 3.1
     */
    "Property 4: ausencia de termos restritos na descricao implica matches vazio" {
        forAll(arbNormalizedTerm, arbNormalizedTerm, arbCategory) { descPart, termPart, category ->
            // Ensure the term is NOT contained in the description by using disjoint strings
            val description = "aaa${descPart}bbb"
            val term = "zzz${termPart}yyy"
            // Only proceed if term is truly absent from description
            if (description.contains(term)) {
                true // vacuously true — skip this case
            } else {
                val restrictedTerm = activeTerm(1L, term, category)
                val result = matcher.findMatches(description, setOf(restrictedTerm))
                result.isEmpty()
            }
        }
    }

    // Feature: mf09-keyword-screening, Property 5: Termos inativos não produzem matches
    /**
     * Para qualquer descrição contendo termos marcados como active=false,
     * findMatches retorna lista vazia.
     *
     * Validates: Requirements 3.5
     */
    "Property 5: termos inativos nao produzem matches mesmo que presentes na descricao" {
        forAll(arbNormalizedTerm, arbCategory) { term, category ->
            val description = "prefix $term suffix"
            val restrictedTerm = inactiveTerm(1L, term, category)
            val result = matcher.findMatches(description, setOf(restrictedTerm))
            result.isEmpty()
        }
    }
})
