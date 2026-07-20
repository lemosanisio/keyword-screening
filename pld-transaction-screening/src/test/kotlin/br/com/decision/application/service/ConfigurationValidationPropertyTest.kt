package br.com.decision.application.service

import br.com.decision.application.usecase.CreateRuleConfigurationCommand
import br.com.decision.domain.exception.InvalidConfigurationException
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
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Property-Based Tests for [RuleConfigurationService] — Configuration Validation.
 *
 * **Property 1: Configuration Validation Correctness**
 * **Validates: Requirements 1.2, 1.3, 1.4, 1.5, 2.2, 2.3, 2.4, 8.5, 8.6**
 *
 * Properties verified:
 * 1. Valid configuration (all facts exist, enabled, in supportedFacts, operators in supportedOperators, types match) → creation succeeds
 * 2. Invalid factName (not in catalog) → throws InvalidConfigurationException
 * 3. Disabled fact → throws InvalidConfigurationException
 * 4. Fact not in supportedFacts → throws InvalidConfigurationException
 * 5. Operator not in supportedOperators → throws InvalidConfigurationException
 * 6. Type incompatibility → throws InvalidConfigurationException
 */
class ConfigurationValidationPropertyTest {

    // --- Shared fixtures ---

    private val ruleId = RuleId(UUID.randomUUID())

    private val keywordMatchedFact = FactDefinition(
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

    private val customerRiskFact = FactDefinition(
        id = UUID.randomUUID(),
        name = FactName("customerRisk"),
        displayName = "Customer Risk",
        entity = "Risk",
        type = FactType.ENUM,
        context = RuleContext.CUSTOMER,
        source = "PLD",
        supportedOperators = listOf(
            ComparisonOperator.EQUALS,
            ComparisonOperator.NOT_EQUALS,
            ComparisonOperator.GREATER_THAN_OR_EQUAL
        ),
        enabled = true
    )

    private val ruleDefinition = RuleDefinition(
        id = ruleId,
        code = RuleCode("KEYWORD_SCREENING"),
        name = "Keyword Screening",
        description = "Detecção de termos restritos",
        context = RuleContext.SCREENING,
        category = RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("keywordMatched"), FactName("customerRisk")),
        supportedActions = listOf(Action.GENERATE_ALERT, Action.IGNORE),
        status = RuleStatus.ACTIVE,
        createdAt = Instant.now()
    )

    // Shared mocks — cleared between iterations
    private val factDefRepo = mockk<FactDefinitionRepository>()
    private val ruleDefRepo = mockk<RuleDefinitionRepository>()
    private val ruleConfigRepo = mockk<RuleConfigurationRepository>()
    private val dryRunLogRepo = mockk<DryRunLogRepository>()

    private val service = RuleConfigurationService(ruleConfigRepo, ruleDefRepo, factDefRepo, dryRunLogRepo)

    @BeforeEach
    fun setUp() {
        clearMocks(factDefRepo, ruleDefRepo, ruleConfigRepo, dryRunLogRepo)
    }

    // --- Helper: generate a valid condition ---

    private fun randomValidCondition(): Condition {
        val useBoolean = Random.nextBoolean()
        return if (useBoolean) {
            Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(Random.nextBoolean())
            )
        } else {
            Condition(
                factName = FactName("customerRisk"),
                operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
                expectedValue = FactValue.EnumValue(listOf("BR", "MR", "AR")[Random.nextInt(3)])
            )
        }
    }

    // --- Property Tests ---

    @RepeatedTest(100)
    @DisplayName("Property: valid configuration with all criteria met → creation succeeds")
    fun `valid configuration with all criteria met - creation succeeds`() {
        val condition = randomValidCondition()

        every { ruleDefRepo.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
        every { factDefRepo.findByName(FactName("keywordMatched")) } returns keywordMatchedFact
        every { factDefRepo.findByName(FactName("customerRisk")) } returns customerRiskFact

        val saved = slot<RuleConfiguration>()
        every { ruleConfigRepo.save(capture(saved)) } answers { saved.captured }

        val command = CreateRuleConfigurationCommand(
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            expressions = listOf(condition),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        val result = service.create(command)

        assertEquals(listOf(condition), result.expressions)
        assertEquals(true, result.draft)
        assertEquals(false, result.active)
    }

    @RepeatedTest(100)
    @DisplayName("Property: invalid factName (not in catalog) → throws InvalidConfigurationException")
    fun `invalid factName not in catalog - throws InvalidConfigurationException`() {
        val invalidNames = listOf("unknownFact", "nonExistent", "invalidName", "ghostFact", "badFact")
        val invalidName = invalidNames[Random.nextInt(invalidNames.size)]

        every { ruleDefRepo.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
        every { factDefRepo.findByName(FactName(invalidName)) } returns null

        val command = CreateRuleConfigurationCommand(
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            expressions = listOf(
                Condition(
                    factName = FactName(invalidName),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        assertThrows<InvalidConfigurationException> { service.create(command) }
    }

    @RepeatedTest(100)
    @DisplayName("Property: disabled fact → throws InvalidConfigurationException")
    fun `disabled fact - throws InvalidConfigurationException`() {
        val boolValue = Random.nextBoolean()

        val disabledFact = keywordMatchedFact.copy(enabled = false)

        every { ruleDefRepo.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
        every { factDefRepo.findByName(FactName("keywordMatched")) } returns disabledFact

        val command = CreateRuleConfigurationCommand(
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(boolValue)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        assertThrows<InvalidConfigurationException> { service.create(command) }
    }

    @RepeatedTest(100)
    @DisplayName("Property: fact not in supportedFacts of RuleDefinition → throws InvalidConfigurationException")
    fun `fact not in supportedFacts of RuleDefinition - throws InvalidConfigurationException`() {
        val boolValue = Random.nextBoolean()

        val restrictedRule = ruleDefinition.copy(
            supportedFacts = listOf(FactName("customerRisk"))
        )

        every { ruleDefRepo.findByCode(RuleCode("KEYWORD_SCREENING")) } returns restrictedRule
        every { factDefRepo.findByName(FactName("keywordMatched")) } returns keywordMatchedFact

        val command = CreateRuleConfigurationCommand(
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(boolValue)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        assertThrows<InvalidConfigurationException> { service.create(command) }
    }

    @RepeatedTest(100)
    @DisplayName("Property: operator not in supportedOperators → throws InvalidConfigurationException")
    fun `operator not in supportedOperators - throws InvalidConfigurationException`() {
        val unsupportedOperators = listOf(
            ComparisonOperator.GREATER_THAN,
            ComparisonOperator.GREATER_THAN_OR_EQUAL,
            ComparisonOperator.LESS_THAN,
            ComparisonOperator.LESS_THAN_OR_EQUAL,
            ComparisonOperator.IN,
            ComparisonOperator.NOT_IN,
            ComparisonOperator.CONTAINS
        )
        val unsupportedOp = unsupportedOperators[Random.nextInt(unsupportedOperators.size)]

        every { ruleDefRepo.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
        every { factDefRepo.findByName(FactName("keywordMatched")) } returns keywordMatchedFact

        val command = CreateRuleConfigurationCommand(
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = unsupportedOp,
                    expectedValue = FactValue.BooleanValue(true)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        assertThrows<InvalidConfigurationException> { service.create(command) }
    }

    @RepeatedTest(100)
    @DisplayName("Property: type incompatibility → throws InvalidConfigurationException")
    fun `type incompatibility - throws InvalidConfigurationException`() {
        val incompatibleValues = listOf(
            FactValue.EnumValue("SOME_VALUE"),
            FactValue.StringValue("text"),
            FactValue.NumberValue(java.math.BigDecimal.TEN),
            FactValue.MoneyValue(java.math.BigDecimal.ONE, "BRL")
        )
        val incompatibleValue = incompatibleValues[Random.nextInt(incompatibleValues.size)]

        every { ruleDefRepo.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
        every { factDefRepo.findByName(FactName("keywordMatched")) } returns keywordMatchedFact

        val command = CreateRuleConfigurationCommand(
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = incompatibleValue
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        assertThrows<InvalidConfigurationException> { service.create(command) }
    }
}
