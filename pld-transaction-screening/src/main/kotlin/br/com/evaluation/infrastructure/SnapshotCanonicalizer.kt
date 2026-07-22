package br.com.evaluation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.erdtman.jcs.JsonCanonicalizer
import org.springframework.stereotype.Component
import java.security.MessageDigest

data class CanonicalSnapshot(val json: String, val hash: String)

@Component
class SnapshotCanonicalizer(private val objectMapper: ObjectMapper) {
    fun canonicalize(snapshot: Map<String, Any?>): CanonicalSnapshot {
        val canonicalBytes = JsonCanonicalizer(objectMapper.writeValueAsBytes(snapshot)).encodedUTF8
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(canonicalBytes)
            .joinToString("") { "%02x".format(it) }
        return CanonicalSnapshot(String(canonicalBytes, Charsets.UTF_8), hash)
    }
}
