package br.com.screening.domain

import br.com.screening.domain.model.ScreeningResult

/**
 * Interface genérica para regras de screening.
 * Permite adicionar novas regras sem alterar o núcleo do domínio.
 */
interface ScreeningRule {
    val ruleCode: String
    fun evaluate(transactionId: String, description: String): ScreeningResult
}
