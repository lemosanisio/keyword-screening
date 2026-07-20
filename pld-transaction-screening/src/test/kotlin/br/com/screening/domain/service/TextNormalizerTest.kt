package br.com.screening.domain.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Testes unitários para [TextNormalizer] — exemplos concretos.
 * Requirements: 2.1–2.5
 */
class TextNormalizerTest {

    private val normalizer = TextNormalizer()

    @Test
    @DisplayName("deve converter texto maiúsculo para minúsculas")
    fun shouldConvertUppercaseToLowercase() {
        assertEquals("lavagem", normalizer.normalize("Lavagem"))
    }

    @Test
    @DisplayName("deve remover acentos de palavras acentuadas")
    fun shouldRemoveAccents() {
        assertEquals("cafe", normalizer.normalize("café"))
    }

    @Test
    @DisplayName("deve remover caracteres especiais como cifrão, vírgula e ponto")
    fun shouldRemoveSpecialCharacters() {
        assertEquals("r10000", normalizer.normalize("R\$100,00"))
    }

    @Test
    @DisplayName("deve compactar múltiplos espaços e remover espaços nas bordas")
    fun shouldCompactMultipleSpacesAndTrim() {
        assertEquals("dois espacos", normalizer.normalize("  dois  espaços  "))
    }

    @Test
    @DisplayName("deve retornar string vazia para entrada vazia")
    fun shouldReturnEmptyStringForEmptyInput() {
        assertEquals("", normalizer.normalize(""))
    }
}
