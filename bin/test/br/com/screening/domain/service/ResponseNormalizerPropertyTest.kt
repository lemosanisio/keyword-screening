package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

/**
 * Testes de propriedade para [ResponseNormalizer].
 *
 * **Property 1: Invariante de classificação**
 * **Validates: Requirements 5.1, 5.4, 12.1, 12.10**
 *
 * **Property 2: Invariante de confiança com clamping**
 * **Validates: Requirements 5.2, 5.5, 12.2**
 */
class ResponseNormalizerPropertyTest : StringSpec({

    val normalizer = ResponseNormalizer()
    val validClassifications = Classification.entries.toSet()

    // Property 1: Invariante de classificação
    /**
     * Para QUALQUER string arbitrária (incluindo nula, vazia, com caracteres especiais,
     * unicode, etc.), normalizeClassification() SEMPRE retorna um valor válido do enum
     * Classification ∈ {FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN}.
     *
     * **Validates: Requirements 5.1, 5.4, 12.1, 12.10**
     */
    "Property 1: normalizeClassification sempre retorna valor válido do enum Classification para qualquer string" {
        forAll(Arb.string().orNull()) { rawClassification ->
            val result = normalizer.normalizeClassification(rawClassification)
            result in validClassifications
        }
    }

    // Property 2: Invariante de confiança com clamping
    /**
     * Para qualquer Double arbitrário (incluindo negativos, valores muito grandes e null),
     * `normalizeConfidence()` sempre retorna um valor no intervalo [0.00, 1.00].
     *
     * **Validates: Requirements 5.2, 5.5, 12.2**
     */
    "Property 2: normalizeConfidence sempre retorna valor em [0.00, 1.00] para doubles no range [-100, 100]" {
        forAll(Arb.double(-100.0..100.0)) { rawConfidence ->
            val result = normalizer.normalizeConfidence(rawConfidence)
            result >= 0.00 && result <= 1.00
        }
    }

    "Property 2: normalizeConfidence sempre retorna valor em [0.00, 1.00] para doubles nullable" {
        forAll(Arb.double(-100.0..100.0).orNull()) { rawConfidence ->
            val result = normalizer.normalizeConfidence(rawConfidence)
            result >= 0.00 && result <= 1.00
        }
    }
})
