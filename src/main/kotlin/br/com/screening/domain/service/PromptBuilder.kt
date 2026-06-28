package br.com.screening.domain.service

import br.com.screening.domain.model.HistoricalDecision

/**
 * Serviço de domínio puro responsável por construir o prompt para o LLM.
 * O prompt inclui: descrição, keyword, decisões históricas (few-shot) e instruções de classificação.
 * Função pura de (description, keyword, decisions) — sem estado, sem side-effects.
 */
class PromptBuilder {

    /**
     * Constrói o prompt completo para envio ao LLM.
     * - Sempre inclui: description, matchedKeyword, instruções de classificação e confiança
     * - Inclui seção de few-shot apenas se decisions não estiver vazia
     */
    fun build(
        description: String,
        matchedKeyword: String,
        decisions: List<HistoricalDecision>
    ): String {
        val sb = StringBuilder()

        sb.appendLine("## Transação para Análise")
        sb.appendLine()
        sb.appendLine("**Descrição da transação:** $description")
        sb.appendLine("**Palavra-chave detectada:** $matchedKeyword")
        sb.appendLine()

        sb.appendLine("## Instruções de Classificação")
        sb.appendLine()
        sb.appendLine("Analise o contexto da transação acima e classifique-a em exatamente uma das seguintes categorias:")
        sb.appendLine()
        sb.appendLine("- **FALSE_POSITIVE**: A transação utiliza a palavra-chave em contexto legítimo, sem indícios de atividade suspeita.")
        sb.appendLine("- **SUSPICIOUS**: A transação apresenta indícios concretos de atividade suspeita relacionada à palavra-chave detectada.")
        sb.appendLine("- **UNCERTAIN**: Não é possível determinar com segurança se a transação é legítima ou suspeita com base nas informações disponíveis.")
        sb.appendLine()

        sb.appendLine("## Instruções de Resposta")
        sb.appendLine()
        sb.appendLine("Forneça sua resposta com:")
        sb.appendLine("1. **classificação**: exatamente um dos valores acima (FALSE_POSITIVE, SUSPICIOUS ou UNCERTAIN)")
        sb.appendLine("2. **justificativa**: uma explicação legível por humanos que fundamente sua decisão")
        sb.appendLine("3. **pontuação de confiança**: um valor numérico entre 0.00 e 1.00 indicando o grau de certeza da sua classificação")
        sb.appendLine()

        if (decisions.isNotEmpty()) {
            sb.appendLine("## Decisões Históricas (Exemplos)")
            sb.appendLine()
            sb.appendLine("A seguir, exemplos de decisões anteriores de analistas para a mesma palavra-chave. Use-os como referência para sua classificação:")
            sb.appendLine()
            decisions.forEach { decision ->
                sb.appendLine("- Descrição: \"${decision.description}\" → Decisão: ${decision.analystDecision.name}")
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }
}
