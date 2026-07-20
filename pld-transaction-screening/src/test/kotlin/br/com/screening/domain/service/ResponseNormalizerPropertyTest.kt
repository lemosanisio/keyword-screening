package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Testes de propriedade para [ResponseNormalizer].
 *
 * Property 1: Invariante de classificação — Validates: Requirements 5.1, 5.4, 12.1, 12.10
 * Property 2: Invariante de confiança com clamping — Validates: Requirements 5.2, 5.5, 12.2
 */
class ResponseNormalizerPropertyTest {

    private val normalizer = ResponseNormalizer()
    private val validClassifications = Classification.entries.toSet()

    private fun randomStringOrNull(): String? {
        if (Random.nextBoolean()) return null
        val length = Random.nextInt(0, 50)
        return buildString { repeat(length) { append(Char(Random.nextInt(0, 0xFFFF))) } }
    }

    private fun randomDoubleInRange(): Double = Random.nextDouble(-100.0, 100.0)

    private fun randomDoubleOrNull(): Double? =
        if (Random.nextBoolean()) null else randomDoubleInRange()

    @RepeatedTest(200)
    @DisplayName("Property 1: normalizeClassification sempre retorna valor válido do enum Classification para qualquer string")
    fun normalizeClassificationAlwaysReturnsValidEnum() {
        val rawClassification = randomStringOrNull()
        val result = normalizer.normalizeClassification(rawClassification)
        assertTrue(result in validClassifications) {
            "Expected valid classification but got $result for input '$rawClassification'"
        }
    }

    @RepeatedTest(200)
    @DisplayName("Property 2: normalizeConfidence sempre retorna valor em [0.00, 1.00] para doubles no range [-100, 100]")
    fun normalizeConfidenceAlwaysReturnsValidRange() {
        val rawConfidence = randomDoubleInRange()
        val result = normalizer.normalizeConfidence(rawConfidence)
        assertTrue(result in 0.0..1.0) {
            "Expected confidence in [0, 1] but got $result for input $rawConfidence"
        }
    }

    @RepeatedTest(200)
    @DisplayName("Property 2: normalizeConfidence sempre retorna valor em [0.00, 1.00] para doubles nullable")
    fun normalizeConfidenceAlwaysReturnsValidRangeForNullable() {
        val rawConfidence = randomDoubleOrNull()
        val result = normalizer.normalizeConfidence(rawConfidence)
        assertTrue(result in 0.0..1.0) {
            "Expected confidence in [0, 1] but got $result for input $rawConfidence"
        }
    }
}
