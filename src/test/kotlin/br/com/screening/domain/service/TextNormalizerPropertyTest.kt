package br.com.screening.domain.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.forAll
import io.kotest.property.arbitrary.string

/**
 * Testes de propriedade para [TextNormalizer].
 *
 * Property 1 — Validates: Requirements 2.5, 2.6
 * Property 2 — Validates: Requirements 2.1, 2.2, 2.3, 2.4
 */
class TextNormalizerPropertyTest : StringSpec({

    val normalizer = TextNormalizer()

    // Feature: mf09-keyword-screening, Property 1: Normalização é idempotente
    "normalize é idempotente: normalize(normalize(s)) == normalize(s)" {
        forAll(Arb.string()) { s ->
            normalizer.normalize(normalizer.normalize(s)) == normalizer.normalize(s)
        }
    }

    // Feature: mf09-keyword-screening, Property 2: Texto normalizado está em minúsculas e sem acentos nem caracteres especiais
    "normalize produz apenas caracteres ASCII minúsculos, dígitos e espaços simples" {
        forAll(Arb.string()) { s ->
            val result = normalizer.normalize(s)
            result.matches(Regex("[a-z0-9 ]*")) && !result.contains("  ")
        }
    }
})
