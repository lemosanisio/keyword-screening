package br.com.decision.application.service

import br.com.decision.application.usecase.CreateRuleConfigurationCommand
import br.com.decision.application.usecase.UpdateRuleConfigurationCommand
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.port.DryRunLogRepository
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Property-Based Tests for Configuration Version Monotonicity.
 *
 * **Property 6: Configuration Version Monotonicity**
 * **Validates: Requirements 8.2, 11.1, 11.2, 11.5**
 *
 * Properties verified:
 * 1. After N updates, currentVersion == N+1 (initial version is 1)
 * 2. Versions list grows monotonically: versions[0].version=1, versions[1].version=2, etc.
 * 3. Previous versions are immutable (not altered by subsequent updates)
 * 4. Each version has non-null createdAt
 */
class ConfigurationVersionMonotonicityPropertyTest {

    private val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()
    private val ruleConfigurationRepository = mockk<RuleConfigurationRepository>()
    private val factDefinitionRepository = mockk<FactDefinitionRepository>()
    private val dryRunLogRepository = mockk<DryRunLogRepository>()

    private val service = RuleConfigurationService(
        ruleConfigurationRepository = ruleConfigurationRepository,
        ruleDefinitionRepository = ruleDefinitionRepository,
        factDefinitionRepository = factDefinitionRepository,
        dryRunLogRepository = dryRunLogRepository
    )

    private val ruleId = RuleId(UUID.randomUUID())
    private val ruleCode = RuleCode("KEYWORD_SCREENING")

    private val ruleDefinition = RuleDefinition(
        id = ruleId,
        code = ruleCode,
        name = "Keyword Screening",
        description = "MF09 rule",
        context = RuleContext.SCREENING,
        category = RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("keywordMatched")),
        supportedActions = listOf(Action.GENERATE_ALERT, Action.IGNORE),
        status = RuleStatus.ACTIVE,
        createdAt = Instant.now()
    )

    private val factDefinition = FactDefinition(
        id = UUID.randomUUID(),
        name = FactName("keywordMatched"),
        displayName = "Keyword Matched",
        entity = "Screening",
        type = FactType.BOOLEAN,
        context = RuleContext.SCREENING,
        source = "Screening",
        supportedOperators = listOf(ComparisonOperator.EQUALS, ComparisonOperator.NOT_EQUALS),
        enabled = true
    )

    private fun validExpression() = Condition(
        factName = FactName("keywordMatched"),
        operator = ComparisonOperator.EQUALS,
        expectedValue = FactValue.BooleanValue(true)
    )

    @BeforeEach
    fun setUp() {
        every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
        every { ruleDefinitionRepository.findAll() } returns listOf(ruleDefinition)
        every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns factDefinition

        val saveSlot = slot<RuleConfiguration>()
        every { ruleConfigurationRepository.save(capture(saveSlot)) } answers { saveSlot.captured }
    }

    @RepeatedTest(200)
    @DisplayName("Property: after N updates, currentVersion == N+1 and versions list grows monotonically")
    fun `after N updates currentVersion equals N+1 and versions list grows monotonically`() {
        val numberOfUpdates = Random.nextInt(1, 6)

        // Reset mock state for each iteration
        val configSlot = slot<RuleConfiguration>()
        every { ruleConfigurationRepository.save(capture(configSlot)) } answers { configSlot.captured }

        // Create initial configuration (version 1)
        val createCommand = CreateRuleConfigurationCommand(
            ruleCode = ruleCode,
            expressions = listOf(validExpression()),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        var current = service.create(createCommand)
        val configId = current.id

        // Apply N updates
        for (i in 1..numberOfUpdates) {
            every { ruleConfigurationRepository.findById(configId) } returns current

            val updateCommand = UpdateRuleConfigurationCommand(
                expressions = listOf(validExpression()),
                actions = listOf(Action.GENERATE_ALERT),
                updatedBy = "analyst@test.com"
            )

            current = service.update(configId, updateCommand)
        }

        // Property 1: After N updates, currentVersion == N+1
        assertEquals(numberOfUpdates + 1, current.currentVersion.value)

        // Property 2: Versions list grows monotonically
        assertEquals(numberOfUpdates + 1, current.versions.size)
        current.versions.forEachIndexed { index, entry ->
            assertEquals(index + 1, entry.version.value)
        }
    }

    @RepeatedTest(200)
    @DisplayName("Property: previous versions are immutable - not altered by subsequent updates")
    fun `previous versions are immutable - not altered by subsequent updates`() {
        val numberOfUpdates = Random.nextInt(2, 6)

        val configSlot = slot<RuleConfiguration>()
        every { ruleConfigurationRepository.save(capture(configSlot)) } answers { configSlot.captured }

        // Create initial configuration
        val createCommand = CreateRuleConfigurationCommand(
            ruleCode = ruleCode,
            expressions = listOf(validExpression()),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        var current = service.create(createCommand)
        val configId = current.id

        // Track snapshots of versions at each step
        val versionSnapshots = mutableListOf(current.versions.toList())

        // Apply N updates, capturing version state at each step
        for (i in 1..numberOfUpdates) {
            every { ruleConfigurationRepository.findById(configId) } returns current

            val updateCommand = UpdateRuleConfigurationCommand(
                expressions = listOf(validExpression()),
                actions = listOf(Action.GENERATE_ALERT),
                updatedBy = "analyst@test.com"
            )

            current = service.update(configId, updateCommand)
            versionSnapshots.add(current.versions.toList())
        }

        // Property 3: Previous versions are immutable
        // Each snapshot[i] should be a prefix of the final versions list
        for (snapshotIdx in 0 until versionSnapshots.size - 1) {
            val snapshot = versionSnapshots[snapshotIdx]
            val finalVersions = current.versions

            snapshot.forEachIndexed { idx, entry ->
                assertEquals(entry.version, finalVersions[idx].version)
                assertEquals(entry.expressions, finalVersions[idx].expressions)
                assertEquals(entry.actions, finalVersions[idx].actions)
                assertEquals(entry.createdBy, finalVersions[idx].createdBy)
                assertEquals(entry.createdAt, finalVersions[idx].createdAt)
            }
        }
    }

    @RepeatedTest(200)
    @DisplayName("Property: each version entry has non-null createdAt")
    fun `each version entry has non-null createdAt`() {
        val numberOfUpdates = Random.nextInt(1, 6)

        val configSlot = slot<RuleConfiguration>()
        every { ruleConfigurationRepository.save(capture(configSlot)) } answers { configSlot.captured }

        // Create initial configuration
        val createCommand = CreateRuleConfigurationCommand(
            ruleCode = ruleCode,
            expressions = listOf(validExpression()),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        var current = service.create(createCommand)
        val configId = current.id

        // Apply N updates
        for (i in 1..numberOfUpdates) {
            every { ruleConfigurationRepository.findById(configId) } returns current

            val updateCommand = UpdateRuleConfigurationCommand(
                expressions = listOf(validExpression()),
                actions = listOf(Action.GENERATE_ALERT),
                updatedBy = "analyst@test.com"
            )

            current = service.update(configId, updateCommand)
        }

        // Property 4: Every version entry has non-null createdAt
        current.versions.forEach { entry ->
            assertNotNull(entry.createdAt)
        }
    }
}
