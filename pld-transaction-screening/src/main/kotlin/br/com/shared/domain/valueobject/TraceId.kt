package br.com.shared.domain.valueobject

/**
 * Identificador de rastreamento para correlação entre contextos e logs.
 * Permite buscar execuções, eventos e alertas em toda a cadeia de decisão.
 */
@JvmInline
value class TraceId(val value: String) {
    init {
        require(value.isNotBlank()) { "TraceId não pode ser vazio" }
    }
}
