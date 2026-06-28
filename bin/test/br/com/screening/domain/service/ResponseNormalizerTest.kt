package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Testes unitários para [ResponseNormalizer].
 * Requirements: 5.4, 5.5, 12.1, 12.2, 12.10
 */
class ResponseNormalizerTest : StringSpec({

    val normalizer = ResponseNormalizer()

    // Requirement 5.4, 12.1 — classificação válida é preservada
    "deve retornar FALSE_POSITIVE para string válida FALSE_POSITIVE" {
        normalizer.normalizeClassification("FALSE_POSITIVE") shouldBe Classification.FALSE_POSITIVE
    }

    "deve retornar SUSPICIOUS para string válida SUSPICIOUS" {
        normalizer.normalizeClassification("SUSPICIOUS") shouldBe Classification.SUSPICIOUS
    }

    "deve retornar UNCERTAIN para string válida UNCERTAIN" {
        normalizer.normalizeClassification("UNCERTAIN") shouldBe Classification.UNCERTAIN
    }

    // Requirement 5.4, 12.2 — classificação inválida retorna UNCERTAIN
    "deve retornar UNCERTAIN para classificação inválida" {
        normalizer.normalizeClassification("INVALID_VALUE") shouldBe Classification.UNCERTAIN
    }

    "deve retornar UNCERTAIN para string vazia" {
        normalizer.normalizeClassification("") shouldBe Classification.UNCERTAIN
    }

    // Requirement 12.10 — classificação nula retorna UNCERTAIN
    "deve retornar UNCERTAIN para classificação nula" {
        normalizer.normalizeClassification(null) shouldBe Classification.UNCERTAIN
    }

    // Requirement 5.5, 12.1 — confidence dentro do range é preservada
    "deve preservar confidence dentro do range válido" {
        normalizer.normalizeConfidence(0.85) shouldBe 0.85
    }

    "deve preservar confidence zero" {
        normalizer.normalizeConfidence(0.00) shouldBe 0.00
    }

    "deve preservar confidence um" {
        normalizer.normalizeConfidence(1.00) shouldBe 1.00
    }

    // Requirement 5.5, 12.2 — confidence fora do range é clamped
    "deve fazer clamp de confidence acima de 1.0 para 1.0" {
        normalizer.normalizeConfidence(1.5) shouldBe 1.00
    }

    "deve fazer clamp de confidence negativa para 0.0" {
        normalizer.normalizeConfidence(-0.5) shouldBe 0.00
    }

    // Requirement 12.10 — confidence nula retorna 0.00
    "deve retornar 0.00 para confidence nula" {
        normalizer.normalizeConfidence(null) shouldBe 0.00
    }
})
