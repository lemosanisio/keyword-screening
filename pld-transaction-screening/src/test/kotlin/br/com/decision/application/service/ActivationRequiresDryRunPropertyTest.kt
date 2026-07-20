package br.com.decision.application.service

import br.com.decision.domain.exception.DuplicateActiveConfigException
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ConfigurationVersionEntry
import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.model.DryRunLogResult
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.port.DryRunLogRepository
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Property-based test for RuleConfigurationService.activate().
 *
 * **Property 12: Activation Requires Prior Dry-Run**
 * For any RuleConfiguration in draft state:
 * (a) if NO dry-run log exists for the ConfigurationVersion being activated, activation MUST be rejected
 * (b) if at least one dry-run log exists, activation MUST be permitted (draft→active)
 * (c) if another config is already active for the same ruleId, activation MUST be rejected with DuplicateActiveConfigException
 *
 * **Validates: Requirements 18.1, 18.2, 18.4, 18.5, 18.6**
 */
class ActivationRequiresDryRunPropertyTest {

    @RepeatedTest(200)
    @DisplayName("Property 12a: activation succeeds when dry-run log exists and no other active config for same ruleId")
    fun `Property 12a - activation succeeds when dry-run log exists and no other active config for same ruleId`() {
        val configId = UUID.randomUUID()
        val versionNumber = Random.nextInt(1, 11)

        val ruleConfigurationRepository = mockk<RuleConfigurationRepository>()
        val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()
        val factDefinitionRepository = mockk<FactDefinitionRepository>()
        val dryRunLogRepository = mockk<DryRunLogRepository>()

        val service = RuleConfigurationService(
            ruleConfigurationRepository,
            ruleDefinitionRepository,
            factDefinitionRepository,
            dryRunLogRepository
        )

        val ruleId = RuleId(UUID.randomUUID())
        val version = ConfigurationVersion(versionNumber)

        val config = RuleConfiguration(
            id = configId,
            ruleId = ruleId,
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            active = false,
            draft = true,
            currentVersion = version,
            versions = listOf(
                ConfigurationVersionEntry(
                    version = version,
                    expressions = listOf(
                        Condition(
                            factName = FactName("keywordMatched"),
                            operator = ComparisonOperator.EQUALS,
                            expectedValue = FactValue.BooleanValue(true)
                        )
                    ),
                    actions = listOf(Action.GENERATE_ALERT),
                    active = false,
                    createdBy = "analyst@test.com",
                    createdAt = Instant.now()
                )
            ),
            createdBy = "analyst@test.com",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val dryRunLog = DryRunLog(
            id = UUID.randomUUID(),
            configurationId = configId,
            version = version,
            facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
            result = DryRunLogResult(decision = Decision.ALERT, actions = listOf(Action.GENERATE_ALERT)),
            executedBy = "analyst@test.com",
            createdAt = Instant.now()
        )

        every { ruleConfigurationRepository.findById(configId) } returns config
        every { dryRunLogRepository.findByConfigurationIdAndVersion(configId, version) } returns listOf(dryRunLog)
        every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns null

        val saved = slot<RuleConfiguration>()
        every { ruleConfigurationRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.activate(configId)

        assertEquals(true, result.active)
        assertEquals(false, result.draft)
    }

    @RepeatedTest(200)
    @DisplayName("Property 12b: activation fails with InvalidConfigurationException when dry-run log does not exist")
    fun `Property 12b - activation fails with InvalidConfigurationException when dry-run log does not exist`() {
        val configId = UUID.randomUUID()
        val versionNumber = Random.nextInt(1, 11)

        val ruleConfigurationRepository = mockk<RuleConfigurationRepository>()
        val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()
        val factDefinitionRepository = mockk<FactDefinitionRepository>()
        val dryRunLogRepository = mockk<DryRunLogRepository>()

        val service = RuleConfigurationService(
            ruleConfigurationRepository,
            ruleDefinitionRepository,
            factDefinitionRepository,
            dryRunLogRepository
        )

        val ruleId = RuleId(UUID.randomUUID())
        val version = ConfigurationVersion(versionNumber)

        val config = RuleConfiguration(
            id = configId,
            ruleId = ruleId,
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            active = false,
            draft = true,
            currentVersion = version,
            versions = listOf(
                ConfigurationVersionEntry(
                    version = version,
                    expressions = listOf(
                        Condition(
                            factName = FactName("keywordMatched"),
                            operator = ComparisonOperator.EQUALS,
                            expectedValue = FactValue.BooleanValue(true)
                        )
                    ),
                    actions = listOf(Action.GENERATE_ALERT),
                    active = false,
                    createdBy = "analyst@test.com",
                    createdAt = Instant.now()
                )
            ),
            createdBy = "analyst@test.com",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        every { ruleConfigurationRepository.findById(configId) } returns config
        every { dryRunLogRepository.findByConfigurationIdAndVersion(configId, version) } returns emptyList()

        val exception = try {
            service.activate(configId)
            null
        } catch (e: InvalidConfigurationException) {
            e
        }

        assertNotNull(exception)
    }

    @RepeatedTest(200)
    @DisplayName("Property 12c: activation fails with DuplicateActiveConfigException when another config is already active for same ruleId")
    fun `Property 12c - activation fails with DuplicateActiveConfigException when another config is already active for same ruleId`() {
        val configId = UUID.randomUUID()
        val versionNumber = Random.nextInt(1, 11)

        val ruleConfigurationRepository = mockk<RuleConfigurationRepository>()
        val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()
        val factDefinitionRepository = mockk<FactDefinitionRepository>()
        val dryRunLogRepository = mockk<DryRunLogRepository>()

        val service = RuleConfigurationService(
            ruleConfigurationRepository,
            ruleDefinitionRepository,
            factDefinitionRepository,
            dryRunLogRepository
        )

        val ruleId = RuleId(UUID.randomUUID())
        val version = ConfigurationVersion(versionNumber)

        val config = RuleConfiguration(
            id = configId,
            ruleId = ruleId,
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            active = false,
            draft = true,
            currentVersion = version,
            versions = listOf(
                ConfigurationVersionEntry(
                    version = version,
                    expressions = listOf(
                        Condition(
                            factName = FactName("keywordMatched"),
                            operator = ComparisonOperator.EQUALS,
                            expectedValue = FactValue.BooleanValue(true)
                        )
                    ),
                    actions = listOf(Action.GENERATE_ALERT),
                    active = false,
                    createdBy = "analyst@test.com",
                    createdAt = Instant.now()
                )
            ),
            createdBy = "analyst@test.com",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // Another config already active for the same ruleId (different id)
        val otherActiveConfigId = UUID.randomUUID()
        val otherActiveConfig = config.copy(
            id = otherActiveConfigId,
            active = true,
            draft = false
        )

        val dryRunLog = DryRunLog(
            id = UUID.randomUUID(),
            configurationId = configId,
            version = version,
            facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
            result = DryRunLogResult(decision = Decision.ALERT, actions = listOf(Action.GENERATE_ALERT)),
            executedBy = "analyst@test.com",
            createdAt = Instant.now()
        )

        every { ruleConfigurationRepository.findById(configId) } returns config
        every { dryRunLogRepository.findByConfigurationIdAndVersion(configId, version) } returns listOf(dryRunLog)
        every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns otherActiveConfig

        val exception = try {
            service.activate(configId)
            null
        } catch (e: DuplicateActiveConfigException) {
            e
        }

        assertNotNull(exception)
    }
}
