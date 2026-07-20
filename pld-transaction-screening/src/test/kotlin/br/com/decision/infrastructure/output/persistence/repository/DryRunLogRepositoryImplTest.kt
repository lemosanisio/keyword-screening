package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.model.DryRunLogResult
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.infrastructure.output.persistence.entity.DryRunLogEntity
import br.com.decision.infrastructure.output.persistence.mapper.DryRunLogMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("DryRunLogRepositoryImpl")
class DryRunLogRepositoryImplTest {

    private val jpaRepository = mockk<DryRunLogJpaRepository>()
    private val mapper = mockk<DryRunLogMapper>()
    private val repository = DryRunLogRepositoryImpl(jpaRepository, mapper)

    private val id = UUID.randomUUID()
    private val configId = UUID.randomUUID()
    private val now = Instant.now()

    private fun sampleEntity() = DryRunLogEntity(
        id = id,
        configurationId = configId,
        version = 2,
        facts = emptyMap(),
        result = mapOf("decision" to "ALERT"),
        executedBy = "admin",
        createdAt = now
    )

    private fun sampleDomain() = DryRunLog(
        id = id,
        configurationId = configId,
        version = ConfigurationVersion(2),
        facts = mapOf(FactName("amount") to FactValue.NumberValue(BigDecimal("100"))),
        result = DryRunLogResult(decision = Decision.ALERT, actions = listOf(Action.GENERATE_ALERT)),
        executedBy = "admin",
        createdAt = now
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
    @DisplayName("findByConfigurationIdAndVersion returns mapped list")
    fun findByConfigurationIdAndVersionReturnsMappedList() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByConfigurationIdAndVersion(configId, 2) } returns listOf(entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByConfigurationIdAndVersion(configId, ConfigurationVersion(2))

        assertEquals(listOf(domain), result)
    }

    @Test
    @DisplayName("findByConfigurationIdAndVersion returns empty list when none found")
    fun findByConfigurationIdAndVersionReturnsEmpty() {
        every { jpaRepository.findByConfigurationIdAndVersion(configId, 5) } returns emptyList()

        val result = repository.findByConfigurationIdAndVersion(configId, ConfigurationVersion(5))

        assertEquals(emptyList<DryRunLog>(), result)
    }

    @Test
    @DisplayName("findByConfigurationId returns all logs for config")
    fun findByConfigurationIdReturnsAllLogs() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByConfigurationId(configId) } returns listOf(entity, entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByConfigurationId(configId)

        assertEquals(2, result.size)
    }
}
