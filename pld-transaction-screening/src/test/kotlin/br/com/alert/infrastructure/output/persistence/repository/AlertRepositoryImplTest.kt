package br.com.alert.infrastructure.output.persistence.repository

import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.model.vo.AlertId
import br.com.alert.infrastructure.output.persistence.entity.AlertEntity
import br.com.alert.infrastructure.output.persistence.mapper.AlertMapper
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
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

@DisplayName("AlertRepositoryImpl")
class AlertRepositoryImplTest {

    private val jpaRepository = mockk<AlertJpaRepository>()
    private val mapper = mockk<AlertMapper>()
    private val repository = AlertRepositoryImpl(jpaRepository, mapper)

    private val id = UUID.randomUUID()
    private val ruleId = UUID.randomUUID()
    private val now = Instant.now()

    private fun sampleEntity() = AlertEntity(
        id = id,
        transactionId = "TX-001",
        ruleId = ruleId,
        customerId = "CUST-001",
        facts = emptyMap(),
        configurationVersion = 1,
        traceId = "trace-1",
        actions = listOf("GENERATE_ALERT"),
        explanation = emptyMap(),
        status = "OPEN",
        createdAt = now,
        updatedAt = now
    )

    private fun sampleDomain() = Alert(
        id = AlertId(id),
        transactionId = TransactionId("TX-001"),
        ruleId = RuleId(ruleId),
        customerId = CustomerId("CUST-001"),
        facts = emptyMap(),
        configurationVersion = 1,
        traceId = TraceId("trace-1"),
        actions = listOf("GENERATE_ALERT"),
        explanation = emptyMap(),
        status = AlertStatus.OPEN,
        createdAt = now,
        updatedAt = now
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

        val result = repository.findById(AlertId(id))

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findById returns null when not found")
    fun findByIdReturnsNull() {
        every { jpaRepository.findById(id) } returns Optional.empty()

        val result = repository.findById(AlertId(id))

        assertNull(result)
    }

    @Test
    @DisplayName("findByTransactionId returns mapped list")
    fun findByTransactionIdReturnsMappedList() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByTransactionId("TX-001") } returns listOf(entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByTransactionId(TransactionId("TX-001"))

        assertEquals(listOf(domain), result)
    }

    @Test
    @DisplayName("findByTransactionId returns empty list when none found")
    fun findByTransactionIdReturnsEmpty() {
        every { jpaRepository.findByTransactionId("TX-NONE") } returns emptyList()

        val result = repository.findByTransactionId(TransactionId("TX-NONE"))

        assertEquals(emptyList<Alert>(), result)
    }

    @Test
    @DisplayName("findByTransactionIdAndRuleId returns domain when found")
    fun findByTransactionIdAndRuleIdReturnsDomain() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByTransactionIdAndRuleId("TX-001", ruleId) } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByTransactionIdAndRuleId(TransactionId("TX-001"), RuleId(ruleId))

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findByTransactionIdAndRuleId returns null when not found")
    fun findByTransactionIdAndRuleIdReturnsNull() {
        every { jpaRepository.findByTransactionIdAndRuleId("TX-001", ruleId) } returns null

        val result = repository.findByTransactionIdAndRuleId(TransactionId("TX-001"), RuleId(ruleId))

        assertNull(result)
    }

    @Test
    @DisplayName("findByRuleId returns paginated results")
    fun findByRuleIdReturnsPaginatedResults() {
        val entity = sampleEntity()
        val domain = sampleDomain()
        val pageable = PageRequest.of(0, 20)
        val page: Page<AlertEntity> = PageImpl(listOf(entity), pageable, 1)

        every { jpaRepository.findByRuleId(ruleId, pageable) } returns page
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByRuleId(RuleId(ruleId), pageable)

        assertEquals(1, result.totalElements)
        assertEquals(domain, result.content[0])
    }
}
