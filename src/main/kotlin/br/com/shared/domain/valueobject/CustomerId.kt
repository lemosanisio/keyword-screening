package br.com.shared.domain.valueobject

/**
 * Identificador único de um cliente.
 * Conceito de negócio compartilhado entre Screening, Decision e Alert contexts.
 */
@JvmInline
value class CustomerId(val value: String) {
    init {
        require(value.isNotBlank()) { "CustomerId não pode ser vazio" }
        require(value.length <= 64) { "CustomerId não pode exceder 64 caracteres" }
    }
}
