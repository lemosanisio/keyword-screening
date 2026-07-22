package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.infrastructure.output.persistence.entity.ContextualScreeningAuditEntity
import br.com.shared.domain.valueobject.TransactionId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("ContextualScreeningAuditMapper")
class ContextualScreeningAuditMapperTest {

    private val mapper = ContextualScreeningAuditMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val now = Instant.now()
        val entity = ContextualScreeningAuditEntity(
            id = 1L,
            transactionId = "TX-001",
            ruleId = "CONTEXTUAL_SCREENING",
            keyword = "terrorismo",
            prompt = "Analyze this transaction",
            modelResponse = """{"classification":"SUSPICIOUS"}""",
            llmClassification = "SUSPICIOUS",
            llmConfidence = 0.85,
            finalClassification = "SUSPICIOUS",
            finalConfidence = 0.9,
            requiresAnalystReview = true,
            reason = "High confidence suspicious activity",
            analystDecision = "FALSE_POSITIVE",
            createdAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(1L, domain.id)
        assertEquals("TX-001", domain.transactionId.value)
        assertEquals("CONTEXTUAL_SCREENING", domain.ruleId)
        assertEquals("terrorismo", domain.keyword)
        assertEquals("Analyze this transaction", domain.prompt)
        assertEquals("""{"classification":"SUSPICIOUS"}""", domain.modelResponse)
        assertEquals("SUSPICIOUS", domain.llmClassification)
        assertEquals(0.85, domain.llmConfidence)
        assertEquals(Classification.SUSPICIOUS, domain.finalClassification)
        assertEquals(0.9, domain.finalConfidence)
        assertTrue(domain.requiresAnalystReview)
        assertEquals("High confidence suspicious activity", domain.reason)
        assertEquals(Classification.FALSE_POSITIVE, domain.analystDecision)
        assertEquals(now, domain.createdAt)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val now = Instant.now()
        val domain = ContextualScreeningAudit(
            id = 5L,
            transactionId = TransactionId("TX-002"),
            ruleId = "RULE_01",
            keyword = "lavagem",
            prompt = "Check description",
            modelResponse = """{"raw":"response text"}""",
            llmClassification = "FALSE_POSITIVE",
            llmConfidence = 0.95,
            finalClassification = Classification.FALSE_POSITIVE,
            finalConfidence = 0.95,
            requiresAnalystReview = false,
            reason = "Clearly legitimate",
            analystDecision = Classification.SUSPICIOUS,
            createdAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(5L, entity.id)
        assertEquals("TX-002", entity.transactionId)
        assertEquals("RULE_01", entity.ruleId)
        assertEquals("lavagem", entity.keyword)
        assertEquals("Check description", entity.prompt)
        assertEquals("""{"raw":"response text"}""", entity.modelResponse)
        assertEquals("FALSE_POSITIVE", entity.llmClassification)
        assertEquals(0.95, entity.llmConfidence)
        assertEquals("FALSE_POSITIVE", entity.finalClassification)
        assertEquals(0.95, entity.finalConfidence)
        assertFalse(entity.requiresAnalystReview)
        assertEquals("Clearly legitimate", entity.reason)
        assertEquals("SUSPICIOUS", entity.analystDecision)
        assertEquals(now, entity.createdAt)
    }

    @Test
    @DisplayName("toEntity uses 0 when domain id is null")
    fun toEntityUsesZeroForNullId() {
        val now = Instant.now()
        val domain = ContextualScreeningAudit(
            id = null,
            transactionId = TransactionId("TX-003"),
            ruleId = "RULE_01",
            keyword = "fraude",
            prompt = "prompt",
            modelResponse = null,
            llmClassification = null,
            llmConfidence = null,
            finalClassification = Classification.UNCERTAIN,
            finalConfidence = 0.5,
            requiresAnalystReview = true,
            reason = "Uncertain result",
            analystDecision = null,
            createdAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(0L, entity.id)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves all data with non-null analystDecision")
    fun roundTripWithAnalystDecision() {
        val now = Instant.now()
        val original = ContextualScreeningAudit(
            id = 10L,
            transactionId = TransactionId("TX-100"),
            ruleId = "CONTEXTUAL_SCREENING",
            keyword = "sancao",
            prompt = "Test prompt",
            modelResponse = """{"raw":"LLM response"}""",
            llmClassification = "SUSPICIOUS",
            llmConfidence = 0.88,
            finalClassification = Classification.SUSPICIOUS,
            finalConfidence = 0.88,
            requiresAnalystReview = true,
            reason = "Needs review",
            analystDecision = Classification.FALSE_POSITIVE,
            createdAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
    }

    @Test
    @DisplayName("round-trip preserves null analystDecision")
    fun roundTripWithNullAnalystDecision() {
        val now = Instant.now()
        val original = ContextualScreeningAudit(
            id = 20L,
            transactionId = TransactionId("TX-200"),
            ruleId = "RULE_02",
            keyword = "financiamento",
            prompt = "Another prompt",
            modelResponse = null,
            llmClassification = null,
            llmConfidence = null,
            finalClassification = Classification.UNCERTAIN,
            finalConfidence = 0.4,
            requiresAnalystReview = true,
            reason = "Low confidence",
            analystDecision = null,
            createdAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
        assertNull(result.analystDecision)
    }

    @Test
    @DisplayName("toDomain handles null analystDecision in entity")
    fun toDomainHandlesNullAnalystDecision() {
        val entity = ContextualScreeningAuditEntity(
            id = 3L,
            transactionId = "TX-003",
            ruleId = "RULE",
            keyword = "test",
            prompt = "test prompt",
            modelResponse = null,
            llmClassification = null,
            llmConfidence = null,
            finalClassification = "FALSE_POSITIVE",
            finalConfidence = 0.99,
            requiresAnalystReview = false,
            reason = "reason",
            analystDecision = null,
            createdAt = Instant.now()
        )

        val domain = mapper.toDomain(entity)

        assertNull(domain.analystDecision)
    }

    @Test
    @DisplayName("maps all Classification values correctly in round-trip")
    fun mapsAllClassifications() {
        val now = Instant.now()
        Classification.entries.forEach { classification ->
            val domain = ContextualScreeningAudit(
                id = 1L,
                transactionId = TransactionId("TX-ENUM"),
                ruleId = "RULE",
                keyword = "test",
                prompt = "prompt",
                modelResponse = "resp",
                llmClassification = classification.name,
                llmConfidence = 0.7,
                finalClassification = classification,
                finalConfidence = 0.7,
                requiresAnalystReview = false,
                reason = "test",
                analystDecision = classification,
                createdAt = now
            )

            val result = mapper.toDomain(mapper.toEntity(domain))

            assertEquals(classification, result.finalClassification)
            assertEquals(classification, result.analystDecision)
        }
    }
}
