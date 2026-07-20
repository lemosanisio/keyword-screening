package br.com.shared.domain.valueobject

/**
 * Identificador único de uma transação PIX.
 * Conceito de negócio compartilhado entre Screening, Decision e Alert contexts.
 */
@JvmInline
value class TransactionId(val value: String) {
    init {
        require(value.isNotBlank()) { "TransactionId não pode ser vazio" }
        require(value.length <= 100) { "TransactionId não pode exceder 100 caracteres" }
    }
}
