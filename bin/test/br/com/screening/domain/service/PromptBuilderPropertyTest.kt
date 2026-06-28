package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.instant
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import java.time.Instant

/**
 * Testes de propriedade para [PromptBuilder].
 *
 * **Property 4: Completude do prompt**
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
 *
 * Verifica que para QUALQUER combinação válida de inputs (description, matchedKeyword, decisions),
 * o prompt construído contém todos os componentes obrigatórios.
 */
class PromptBuilderPropertyTest : StringSpec({

    val promptBuilder = PromptBuilder()

    /** Gera uma HistoricalDecision arbitrária com campos não vazios. */
    val arbHistoricalDecision: Arb<HistoricalDecision> = Arb.string(1..50)
        .filter { it.isNotBlank() }
        .map { desc ->
            HistoricalDecision(
                id = 1L,
                keyword = "keyword",
                description = desc,
                analystDecision = Classification.entries.random(),
                createdAt = Instant.now()
            )
        }

    // Property 4.1: O prompt SEMPRE contém a description da transação
    // Validates: Requirements 3.1
    "Property 4: prompt sempre contém a description para qualquer input válido" {
        forAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.list(arbHistoricalDecision, 0..5)
        ) { description, keyword, decisions ->
            val prompt = promptBuilder.build(description, keyword, decisions)
            prompt.contains(description)
        }
    }

    // Property 4.2: O prompt SEMPRE contém a matchedKeyword
    // Validates: Requirements 3.2
    "Property 4: prompt sempre contém a matchedKeyword para qualquer input válido" {
        forAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.list(arbHistoricalDecision, 0..5)
        ) { description, keyword, decisions ->
            val prompt = promptBuilder.build(description, keyword, decisions)
            prompt.contains(keyword)
        }
    }

    // Property 4.3: O prompt SEMPRE contém instruções de classificação (FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN)
    // Validates: Requirements 3.4
    "Property 4: prompt sempre contém instruções de classificação com os três valores" {
        forAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.list(arbHistoricalDecision, 0..5)
        ) { description, keyword, decisions ->
            val prompt = promptBuilder.build(description, keyword, decisions)
            prompt.contains("FALSE_POSITIVE") &&
                prompt.contains("SUSPICIOUS") &&
                prompt.contains("UNCERTAIN")
        }
    }

    // Property 4.4: Se decisions não está vazia, o prompt contém TODAS as descrições das decisões históricas
    // Validates: Requirements 3.3
    "Property 4: prompt contém todas as decisões históricas quando a lista não está vazia" {
        forAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.list(arbHistoricalDecision, 1..5)
        ) { description, keyword, decisions ->
            val prompt = promptBuilder.build(description, keyword, decisions)
            decisions.all { decision ->
                prompt.contains(decision.description)
            }
        }
    }

    // Property 4.5: Se decisions está vazia, o prompt NÃO contém "Decisões Históricas"
    // Validates: Requirements 3.6
    "Property 4: prompt não contém seção de decisões históricas quando lista está vazia" {
        forAll(
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.string(1..50).filter { it.isNotBlank() }
        ) { description, keyword ->
            val prompt = promptBuilder.build(description, keyword, emptyList())
            !prompt.contains("Decisões Históricas")
        }
    }
})
