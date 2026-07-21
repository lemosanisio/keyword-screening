package br.com.pld.customeranalysis.common

import java.security.SecureRandom

object PrefixedUlid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()

    fun next(prefix: String): String = prefix + ulid()

    fun ulid(): String {
        val chars = CharArray(26)
        var time = System.currentTimeMillis()

        for (index in 9 downTo 0) {
            chars[index] = ALPHABET[(time and 31).toInt()]
            time = time ushr 5
        }

        for (index in 10 until 26) {
            chars[index] = ALPHABET[random.nextInt(32)]
        }

        return String(chars)
    }
}
