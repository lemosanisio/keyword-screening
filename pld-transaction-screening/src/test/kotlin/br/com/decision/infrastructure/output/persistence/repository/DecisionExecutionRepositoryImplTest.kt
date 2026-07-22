package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.*
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.DecisionExecutionEntity
import br.com.decision.infrastructure.output.persistence.mapper.DecisionExecutionMapper
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.*

@DisplayName("DecisionExecutionRepositoryImpl")
class DecisionExecutionRepositoryImplTest {

    private val jpaRepository = mockk<DecisionExecutionJpaRepository>()
    private val mapper = mockk<DecisionExecutionMapper>()
    private val repository = DecisionExecutionRepositoryImpl(jpaRepository, mapper)

    private val id = UUID.randomUUID()
    private val ruleId = UUID.randomUUID()
    private val now = Instant.now()

    private fun sampleEntity() = DecisionExecutionEntity(
        id = id,
        transactionId = "TX-001",
        ruleId = ruleId,
        configurationVersion = 1,
        facts = emptyMap(),
        decision = "ALERT",
        actions = listOf("GENERATE_ALERT"),
        matchedExpressions = emptyList(),
        failedExpressions = emptyList(),
        explanation = mapOf("traceId" to "trace-1"),
        executionTimeMs = 50,
        traceId = "trace-1",
        createdAt = now
    )

    private fun sampleDomain() = DecisionExecution(
        id = id,
        transactionId = TransactionId("TX-001"),
        ruleId = RuleId(ruleId),
        configurationVersion = ConfigurationVersion(1),
        facts = emptyMap(),
        result = DecisionResult(
            decision = Decision.ALERT,
            actions = listOf(Action.GENERATE_ALERT),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            executionTimeMs = 50,
            configurationVersion = ConfigurationVersion(1),
            facts = emptyMap()
        ),
        explanation = DecisionExplanation(traceId = TraceId("trace-1"), steps = emptyList()),
        executionTimeMs = 50,
        traceId = TraceId("trace-1"),
        timestamp = now
    )

    @Test
    @DisplayName("save persists and returns mapped domain")
    fun savePersistsAndReturnsMappedDomain() {
        val domain = sampleDomain()
        val entity = sampleEntity()

        every { mapper.toEntity(domain) } returns entity
        every { jpaRepository.save(entity) } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.save(domain)

        assertEquals(domain, result)
        verify(exactly = 1) { jpaRepository.save(entity) }
    }

    @Test
    @DisplayName("findById returns domain when found")
    fun findByIdReturnsDomain() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findById(id) } returns Optional.of(entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findById(id)

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findById returns null when not found")
    fun findByIdReturnsNull() {
        every { jpaRepository.findById(id) } returns Optional.empty()

        val result = repository.findById(id)

        assertNull(result)
    }

    @Test
    @DisplayName("findByTransactionIdAndRuleId returns domain when found")
    fun findByTransactionIdAndRuleIdReturnsDomain() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findTopByTransactionIdAndRuleIdOrderByCreatedAtDesc("TX-001", ruleId) } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByTransactionIdAndRuleId(TransactionId("TX-001"), RuleId(ruleId))

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findByTransactionIdAndRuleId returns null when not found")
    fun findByTransactionIdAndRuleIdReturnsNull() {
        every { jpaRepository.findTopByTransactionIdAndRuleIdOrderByCreatedAtDesc("TX-001", ruleId) } returns null

        val result = repository.findByTransactionIdAndRuleId(TransactionId("TX-001"), RuleId(ruleId))

        assertNull(result)
    }

    @Test
    @DisplayName("findByTransactionId returns paginated results")
    fun findByTransactionIdReturnsPaginatedResults() {
        val entity = sampleEntity()
        val domain = sampleDomain()
        val pageable = PageRequest.of(0, 20)
        val page: Page<DecisionExecutionEntity> = PageImpl(listOf(entity), pageable, 1)

        every { jpaRepository.findByTransactionId("TX-001", pageable) } returns page
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByTransactionId(TransactionId("TX-001"), pageable)

        assertEquals(1, result.totalElements)
        assertEquals(domain, result.content[0])
    }

    @Test
    @DisplayName("findByRuleId returns paginated results")
    fun findByRuleIdReturnsPaginatedResults() {
        val entity = sampleEntity()
        val domain = sampleDomain()
        val pageable = PageRequest.of(0, 10)
        val page: Page<DecisionExecutionEntity> = PageImpl(listOf(entity), pageable, 1)

        every { jpaRepository.findByRuleId(ruleId, pageable) } returns page
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByRuleId(RuleId(ruleId), pageable)

        assertEquals(1, result.totalElements)
        assertEquals(domain, result.content[0])
    }

    @Test
    @DisplayName("findByDecision returns paginated results")
    fun findByDecisionReturnsPaginatedResults() {
        val entity = sampleEntity()
        val domain = sampleDomain()
        val pageable = PageRequest.of(0, 20)
        val page: Page<DecisionExecutionEntity> = PageImpl(listOf(entity), pageable, 1)

        every { jpaRepository.findByDecision("ALERT", pageable) } returns page
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByDecision(Decision.ALERT, pageable)

        assertEquals(1, result.totalElements)
    }

    @Test
    @DisplayName("findByTraceId returns domain when found")
    fun findByTraceIdReturnsDomain() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByTraceId("trace-1") } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByTraceId(TraceId("trace-1"))

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findByTraceId returns null when not found")
    fun findByTraceIdReturnsNull() {
        every { jpaRepository.findByTraceId("trace-unknown") } returns null

        val result = repository.findByTraceId(TraceId("trace-unknown"))

        assertNull(result)
    }
}
