package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.*
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.ConfigurationVersionEntity
import br.com.decision.infrastructure.output.persistence.entity.RuleConfigurationEntity
import br.com.decision.infrastructure.output.persistence.mapper.RuleConfigurationMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@DisplayName("RuleConfigurationRepositoryImpl")
class RuleConfigurationRepositoryImplTest {

    private val jpaRepository = mockk<RuleConfigurationJpaRepository>()
    private val versionJpaRepository = mockk<ConfigurationVersionJpaRepository>()
    private val mapper = mockk<RuleConfigurationMapper>()
    private val repository = RuleConfigurationRepositoryImpl(jpaRepository, versionJpaRepository, mapper)

    private val id = UUID.randomUUID()
    private val ruleId = UUID.randomUUID()
    private val now = Instant.now()

    private fun sampleEntity() = RuleConfigurationEntity(
        id = id,
        ruleId = ruleId,
        expressions = emptyList(),
        actions = listOf("GENERATE_ALERT"),
        active = true,
        draft = false,
        currentVersion = 1,
        createdBy = "admin",
        createdAt = now,
        updatedAt = now
    )

    private fun sampleDomain() = RuleConfiguration(
        id = id,
        ruleId = RuleId(ruleId),
        expressions = listOf(
            Condition(FactName("amount"), ComparisonOperator.GREATER_THAN, FactValue.NumberValue(BigDecimal("1000")))
        ),
        actions = listOf(Action.GENERATE_ALERT),
        active = true,
        draft = false,
        currentVersion = ConfigurationVersion(1),
        versions = emptyList(),
        createdBy = "admin",
        createdAt = now,
        updatedAt = now
    )

    @Test
    @DisplayName("save persists config and versions, returns domain from findById")
    fun savePersistsConfigAndVersions() {
        val domain = sampleDomain()
        val entity = sampleEntity()

        every { jpaRepository.existsById(id) } returns false
        every { mapper.toEntity(domain) } returns entity
        every { jpaRepository.save(entity) } returns entity
        every { jpaRepository.findById(id) } returns Optional.of(entity)
        every { mapper.toDomain(entity) } returns domain
        every { versionJpaRepository.saveAll(emptyList<ConfigurationVersionEntity>()) } returns emptyList()

        val result = repository.save(domain)

        assertEquals(domain, result)
        verify(exactly = 1) { jpaRepository.save(entity) }
        verify(exactly = 1) { versionJpaRepository.saveAll(any<List<ConfigurationVersionEntity>>()) }
    }

    @Test
    @DisplayName("save falls back to mapper.toDomain when findById returns null")
    fun saveFallsBackToMapperWhenFindByIdReturnsNull() {
        val domain = sampleDomain()
        val entity = sampleEntity()

        every { jpaRepository.existsById(id) } returns false
        every { mapper.toEntity(domain) } returns entity
        every { jpaRepository.save(entity) } returns entity
        every { jpaRepository.findById(id) } returns Optional.empty()
        every { mapper.toDomain(entity) } returns domain
        every { versionJpaRepository.saveAll(emptyList<ConfigurationVersionEntity>()) } returns emptyList()

        val result = repository.save(domain)

        assertEquals(domain, result)
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
    @DisplayName("findActiveByRuleId returns active config")
    fun findActiveByRuleIdReturnsActiveConfig() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByRuleIdAndActiveTrue(ruleId) } returns entity
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findActiveByRuleId(RuleId(ruleId))

        assertEquals(domain, result)
    }

    @Test
    @DisplayName("findActiveByRuleId returns null when no active config")
    fun findActiveByRuleIdReturnsNull() {
        every { jpaRepository.findByRuleIdAndActiveTrue(ruleId) } returns null

        val result = repository.findActiveByRuleId(RuleId(ruleId))

        assertNull(result)
    }

    @Test
    @DisplayName("findByRuleId returns all configs for rule")
    fun findByRuleIdReturnsAllConfigs() {
        val entity = sampleEntity()
        val domain = sampleDomain()

        every { jpaRepository.findByRuleId(ruleId) } returns listOf(entity)
        every { mapper.toDomain(entity) } returns domain

        val result = repository.findByRuleId(RuleId(ruleId))

        assertEquals(listOf(domain), result)
    }
}
