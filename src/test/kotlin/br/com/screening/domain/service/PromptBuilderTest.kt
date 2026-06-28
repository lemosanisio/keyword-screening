package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant

/**
 * Testes unitários para [PromptBuilder].
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
class PromptBuilderTest : StringSpec({

    val promptBuilder = PromptBuilder()

    // Requirement 3.3 — prompt com decisões históricas contém seção few-shot
    "deve incluir seção de decisões históricas quando decisions não está vazia" {
        val decisions = listOf(
            HistoricalDecision(
                id = 1L,
                keyword = "lavagem",
                description = "Transferência para conta no exterior",
                analystDecision = Classification.SUSPICIOUS,
                createdAt = Instant.now()
            ),
            HistoricalDecision(
                id = 2L,
                keyword = "lavagem",
                description = "Pagamento de lavanderia",
                analystDecision = Classification.FALSE_POSITIVE,
                createdAt = Instant.now()
            )
        )

        val prompt = promptBuilder.build("Depósito em espécie", "lavagem", decisions)

        prompt shouldContain "Decisões Históricas"
        prompt shouldContain "Transferência para conta no exterior"
        prompt shouldContain "SUSPICIOUS"
        prompt shouldContain "Pagamento de lavanderia"
        prompt shouldContain "FALSE_POSITIVE"
    }

    // Requirement 3.6 — prompt sem decisões históricas não contém seção few-shot
    "não deve incluir seção de decisões históricas quando decisions está vazia" {
        val prompt = promptBuilder.build("Depósito em espécie", "lavagem", emptyList())

        prompt shouldNotContain "Decisões Históricas"
    }

    // Requirement 3.1 — prompt sempre contém description
    "deve incluir a descrição da transação no prompt" {
        val prompt = promptBuilder.build("Pagamento de fornecedor habitual", "terrorismo", emptyList())

        prompt shouldContain "Pagamento de fornecedor habitual"
    }

    // Requirement 3.2 — prompt sempre contém matchedKeyword
    "deve incluir a matchedKeyword no prompt" {
        val prompt = promptBuilder.build("Depósito em espécie", "lavagem", emptyList())

        prompt shouldContain "lavagem"
    }

    // Requirement 3.4 — prompt contém instruções de classificação com os três valores
    "deve incluir instruções de classificação com FALSE_POSITIVE, SUSPICIOUS e UNCERTAIN" {
        val prompt = promptBuilder.build("Qualquer descrição", "keyword", emptyList())

        prompt shouldContain "FALSE_POSITIVE"
        prompt shouldContain "SUSPICIOUS"
        prompt shouldContain "UNCERTAIN"
    }

    // Requirement 3.5 — prompt contém instruções de justificativa e pontuação de confiança
    "deve incluir instruções de justificativa e pontuação de confiança" {
        val prompt = promptBuilder.build("Qualquer descrição", "keyword", emptyList())

        prompt shouldContain "justificativa"
        prompt shouldContain "confiança"
    }

    // Edge case — caracteres especiais na descrição são mantidos no prompt
    "deve manter caracteres especiais na descrição sem quebrar o prompt" {
        val descricao = "Depósito R\$ 50.000,00 via \"PIX\" — conta <offshore> & cia."
        val prompt = promptBuilder.build(descricao, "offshore", emptyList())

        prompt shouldContain descricao
        prompt shouldContain "offshore"
        prompt shouldContain "FALSE_POSITIVE"
        prompt shouldContain "SUSPICIOUS"
        prompt shouldContain "UNCERTAIN"
    }

    // Requirement 3.6 — sem decisões históricas, mantém todas as seções obrigatórias
    "sem decisões históricas deve manter descrição, keyword, instruções de classificação e confiança" {
        val prompt = promptBuilder.build("Saque em espécie", "saque", emptyList())

        prompt shouldContain "Saque em espécie"
        prompt shouldContain "saque"
        prompt shouldContain "FALSE_POSITIVE"
        prompt shouldContain "SUSPICIOUS"
        prompt shouldContain "UNCERTAIN"
        prompt shouldContain "confiança"
        prompt shouldNotContain "Decisões Históricas"
    }

    // Requirement 3.3 — cada decisão histórica aparece formatada no prompt
    "deve formatar cada decisão histórica com descrição e decisão do analista" {
        val decisions = listOf(
            HistoricalDecision(
                id = 1L,
                keyword = "financiamento",
                description = "Empréstimo para compra de imóvel",
                analystDecision = Classification.FALSE_POSITIVE,
                createdAt = Instant.now()
            )
        )

        val prompt = promptBuilder.build("Financiamento veicular", "financiamento", decisions)

        prompt shouldContain "Empréstimo para compra de imóvel"
        prompt shouldContain "FALSE_POSITIVE"
        (prompt.contains("Decisões Históricas") || prompt.contains("Exemplos")) shouldBe true
    }
})
