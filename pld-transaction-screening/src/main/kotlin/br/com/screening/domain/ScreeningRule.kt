package br.com.screening.domain

import br.com.screening.domain.model.ScreeningResult
import br.com.shared.domain.valueobject.TransactionId

/**
 * Interface genérica para regras de screening.
 * Permite adicionar novas regras sem alterar o núcleo do domínio.
 */
interface ScreeningRule {
    val ruleCode: String
    fun evaluate(transactionId: TransactionId, description: String): ScreeningResult
}
