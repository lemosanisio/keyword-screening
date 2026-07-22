package br.com.evaluation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class SnapshotCanonicalizerTest {
    private val canonicalizer = SnapshotCanonicalizer(ObjectMapper())

    @Test
    fun `canonical snapshot preserves array order and has reproducible sha256`() {
        val snapshot = linkedMapOf<String, Any?>(
            "z" to "last",
            "a" to listOf(3, 2, 1),
        )

        val result = canonicalizer.canonicalize(snapshot)

        assertThat(result.json).isEqualTo("{\"a\":[3,2,1],\"z\":\"last\"}")
        assertThat(result.hash).isEqualTo(
            MessageDigest.getInstance("SHA-256")
                .digest(result.json.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) },
        )
        assertThat(result.hash).matches("^[a-f0-9]{64}$")
    }
}
