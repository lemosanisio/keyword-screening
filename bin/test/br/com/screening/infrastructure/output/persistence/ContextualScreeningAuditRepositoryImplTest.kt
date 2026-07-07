package br.com.screening.infrastructure.output.persistence

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.infrastructure.output.persistence.entity.ContextualScreeningAuditEntity
import br.com.screening.infrastructure.output.persistence.mapper.ContextualScreeningAuditMapper
import br.com.screening.infrastructure.output.persistence.repository.ContextualScreeningAuditJpaRepository
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

@DisplayName("ContextualScreeningAuditRepositoryImpl")
class ContextualScreeningAuditRepositoryImplTest {

    private val jpaRepository = mockk<ContextualScreeningAuditJpaRepository>()
    private val mapper = mockk<ContextualScreeningAuditMapper>()
    private val repository = ContextualScreeningAuditRepositoryImpl(jpaRepository, mapper)

    private val now = Instant.now()
    private val transactionId = TransactionId("TX-001")
    private val ruleId = "CONTEXTUAL_SCREENING"

    private fun sampleAudit(id: Long? = 1L) = ContextualScreeningAudit(
        id = id,
        transactionId = transactionId,
        ruleId = ruleId,
        keyword = "terrorismo",
        prompt = "prompt",
        modelResponse = "{\"r\":1}",
        llmClassification = "SUSPICIOUS",
        llmConfidence = 0.8,
        finalClassification = Classification.SUSPICIOUS,
        finalConfidence = 0.8,
        requiresAnalystReview = true,
        reason = "reason",
        analystDecision = null,
        createdAt = now
    )

    private fun sampleEntity(id: Long = 1L) = ContextualScreeningAuditEntity(
        id = id,
        transactionId = "TX-001",
        ruleId = ruleId,
        keyword = "terrorismo",
        prompt = "prompt",
        modelResponse = "{\"r\":1}",
        llmClassification = "SUSPICIOUS",
        llmConfidence = 0.8,
        finalClassification = "SUSPICIOUS",
        finalConfidence = 0.8,
        requiresAnalystReview = true,
        reason = "reason",
        analystDecision = null,
        createdAt = now
    )

    @Test
    @DisplayName("findByTransactionIdAndRuleId returns domain when entity found")
    fun findByTransactionIdAndRuleIdReturnsDomain() {
        val entity = sampleEntity()
        val domain = sampleAudit()

        every { jpaRepository.findByTransactionIdAndRuleId("TX-001", ruleId) } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByTransactionIdAndRuleId(transactionId, ruleId)

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findByTransactionIdAndRuleId returns null when not found")
    fun findByTransactionIdAndRuleIdReturnsNull() {
        every { jpaRepository.findByTransactionIdAndRuleId("TX-001", ruleId) } returns null

        val result = repository.findByTransactionIdAndRuleId(transactionId, ruleId)

        assertNull(result)
    }

    @Test
    @DisplayName("save persists and returns mapped domain")
    fun savePersistsAndReturnsMappedDomain() {
        val audit = sampleAudit(id = null)
        val entity = sampleEntity(id = 0L)
        val savedEntity = sampleEntity(id = 10L)
        val savedDomain = sampleAudit(id = 10L)

        every { mapper.toEntity(audit) } returns entity
        every { jpaRepository.save(entity) } returns savedEntity
        every { mapper.toDomain(savedEntity) } returns savedDomain

        val result = repository.save(audit)

        assertEquals(savedDomain, result)
    }

    @Test
    @DisplayName("save handles race condition by returning existing record")
    fun saveHandlesRaceCondition() {
        val audit = sampleAudit(id = null)
        val entity = sampleEntity(id = 0L)
        val existingEntity = sampleEntity(id = 99L)
        val existingDomain = sampleAudit(id = 99L)

        every { mapper.toEntity(audit) } returns entity
        every { jpaRepository.save(entity) } throws DataIntegrityViolationException("Duplicate")
        every { jpaRepository.findByTransactionIdAndRuleId("TX-001", ruleId) } returns existingEntity
        every { mapper.toDomain(existingEntity) } returns existingDomain

        val result = repository.save(audit)

        assertEquals(existingDomain, result)
    }

    @Test
    @DisplayName("save rethrows when existing not found after race condition")
    fun saveRethrowsWhenExistingNotFound() {
        val audit = sampleAudit(id = null)
        val entity = sampleEntity(id = 0L)

        every { mapper.toEntity(audit) } returns entity
        every { jpaRepository.save(entity) } throws DataIntegrityViolationException("Duplicate")
        every { jpaRepository.findByTransactionIdAndRuleId("TX-001", ruleId) } returns null

        assertThrows<DataIntegrityViolationException> {
            repository.save(audit)
        }
    }

    @Test
    @DisplayName("updateAnalystDecision delegates to JPA repository")
    fun updateAnalystDecisionDelegatesToJpa() {
        every { jpaRepository.updateAnalystDecision("TX-001", ruleId, "FALSE_POSITIVE") } returns Unit

        repository.updateAnalystDecision(transactionId, ruleId, Classification.FALSE_POSITIVE)

        verify(exactly = 1) { jpaRepository.updateAnalystDecision("TX-001", ruleId, "FALSE_POSITIVE") }
    }
}
