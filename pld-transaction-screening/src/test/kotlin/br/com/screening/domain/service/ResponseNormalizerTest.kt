package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Testes unitários para [ResponseNormalizer].
 * Requirements: 5.4, 5.5, 12.1, 12.2, 12.10
 */
class ResponseNormalizerTest {

    private val normalizer = ResponseNormalizer()

    @Test
    @DisplayName("deve retornar FALSE_POSITIVE para string válida FALSE_POSITIVE")
    fun shouldReturnFalsePositiveForValidString() {
        assertEquals(Classification.FALSE_POSITIVE, normalizer.normalizeClassification("FALSE_POSITIVE"))
    }

    @Test
    @DisplayName("deve retornar SUSPICIOUS para string válida SUSPICIOUS")
    fun shouldReturnSuspiciousForValidString() {
        assertEquals(Classification.SUSPICIOUS, normalizer.normalizeClassification("SUSPICIOUS"))
    }

    @Test
    @DisplayName("deve retornar UNCERTAIN para string válida UNCERTAIN")
    fun shouldReturnUncertainForValidString() {
        assertEquals(Classification.UNCERTAIN, normalizer.normalizeClassification("UNCERTAIN"))
    }

    @Test
    @DisplayName("deve retornar UNCERTAIN para classificação inválida")
    fun shouldReturnUncertainForInvalidClassification() {
        assertEquals(Classification.UNCERTAIN, normalizer.normalizeClassification("INVALID_VALUE"))
    }

    @Test
    @DisplayName("deve retornar UNCERTAIN para string vazia")
    fun shouldReturnUncertainForEmptyString() {
        assertEquals(Classification.UNCERTAIN, normalizer.normalizeClassification(""))
    }

    @Test
    @DisplayName("deve retornar UNCERTAIN para classificação nula")
    fun shouldReturnUncertainForNullClassification() {
        assertEquals(Classification.UNCERTAIN, normalizer.normalizeClassification(null))
    }

    @Test
    @DisplayName("deve preservar confidence dentro do range válido")
    fun shouldPreserveConfidenceInValidRange() {
        assertEquals(0.85, normalizer.normalizeConfidence(0.85))
    }

    @Test
    @DisplayName("deve preservar confidence zero")
    fun shouldPreserveConfidenceZero() {
        assertEquals(0.00, normalizer.normalizeConfidence(0.00))
    }

    @Test
    @DisplayName("deve preservar confidence um")
    fun shouldPreserveConfidenceOne() {
        assertEquals(1.00, normalizer.normalizeConfidence(1.00))
    }

    @Test
    @DisplayName("deve fazer clamp de confidence acima de 1.0 para 1.0")
    fun shouldClampConfidenceAboveOne() {
        assertEquals(1.00, normalizer.normalizeConfidence(1.5))
    }

    @Test
    @DisplayName("deve fazer clamp de confidence negativa para 0.0")
    fun shouldClampNegativeConfidence() {
        assertEquals(0.00, normalizer.normalizeConfidence(-0.5))
    }

    @Test
    @DisplayName("deve retornar 0.00 para confidence nula")
    fun shouldReturnZeroForNullConfidence() {
        assertEquals(0.00, normalizer.normalizeConfidence(null))
    }
}
