package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Testes de propriedade para [RoutingClassifier].
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.6, 12.3, 12.4, 12.5
 */
class RoutingClassifierPropertyTest {

    private val classifier = RoutingClassifier()

    @RepeatedTest(200)
    @DisplayName("SUSPICIOUS sempre requer revisão do analista independente de confidence e threshold")
    fun suspiciousAlwaysRequiresReview() {
        val confidence = Random.nextDouble(0.0, 1.0)
        val threshold = Random.nextDouble(0.0, 1.0)
        assertEquals(true, classifier.requiresAnalystReview(Classification.SUSPICIOUS, confidence, threshold))
    }

    @RepeatedTest(200)
    @DisplayName("UNCERTAIN sempre requer revisão do analista independente de confidence e threshold")
    fun uncertainAlwaysRequiresReview() {
        val confidence = Random.nextDouble(0.0, 1.0)
        val threshold = Random.nextDouble(0.0, 1.0)
        assertEquals(true, classifier.requiresAnalystReview(Classification.UNCERTAIN, confidence, threshold))
    }

    @RepeatedTest(200)
    @DisplayName("FALSE_POSITIVE com confidence >= threshold não requer revisão (auto-close)")
    fun falsePositiveAboveThresholdDoesNotRequireReview() {
        val threshold = Random.nextDouble(0.0, 1.0)
        val confidence = threshold + Random.nextDouble(0.0, 1.0 - threshold)

        assertEquals(false, classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, confidence, threshold))
    }

    @RepeatedTest(200)
    @DisplayName("FALSE_POSITIVE com confidence < threshold requer revisão conservadora")
    fun falsePositiveBelowThresholdRequiresReview() {
        val threshold = Random.nextDouble(0.01, 1.0)
        val confidence = Random.nextDouble(0.0, threshold - 0.0001)

        assertEquals(true, classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, confidence, threshold))
    }

    @RepeatedTest(200)
    @DisplayName("routing é determinístico para qualquer combinação de classificação, confidence e threshold")
    fun routingIsDeterministic() {
        val classification = Classification.entries[Random.nextInt(Classification.entries.size)]
        val confidence = Random.nextDouble(0.0, 1.0)
        val threshold = Random.nextDouble(0.0, 1.0)

        val result = classifier.requiresAnalystReview(classification, confidence, threshold)
        val expected = when (classification) {
            Classification.SUSPICIOUS -> true
            Classification.UNCERTAIN -> true
            Classification.FALSE_POSITIVE -> confidence < threshold
        }
        assertEquals(expected, result)
    }
}
