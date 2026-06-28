package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.forAll

/**
 * Testes de propriedade para [RoutingClassifier].
 *
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.6, 12.3, 12.4, 12.5**
 *
 * Property 3 — Determinismo do roteamento:
 * Verifica que as regras de routing são satisfeitas para todas as combinações
 * de classificação, confidence e threshold.
 */
class RoutingClassifierPropertyTest : StringSpec({

    val classifier = RoutingClassifier()

    "SUSPICIOUS sempre requer revisão do analista independente de confidence e threshold" {
        forAll(Arb.double(0.0..1.0), Arb.double(0.0..1.0)) { confidence, threshold ->
            classifier.requiresAnalystReview(Classification.SUSPICIOUS, confidence, threshold) == true
        }
    }

    "UNCERTAIN sempre requer revisão do analista independente de confidence e threshold" {
        forAll(Arb.double(0.0..1.0), Arb.double(0.0..1.0)) { confidence, threshold ->
            classifier.requiresAnalystReview(Classification.UNCERTAIN, confidence, threshold) == true
        }
    }

    "FALSE_POSITIVE com confidence >= threshold não requer revisão (auto-close)" {
        forAll(Arb.double(0.0..1.0), Arb.double(0.0..1.0)) { confidence, threshold ->
            if (confidence >= threshold) {
                classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, confidence, threshold) == false
            } else {
                true // skip — coberto pelo próximo teste
            }
        }
    }

    "FALSE_POSITIVE com confidence < threshold requer revisão conservadora" {
        forAll(Arb.double(0.0..1.0), Arb.double(0.0..1.0)) { confidence, threshold ->
            if (confidence < threshold) {
                classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, confidence, threshold) == true
            } else {
                true // skip — coberto pelo teste anterior
            }
        }
    }

    "routing é determinístico para qualquer combinação de classificação, confidence e threshold" {
        forAll(
            Arb.enum<Classification>(),
            Arb.double(0.0..1.0),
            Arb.double(0.0..1.0)
        ) { classification, confidence, threshold ->
            val result = classifier.requiresAnalystReview(classification, confidence, threshold)
            when (classification) {
                Classification.SUSPICIOUS -> result == true
                Classification.UNCERTAIN -> result == true
                Classification.FALSE_POSITIVE -> result == (confidence < threshold)
            }
        }
    }
})
