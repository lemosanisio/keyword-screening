package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Testes unitários para [RoutingClassifier].
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
class RoutingClassifierTest {

    private val classifier = RoutingClassifier()
    private val threshold = 0.95

    @Test
    @DisplayName("SUSPICIOUS deve sempre requerer revisão do analista")
    fun suspiciousAlwaysRequiresReview() {
        assertEquals(true, classifier.requiresAnalystReview(Classification.SUSPICIOUS, 0.99, threshold))
    }

    @Test
    @DisplayName("UNCERTAIN deve sempre requerer revisão do analista")
    fun uncertainAlwaysRequiresReview() {
        assertEquals(true, classifier.requiresAnalystReview(Classification.UNCERTAIN, 0.99, threshold))
    }

    @Test
    @DisplayName("FALSE_POSITIVE com confidence >= threshold deve permitir auto-close")
    fun falsePositiveAboveThresholdAllowsAutoClose() {
        assertEquals(false, classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, 0.95, threshold))
    }

    @Test
    @DisplayName("FALSE_POSITIVE com confidence acima do threshold deve permitir auto-close")
    fun falsePositiveWellAboveThresholdAllowsAutoClose() {
        assertEquals(false, classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, 0.99, threshold))
    }

    @Test
    @DisplayName("FALSE_POSITIVE com confidence abaixo do threshold deve requerer revisão")
    fun falsePositiveBelowThresholdRequiresReview() {
        assertEquals(true, classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, 0.80, threshold))
    }

    @Test
    @DisplayName("FALSE_POSITIVE com confidence exatamente no threshold deve permitir auto-close")
    fun falsePositiveExactlyAtThresholdAllowsAutoClose() {
        assertEquals(false, classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, 0.95, 0.95))
    }
}
