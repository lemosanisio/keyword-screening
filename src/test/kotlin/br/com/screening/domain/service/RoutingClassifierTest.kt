package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Testes unitários para [RoutingClassifier].
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
class RoutingClassifierTest : StringSpec({

    val classifier = RoutingClassifier()
    val threshold = 0.95

    // Requirement 6.1 — SUSPICIOUS sempre requer revisão do analista
    "SUSPICIOUS deve sempre requerer revisão do analista" {
        classifier.requiresAnalystReview(Classification.SUSPICIOUS, 0.99, threshold) shouldBe true
    }

    // Requirement 6.2 — UNCERTAIN sempre requer revisão do analista
    "UNCERTAIN deve sempre requerer revisão do analista" {
        classifier.requiresAnalystReview(Classification.UNCERTAIN, 0.99, threshold) shouldBe true
    }

    // Requirement 6.3 — FALSE_POSITIVE com confidence >= threshold não requer revisão (auto-close)
    "FALSE_POSITIVE com confidence >= threshold deve permitir auto-close" {
        classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, 0.95, threshold) shouldBe false
    }

    // Requirement 6.4 — FALSE_POSITIVE com confidence > threshold não requer revisão
    "FALSE_POSITIVE com confidence acima do threshold deve permitir auto-close" {
        classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, 0.99, threshold) shouldBe false
    }

    // Requirement 6.5 — FALSE_POSITIVE com confidence < threshold requer revisão conservadora
    "FALSE_POSITIVE com confidence abaixo do threshold deve requerer revisão" {
        classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, 0.80, threshold) shouldBe true
    }

    // Requirement 6.6 — Edge case: confidence exatamente igual ao threshold
    "FALSE_POSITIVE com confidence exatamente no threshold deve permitir auto-close" {
        classifier.requiresAnalystReview(Classification.FALSE_POSITIVE, 0.95, 0.95) shouldBe false
    }
})
