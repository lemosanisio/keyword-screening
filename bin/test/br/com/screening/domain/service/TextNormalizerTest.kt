package br.com.screening.domain.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Testes unitários para [TextNormalizer] — exemplos concretos.
 * Requirements: 2.1–2.5
 */
class TextNormalizerTest : StringSpec({

    val normalizer = TextNormalizer()

    // Requirement 2.1 — texto convertido para minúsculas
    "deve converter texto maiúsculo para minúsculas" {
        normalizer.normalize("Lavagem") shouldBe "lavagem"
    }

    // Requirement 2.2 — acentos removidos
    "deve remover acentos de palavras acentuadas" {
        normalizer.normalize("café") shouldBe "cafe"
    }

    // Requirements 2.3 — caracteres especiais removidos
    "deve remover caracteres especiais como cifrão, vírgula e ponto" {
        normalizer.normalize("R\$100,00") shouldBe "r10000"
    }

    // Requirement 2.4 — espaços múltiplos compactados e trim aplicado
    "deve compactar múltiplos espaços e remover espaços nas bordas" {
        normalizer.normalize("  dois  espaços  ") shouldBe "dois espacos"
    }

    // Requirement 2.5 — string vazia retorna string vazia
    "deve retornar string vazia para entrada vazia" {
        normalizer.normalize("") shouldBe ""
    }
})
