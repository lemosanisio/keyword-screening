package br.com.screening.domain.service

import br.com.screening.domain.model.Classification

/**
 * Serviço de domínio puro que determina a decisão de roteamento
 * com base na classificação e pontuação de confiança.
 */
class RoutingClassifier {

    /**
     * Determina se a transação requer revisão do analista.
     *
     * Regras:
     * - SUSPICIOUS → sempre true
     * - UNCERTAIN → sempre true
     * - FALSE_POSITIVE + confidence >= threshold → false (auto-close)
     * - FALSE_POSITIVE + confidence < threshold → true (revisão conservadora)
     */
    fun requiresAnalystReview(
        classification: Classification,
        confidence: Double,
        autoCloseThreshold: Double
    ): Boolean = when (classification) {
        Classification.SUSPICIOUS -> true
        Classification.UNCERTAIN -> true
        Classification.FALSE_POSITIVE -> confidence < autoCloseThreshold
    }
}
