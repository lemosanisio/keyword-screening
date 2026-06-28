package br.com.screening.domain.service

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RestrictedTerm
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * Testes unitários para [KeywordMatcher] — exemplos concretos.
 * Requirements: 3.2, 3.3, 3.5
 */
class KeywordMatcherTest : StringSpec({

    val matcher = KeywordMatcher()

    fun term(t: String, cat: Category = Category.AML, active: Boolean = true) =
        RestrictedTerm(id = 1, term = t, category = cat, active = active, createdAt = Instant.now(), updatedAt = Instant.now())

    "deve retornar match quando descrição é exatamente igual ao termo" {
        val terms = setOf(term("lavagem"))
        val result = matcher.findMatches("lavagem", terms)

        result.size shouldBe 1
        result[0].term shouldBe "lavagem"
        result[0].category shouldBe Category.AML
    }

    "deve retornar match quando descrição contém o termo como substring" {
        val terms = setOf(term("lavagem"))
        val result = matcher.findMatches("transferencia lavagem de dinheiro", terms)

        result.size shouldBe 1
        result[0].term shouldBe "lavagem"
    }

    "deve retornar todos os matches quando descrição contém múltiplos termos restritos" {
        val terms = setOf(
            term("lavagem", Category.AML),
            term("terrorismo", Category.TERRORISM),
            term("fraude", Category.FRAUD)
        )
        val result = matcher.findMatches("suspeita de lavagem e terrorismo na transacao", terms)

        result.size shouldBe 2
        result.map { it.term } shouldContainExactlyInAnyOrder listOf("lavagem", "terrorismo")
    }

    "deve retornar lista vazia quando descrição não contém nenhum termo restrito" {
        val terms = setOf(
            term("lavagem"),
            term("terrorismo")
        )
        val result = matcher.findMatches("pagamento normal de fornecedor", terms)

        result.shouldBeEmpty()
    }

    "deve ignorar termos com active=false mesmo que descrição os contenha" {
        val terms = setOf(
            term("lavagem", active = false),
            term("fraude", active = false)
        )
        val result = matcher.findMatches("suspeita de lavagem e fraude", terms)

        result.shouldBeEmpty()
    }

    "deve retornar lista vazia quando activeTerms é vazio" {
        val result = matcher.findMatches("qualquer descricao aqui", emptySet())

        result.shouldBeEmpty()
    }
})
