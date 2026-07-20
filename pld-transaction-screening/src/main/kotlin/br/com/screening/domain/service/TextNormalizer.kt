package br.com.screening.domain.service

/**
 * Serviço de domínio puro (sem estado) responsável pela normalização de texto.
 * Pipeline: lowercase → remove acentos → remove caracteres especiais → compacta espaços.
 */
class TextNormalizer {

    /**
     * Normaliza o texto aplicando o pipeline completo.
     * Idempotente: normalize(normalize(s)) == normalize(s)
     */
    fun normalize(text: String): String {
        return text
            .let { toLowerCase(it) }
            .let { removeAccents(it) }
            .let { removeSpecialChars(it) }
            .let { compactSpaces(it) }
    }

    private fun toLowerCase(text: String): String = text.lowercase()

    private fun removeAccents(text: String): String {
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("[^\\p{ASCII}]"), "")
    }

    private fun removeSpecialChars(text: String): String = text.replace(Regex("[^a-z0-9 ]"), "")

    private fun compactSpaces(text: String): String = text.replace(Regex("\\s+"), " ").trim()
}
