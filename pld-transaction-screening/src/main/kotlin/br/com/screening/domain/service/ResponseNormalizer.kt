package br.com.screening.domain.service

import br.com.screening.domain.model.Classification

/**
 * Serviço de domínio puro que normaliza a resposta do LLM:
 * - Classificação inválida ou nula → UNCERTAIN
 * - Confidence fora de [0.00, 1.00] → clamp para limite mais próximo
 * - Confidence nula → 0.00
 */
class ResponseNormalizer {

    /**
     * Normaliza a classificação: se nula ou não for um valor válido do enum, retorna UNCERTAIN.
     */
    fun normalizeClassification(rawClassification: String?): Classification {
        if (rawClassification == null) return Classification.UNCERTAIN
        return try {
            Classification.valueOf(rawClassification)
        } catch (e: IllegalArgumentException) {
            Classification.UNCERTAIN
        }
    }

    /**
     * Normaliza a confidence: nulo → 0.00, valores fora de range → clamp para [0.00, 1.00].
     */
    fun normalizeConfidence(rawConfidence: Double?): Double {
        if (rawConfidence == null) return 0.00
        return rawConfidence.coerceIn(0.00, 1.00)
    }
}
