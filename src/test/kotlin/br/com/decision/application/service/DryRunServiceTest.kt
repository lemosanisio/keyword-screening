package br.com.decision.application.service

import br.com.decision.application.usecase.ExecuteDryRunCommand
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.exception.RuleConfigurationNotFoundException
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.port.DryRunLogRepository
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.service.ExpressionEvaluator
import br.com.decision.domain.service.RuleEngine
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("DryRunService — Unit Tests")
class DryRunServiceTest {

    private val ruleConfigurationRepository: RuleConfigurationRepository = mockk()
    private val factDefinitionRepository: FactDefinitionRepository = mockk()
    private val dryRunLogRepository: DryRunLogRepository = mockk()
    private val expressionEvaluator = ExpressionEvaluator()
    private val ruleEngine = RuleEngine(expressionEvaluator)

    private lateinit var dryRunService: DryRunService

    @BeforeEach
    fun setUp() {
        dryRunService = DryRunService(
            ruleConfigurationRepository,
            factDefinitionRepository,
            dryRunLogRepository,
            ruleEngine
        )
    }

    private fun buildConfiguration(
        id: UUID = UUID.randomUUID(),
        expressions: List<Expression> = listOf(
            Condition(FactName("keywordMatched"), ComparisonOperator.EQUALS, FactValue.BooleanValue(true)),
            Condition(FactName("customerRisk"), ComparisonOperator.GREATER_THAN_OR_EQUAL, FactValue.EnumValue("MR"))
        ),
        actions: List<Action> = listOf(Action.GENERATE_ALERT),
        active: Boolean = true,
        draft: Boolean = false,
        version: Int = 1
    ) = RuleConfiguration(
        id = id,
        ruleId = RuleId(UUID.randomUUID()),
        expressions = expressions,
        actions = actions,
        active = active,
        draft = draft,
        currentVersion = ConfigurationVersion(version),
        versions = emptyList(),
        createdBy = "analyst@company.com",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun keywordMatchedDefinition() = FactDefinition(
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

    private fun customerRiskDefinition() = FactDefinition(
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

    @Nested
    @DisplayName("execute — ALERT result")
    inner class AlertResult {

        @Test
        fun `returns ALERT when all expressions are satisfied`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()
            every { factDefinitionRepository.findByName(FactName("customerRisk")) } returns customerRiskDefinition()
            every { dryRunLogRepository.save(any()) } answers { firstArg() }

            val result = dryRunService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.ALERT)
            assertThat(result.actions).containsExactly(Action.GENERATE_ALERT)
            assertThat(result.matchedExpressions).hasSize(2)
            assertThat(result.failedExpressions).isEmpty()
            assertThat(result.configurationVersion).isEqualTo(ConfigurationVersion(1))
        }
    }

    @Nested
    @DisplayName("execute — IGNORE result")
    inner class IgnoreResult {

        @Test
        fun `returns IGNORE when not all expressions are satisfied`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("BR")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()
            every { factDefinitionRepository.findByName(FactName("customerRisk")) } returns customerRiskDefinition()
            every { dryRunLogRepository.save(any()) } answers { firstArg() }

            val result = dryRunService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()
            assertThat(result.matchedExpressions).hasSize(1)
            assertThat(result.failedExpressions).hasSize(1)
        }

        @Test
        fun `returns IGNORE when keyword not matched`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(false),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()
            every { factDefinitionRepository.findByName(FactName("customerRisk")) } returns customerRiskDefinition()
            every { dryRunLogRepository.save(any()) } answers { firstArg() }

            val result = dryRunService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()
        }
    }

    @Nested
    @DisplayName("execute — configuration not found")
    inner class ConfigurationNotFound {

        @Test
        fun `throws RuleConfigurationNotFoundException when config does not exist`() {
            val configId = UUID.randomUUID()
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns null

            assertThatThrownBy { dryRunService.execute(command) }
                .isInstanceOf(RuleConfigurationNotFoundException::class.java)
                .hasMessageContaining(configId.toString())
        }
    }

    @Nested
    @DisplayName("execute — fact validation")
    inner class FactValidation {

        @Test
        fun `throws InvalidConfigurationException when factName does not exist in catalog`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val facts = mapOf(FactName("unknownFact") to FactValue.BooleanValue(true))
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("unknownFact")) } returns null

            assertThatThrownBy { dryRunService.execute(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("unknownFact")
                .hasMessageContaining("não existe no catálogo")
        }

        @Test
        fun `throws InvalidConfigurationException when fact is disabled`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val disabledDefinition = keywordMatchedDefinition().copy(enabled = false)
            val facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns disabledDefinition

            assertThatThrownBy { dryRunService.execute(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("keywordMatched")
                .hasMessageContaining("desabilitado")
        }

        @Test
        fun `throws InvalidConfigurationException when fact type is incompatible`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            // keywordMatched is BOOLEAN, but we pass EnumValue
            val facts = mapOf(FactName("keywordMatched") to FactValue.EnumValue("SOME_VALUE"))
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()

            assertThatThrownBy { dryRunService.execute(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("keywordMatched")
                .hasMessageContaining("BOOLEAN")
                .hasMessageContaining("ENUM")
        }

        @Test
        fun `aggregates multiple validation errors`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val facts = mapOf(
                FactName("unknownFact") to FactValue.BooleanValue(true),
                FactName("keywordMatched") to FactValue.EnumValue("WRONG_TYPE")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("unknownFact")) } returns null
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()

            assertThatThrownBy { dryRunService.execute(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("unknownFact")
                .hasMessageContaining("keywordMatched")
        }
    }

    @Nested
    @DisplayName("execute — DryRunLog persistence")
    inner class DryRunLogPersistence {

        @Test
        fun `persists DryRunLog with correct data`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId, version = 3)
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")
            val logSlot = slot<DryRunLog>()

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()
            every { factDefinitionRepository.findByName(FactName("customerRisk")) } returns customerRiskDefinition()
            every { dryRunLogRepository.save(capture(logSlot)) } answers { firstArg() }

            dryRunService.execute(command)

            verify(exactly = 1) { dryRunLogRepository.save(any()) }
            val captured = logSlot.captured
            assertThat(captured.configurationId).isEqualTo(configId)
            assertThat(captured.version).isEqualTo(ConfigurationVersion(3))
            assertThat(captured.facts).isEqualTo(facts)
            assertThat(captured.executedBy).isEqualTo("analyst@company.com")
            assertThat(captured.result.decision).isEqualTo(Decision.ALERT)
            assertThat(captured.result.actions).containsExactly(Action.GENERATE_ALERT)
            assertThat(captured.createdAt).isNotNull()
        }
    }

    @Nested
    @DisplayName("execute — works for draft AND active configs")
    inner class DraftAndActiveConfigs {

        @Test
        fun `works for draft configuration`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId, active = false, draft = true)
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()
            every { factDefinitionRepository.findByName(FactName("customerRisk")) } returns customerRiskDefinition()
            every { dryRunLogRepository.save(any()) } answers { firstArg() }

            val result = dryRunService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.ALERT)
            assertThat(result.actions).containsExactly(Action.GENERATE_ALERT)
        }

        @Test
        fun `works for active configuration`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId, active = true, draft = false)
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()
            every { factDefinitionRepository.findByName(FactName("customerRisk")) } returns customerRiskDefinition()
            every { dryRunLogRepository.save(any()) } answers { firstArg() }

            val result = dryRunService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.ALERT)
            assertThat(result.actions).containsExactly(Action.GENERATE_ALERT)
        }
    }

    @Nested
    @DisplayName("execute — no side effects (no DecisionExecution, no events, no FactResolvers)")
    inner class NoSideEffects {

        @Test
        fun `does not persist DecisionExecution or publish events`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst@company.com")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedDefinition()
            every { factDefinitionRepository.findByName(FactName("customerRisk")) } returns customerRiskDefinition()
            every { dryRunLogRepository.save(any()) } answers { firstArg() }

            dryRunService.execute(command)

            // Only DryRunLog is persisted — no other repository or event publisher interaction
            verify(exactly = 1) { dryRunLogRepository.save(any()) }
            verify(exactly = 1) { ruleConfigurationRepository.findById(configId) }
            verify(exactly = 1) { factDefinitionRepository.findByName(FactName("keywordMatched")) }
            verify(exactly = 1) { factDefinitionRepository.findByName(FactName("customerRisk")) }
        }
    }

    @Nested
    @DisplayName("execute — type compatibility branches")
    inner class TypeCompatibilityBranches {

        @Test
        fun `MONEY fact with correct type passes validation`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(
                id = configId,
                expressions = listOf(
                    Condition(FactName("keywordMatched"), ComparisonOperator.EQUALS, FactValue.BooleanValue(true))
                )
            )
            val moneyDefinition = FactDefinition(
                id = UUID.randomUUID(),
                name = FactName("txAmount"),
                displayName = "Transaction Amount",
                entity = "Transaction",
                type = FactType.MONEY,
                context = RuleContext.SCREENING,
                source = "Payment",
                supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
                enabled = true
            )
            val facts = mapOf(
                FactName("txAmount") to FactValue.MoneyValue(java.math.BigDecimal("5000"), "BRL")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("txAmount")) } returns moneyDefinition
            every { dryRunLogRepository.save(any()) } answers { firstArg() }

            val result = dryRunService.execute(command)

            // Validation passes (no exception) and result is returned
            assertThat(result.decision).isIn(Decision.ALERT, Decision.IGNORE)
        }

        @Test
        fun `STRING fact with StringValue passes validation`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(
                id = configId,
                expressions = listOf(
                    Condition(FactName("keywordMatched"), ComparisonOperator.EQUALS, FactValue.BooleanValue(true))
                )
            )
            val stringDefinition = FactDefinition(
                id = UUID.randomUUID(),
                name = FactName("description"),
                displayName = "Description",
                entity = "Transaction",
                type = FactType.STRING,
                context = RuleContext.SCREENING,
                source = "Payment",
                supportedOperators = listOf(ComparisonOperator.CONTAINS),
                enabled = true
            )
            val facts = mapOf(
                FactName("description") to FactValue.StringValue("test payment")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("description")) } returns stringDefinition
            every { dryRunLogRepository.save(any()) } answers { firstArg() }

            val result = dryRunService.execute(command)

            assertThat(result.decision).isIn(Decision.ALERT, Decision.IGNORE)
        }

        @Test
        fun `NUMBER fact with wrong type (StringValue) throws`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val numberDefinition = FactDefinition(
                id = UUID.randomUUID(),
                name = FactName("amount"),
                displayName = "Amount",
                entity = "Transaction",
                type = FactType.NUMBER,
                context = RuleContext.SCREENING,
                source = "Payment",
                supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
                enabled = true
            )
            val facts = mapOf(
                FactName("amount") to FactValue.StringValue("not a number")
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("amount")) } returns numberDefinition

            assertThatThrownBy { dryRunService.execute(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("amount")
                .hasMessageContaining("NUMBER")
                .hasMessageContaining("STRING")
        }

        @Test
        fun `MONEY fact with BooleanValue throws incompatible type`() {
            val configId = UUID.randomUUID()
            val config = buildConfiguration(id = configId)
            val moneyDefinition = FactDefinition(
                id = UUID.randomUUID(),
                name = FactName("txAmount"),
                displayName = "Transaction Amount",
                entity = "Transaction",
                type = FactType.MONEY,
                context = RuleContext.SCREENING,
                source = "Payment",
                supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
                enabled = true
            )
            val facts = mapOf(
                FactName("txAmount") to FactValue.BooleanValue(true)
            )
            val command = ExecuteDryRunCommand(configId, facts, "analyst")

            every { ruleConfigurationRepository.findById(configId) } returns config
            every { factDefinitionRepository.findByName(FactName("txAmount")) } returns moneyDefinition

            assertThatThrownBy { dryRunService.execute(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("txAmount")
                .hasMessageContaining("MONEY")
                .hasMessageContaining("BOOLEAN")
        }
    }
}
