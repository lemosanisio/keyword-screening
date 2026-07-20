package br.com.screening.domain.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Testes de propriedade para [TextNormalizer].
 *
 * Property 1 — Validates: Requirements 2.5, 2.6
 * Property 2 — Validates: Requirements 2.1, 2.2, 2.3, 2.4
 */
class TextNormalizerPropertyTest {

    private val normalizer = TextNormalizer()

    private fun randomString(maxLength: Int = 100): String {
        val length = Random.nextInt(0, maxLength + 1)
        return buildString {
            repeat(length) {
                append(Char(Random.nextInt(0, 0xFFFF)))
            }
        }
    }

    @RepeatedTest(200)
    @DisplayName("normalize é idempotente: normalize(normalize(s)) == normalize(s)")
    fun normalizeIsIdempotent() {
        val s = randomString()
        assertEquals(normalizer.normalize(s), normalizer.normalize(normalizer.normalize(s)))
    }

    @RepeatedTest(200)
    @DisplayName("normalize produz apenas caracteres ASCII minúsculos, dígitos e espaços simples")
    fun normalizeProducesOnlyValidCharacters() {
        val s = randomString()
        val result = normalizer.normalize(s)
        assertTrue(result.matches(Regex("[a-z0-9 ]*"))) { "Result contained invalid chars: '$result'" }
        assertTrue(!result.contains("  ")) { "Result contained double spaces: '$result'" }
    }
}
