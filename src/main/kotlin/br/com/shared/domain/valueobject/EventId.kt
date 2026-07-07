package br.com.shared.domain.valueobject

/**
 * Identificador único de um evento de domínio.
 * Garante rastreabilidade individual de cada evento publicado.
 */
@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "EventId não pode ser vazio" }
    }
}
