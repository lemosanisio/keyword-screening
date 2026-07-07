package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Testes unitários para [PromptBuilder].
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
class PromptBuilderTest {

    private val promptBuilder = PromptBuilder()

    @Test
    @DisplayName("deve incluir seção de decisões históricas quando decisions não está vazia")
    fun shouldIncludeHistoricalDecisionsSection() {
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

        assertTrue(prompt.contains("Decisões Históricas"))
        assertTrue(prompt.contains("Transferência para conta no exterior"))
        assertTrue(prompt.contains("SUSPICIOUS"))
        assertTrue(prompt.contains("Pagamento de lavanderia"))
        assertTrue(prompt.contains("FALSE_POSITIVE"))
    }

    @Test
    @DisplayName("não deve incluir seção de decisões históricas quando decisions está vazia")
    fun shouldNotIncludeHistoricalDecisionsSectionWhenEmpty() {
        val prompt = promptBuilder.build("Depósito em espécie", "lavagem", emptyList())

        assertTrue(!prompt.contains("Decisões Históricas"))
    }

    @Test
    @DisplayName("deve incluir a descrição da transação no prompt")
    fun shouldIncludeTransactionDescription() {
        val prompt = promptBuilder.build("Pagamento de fornecedor habitual", "terrorismo", emptyList())

        assertTrue(prompt.contains("Pagamento de fornecedor habitual"))
    }

    @Test
    @DisplayName("deve incluir a matchedKeyword no prompt")
    fun shouldIncludeMatchedKeyword() {
        val prompt = promptBuilder.build("Depósito em espécie", "lavagem", emptyList())

        assertTrue(prompt.contains("lavagem"))
    }

    @Test
    @DisplayName("deve incluir instruções de classificação com FALSE_POSITIVE, SUSPICIOUS e UNCERTAIN")
    fun shouldIncludeClassificationInstructions() {
        val prompt = promptBuilder.build("Qualquer descrição", "keyword", emptyList())

        assertTrue(prompt.contains("FALSE_POSITIVE"))
        assertTrue(prompt.contains("SUSPICIOUS"))
        assertTrue(prompt.contains("UNCERTAIN"))
    }

    @Test
    @DisplayName("deve incluir instruções de justificativa e pontuação de confiança")
    fun shouldIncludeJustificationAndConfidenceInstructions() {
        val prompt = promptBuilder.build("Qualquer descrição", "keyword", emptyList())

        assertTrue(prompt.contains("justificativa"))
        assertTrue(prompt.contains("confiança"))
    }

    @Test
    @DisplayName("deve manter caracteres especiais na descrição sem quebrar o prompt")
    fun shouldPreserveSpecialCharactersInDescription() {
        val descricao = "Depósito R\$ 50.000,00 via \"PIX\" — conta <offshore> & cia."
        val prompt = promptBuilder.build(descricao, "offshore", emptyList())

        assertTrue(prompt.contains(descricao))
        assertTrue(prompt.contains("offshore"))
        assertTrue(prompt.contains("FALSE_POSITIVE"))
        assertTrue(prompt.contains("SUSPICIOUS"))
        assertTrue(prompt.contains("UNCERTAIN"))
    }

    @Test
    @DisplayName("sem decisões históricas deve manter descrição, keyword, instruções de classificação e confiança")
    fun shouldKeepAllMandatorySectionsWithoutDecisions() {
        val prompt = promptBuilder.build("Saque em espécie", "saque", emptyList())

        assertTrue(prompt.contains("Saque em espécie"))
        assertTrue(prompt.contains("saque"))
        assertTrue(prompt.contains("FALSE_POSITIVE"))
        assertTrue(prompt.contains("SUSPICIOUS"))
        assertTrue(prompt.contains("UNCERTAIN"))
        assertTrue(prompt.contains("confiança"))
        assertTrue(!prompt.contains("Decisões Históricas"))
    }

    @Test
    @DisplayName("deve formatar cada decisão histórica com descrição e decisão do analista")
    fun shouldFormatEachHistoricalDecision() {
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

        assertTrue(prompt.contains("Empréstimo para compra de imóvel"))
        assertTrue(prompt.contains("FALSE_POSITIVE"))
        assertEquals(true, prompt.contains("Decisões Históricas") || prompt.contains("Exemplos"))
    }
}
