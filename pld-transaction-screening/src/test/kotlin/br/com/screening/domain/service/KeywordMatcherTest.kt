package br.com.screening.domain.service

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Testes unitários para [KeywordMatcher] — exemplos concretos.
 * Requirements: 3.2, 3.3, 3.5
 */
class KeywordMatcherTest {

    private val matcher = KeywordMatcher()

    private fun term(t: String, cat: Category = Category.AML, active: Boolean = true) =
        RestrictedTerm(id = 1, term = t, category = cat, active = active, createdAt = Instant.now(), updatedAt = Instant.now())

    @Test
    @DisplayName("deve retornar match quando descrição é exatamente igual ao termo")
    fun shouldMatchExactDescription() {
        val terms = setOf(term("lavagem"))
        val result = matcher.findMatches("lavagem", terms)

        assertEquals(1, result.size)
        assertEquals("lavagem", result[0].term)
        assertEquals(Category.AML, result[0].category)
    }

    @Test
    @DisplayName("deve retornar match quando descrição contém o termo como substring")
    fun shouldMatchSubstring() {
        val terms = setOf(term("lavagem"))
        val result = matcher.findMatches("transferencia lavagem de dinheiro", terms)

        assertEquals(1, result.size)
        assertEquals("lavagem", result[0].term)
    }

    @Test
    @DisplayName("deve retornar todos os matches quando descrição contém múltiplos termos restritos")
    fun shouldMatchMultipleTerms() {
        val terms = setOf(
            term("lavagem", Category.AML),
            term("terrorismo", Category.TERRORISM),
            term("fraude", Category.FRAUD)
        )
        val result = matcher.findMatches("suspeita de lavagem e terrorismo na transacao", terms)

        assertEquals(2, result.size)
        assertTrue(result.map { it.term }.containsAll(listOf("lavagem", "terrorismo")))
    }

    @Test
    @DisplayName("deve retornar lista vazia quando descrição não contém nenhum termo restrito")
    fun shouldReturnEmptyWhenNoMatch() {
        val terms = setOf(term("lavagem"), term("terrorismo"))
        val result = matcher.findMatches("pagamento normal de fornecedor", terms)

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("deve ignorar termos com active=false mesmo que descrição os contenha")
    fun shouldIgnoreInactiveTerms() {
        val terms = setOf(
            term("lavagem", active = false),
            term("fraude", active = false)
        )
        val result = matcher.findMatches("suspeita de lavagem e fraude", terms)

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("deve retornar lista vazia quando activeTerms é vazio")
    fun shouldReturnEmptyWhenNoTerms() {
        val result = matcher.findMatches("qualquer descricao aqui", emptySet())

        assertTrue(result.isEmpty())
    }
}
