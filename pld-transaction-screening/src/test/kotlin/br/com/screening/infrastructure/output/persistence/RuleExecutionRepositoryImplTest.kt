package br.com.screening.infrastructure.output.persistence

import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.Category
import br.com.screening.domain.model.RuleExecution
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.infrastructure.output.persistence.entity.RuleExecutionEntity
import br.com.screening.infrastructure.output.persistence.mapper.RuleExecutionMapper
import br.com.screening.infrastructure.output.persistence.repository.RuleExecutionJpaRepository
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

@DisplayName("RuleExecutionRepositoryImpl")
class RuleExecutionRepositoryImplTest {

    private val jpaRepository = mockk<RuleExecutionJpaRepository>()
    private val mapper = mockk<RuleExecutionMapper>()
    private val repository = RuleExecutionRepositoryImpl(jpaRepository, mapper)

    private val now = Instant.now()
    private val transactionId = TransactionId("TX-001")
    private val ruleCode = "KEYWORD_SCREENING"
    private val screeningResult = ScreeningResult(ruleCode, true, listOf(MatchResult("terrorismo", Category.TERRORISM)))

    @Test
    @DisplayName("findByTransactionIdAndRuleCode returns domain when entity found")
    fun findByTransactionIdAndRuleCodeReturnsDomain() {
        val entity = RuleExecutionEntity(id = 1L, transactionId = "TX-001", ruleCode = ruleCode, result = "{}", createdAt = now)
        val domain = RuleExecution(id = 1L, transactionId = transactionId, ruleCode = ruleCode, result = screeningResult, createdAt = now)

        every { jpaRepository.findByTransactionIdAndRuleCode("TX-001", ruleCode) } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByTransactionIdAndRuleCode(transactionId, ruleCode)

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findByTransactionIdAndRuleCode returns null when not found")
    fun findByTransactionIdAndRuleCodeReturnsNull() {
        every { jpaRepository.findByTransactionIdAndRuleCode("TX-001", ruleCode) } returns null

        val result = repository.findByTransactionIdAndRuleCode(transactionId, ruleCode)

        assertNull(result)
    }

    @Test
    @DisplayName("save persists and returns mapped domain")
    fun savePersistsAndReturnsMappedDomain() {
        val domain = RuleExecution(id = null, transactionId = transactionId, ruleCode = ruleCode, result = screeningResult, createdAt = now)
        val entity = RuleExecutionEntity(id = 0L, transactionId = "TX-001", ruleCode = ruleCode, result = "{}", createdAt = now)
        val savedEntity = RuleExecutionEntity(id = 1L, transactionId = "TX-001", ruleCode = ruleCode, result = "{}", createdAt = now)
        val savedDomain = domain.copy(id = 1L)

        every { mapper.toEntity(domain) } returns entity
        every { jpaRepository.save(entity) } returns savedEntity
        every { mapper.toDomain(savedEntity) } returns savedDomain

        val result = repository.save(domain)

        assertEquals(savedDomain, result)
        verify(exactly = 1) { jpaRepository.save(entity) }
    }

    @Test
    @DisplayName("save handles race condition by returning existing record")
    fun saveHandlesRaceCondition() {
        val domain = RuleExecution(id = null, transactionId = transactionId, ruleCode = ruleCode, result = screeningResult, createdAt = now)
        val entity = RuleExecutionEntity(id = 0L, transactionId = "TX-001", ruleCode = ruleCode, result = "{}", createdAt = now)
        val existingEntity = RuleExecutionEntity(id = 99L, transactionId = "TX-001", ruleCode = ruleCode, result = "{}", createdAt = now)
        val existingDomain = domain.copy(id = 99L)

        every { mapper.toEntity(domain) } returns entity
        every { jpaRepository.save(entity) } throws DataIntegrityViolationException("Duplicate")
        every { jpaRepository.findByTransactionIdAndRuleCode("TX-001", ruleCode) } returns existingEntity
        every { mapper.toDomain(existingEntity) } returns existingDomain

        val result = repository.save(domain)

        assertEquals(existingDomain, result)
    }

    @Test
    @DisplayName("save rethrows DataIntegrityViolationException when existing not found")
    fun saveRethrowsWhenExistingNotFound() {
        val domain = RuleExecution(id = null, transactionId = transactionId, ruleCode = ruleCode, result = screeningResult, createdAt = now)
        val entity = RuleExecutionEntity(id = 0L, transactionId = "TX-001", ruleCode = ruleCode, result = "{}", createdAt = now)

        every { mapper.toEntity(domain) } returns entity
        every { jpaRepository.save(entity) } throws DataIntegrityViolationException("Duplicate")
        every { jpaRepository.findByTransactionIdAndRuleCode("TX-001", ruleCode) } returns null

        assertThrows<DataIntegrityViolationException> {
            repository.save(domain)
        }
    }
}
