package br.com.screening.domain.exception

import br.com.screening.domain.model.Classification
import br.com.shared.domain.DomainException

/**
 * Lançada quando uma classificação fornecida não é um valor válido do domínio.
 */
class InvalidClassificationException(
    invalidValue: String
) : DomainException(
    code = "CLASSIFICACAO_INVALIDA",
    message = "Classificação inválida: '$invalidValue'. Valores permitidos: ${Classification.entries.map { it.name }}"
)
