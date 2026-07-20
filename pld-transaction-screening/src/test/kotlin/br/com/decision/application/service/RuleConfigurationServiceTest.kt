package br.com.decision.application.service

import br.com.decision.application.usecase.CreateRuleConfigurationCommand
import br.com.decision.application.usecase.UpdateRuleConfigurationCommand
import br.com.decision.domain.exception.DuplicateActiveConfigException
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.exception.RuleConfigurationNotFoundException
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ConfigurationVersionEntry
import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.model.DryRunLogResult
import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.port.DryRunLogRepository
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
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

@DisplayName("RuleConfigurationService — Unit Tests")
class RuleConfigurationServiceTest {

    private val ruleConfigurationRepository = mockk<RuleConfigurationRepository>()
    private val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()
    private val factDefinitionRepository = mockk<FactDefinitionRepository>()
    private val dryRunLogRepository = mockk<DryRunLogRepository>()

    private lateinit var service: RuleConfigurationService

    private val ruleId = RuleId(UUID.randomUUID())
    private val ruleCode = RuleCode("KEYWORD_SCREENING")

    private val ruleDefinition = RuleDefinition(
        id = ruleId,
        code = ruleCode,
        name = "Keyword Screening",
        description = "Detecção de termos restritos",
        context = RuleContext.SCREENING,
        category = RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("keywordMatched"), FactName("customerRisk")),
        supportedActions = listOf(Action.GENERATE_ALERT, Action.IGNORE),
        status = RuleStatus.ACTIVE,
        createdAt = Instant.now()
    )

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

    private val validExpressions: List<Expression> = listOf(
        Condition(
            factName = FactName("keywordMatched"),
            operator = ComparisonOperator.EQUALS,
            expectedValue = FactValue.BooleanValue(true)
        ),
        Condition(
            factName = FactName("customerRisk"),
            operator = ComparisonOperator.GREATER_THAN_OR_EQUAL,
            expectedValue = FactValue.EnumValue("MR")
        )
    )

    @BeforeEach
    fun setup() {
        service = RuleConfigurationService(
            ruleConfigurationRepository,
            ruleDefinitionRepository,
            factDefinitionRepository,
            dryRunLogRepository
        )
    }

    @Nested
    @DisplayName("create")
    inner class Create {

        @Test
        fun `creates configuration in draft state with version 1`() {
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = validExpressions,
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedFact
            every { factDefinitionRepository.findByName(FactName("customerRisk")) } returns customerRiskFact

            val saved = slot<RuleConfiguration>()
            every { ruleConfigurationRepository.save(capture(saved)) } answers { saved.captured }

            val result = service.create(command)

            assertThat(result.draft).isTrue()
            assertThat(result.active).isFalse()
            assertThat(result.currentVersion).isEqualTo(ConfigurationVersion(1))
            assertThat(result.versions).hasSize(1)
            assertThat(result.versions[0].version).isEqualTo(ConfigurationVersion(1))
            assertThat(result.expressions).isEqualTo(validExpressions)
            assertThat(result.actions).containsExactly(Action.GENERATE_ALERT)
            assertThat(result.createdBy).isEqualTo("analyst@company.com")
            assertThat(result.ruleId).isEqualTo(ruleId)
        }

        @Test
        fun `throws RuleConfigurationNotFoundException when ruleCode not found`() {
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("NONEXISTENT_RULE"),
                expressions = validExpressions,
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("NONEXISTENT_RULE")) } returns null

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(RuleConfigurationNotFoundException::class.java)
                .hasMessageContaining("NONEXISTENT_RULE")
        }

        @Test
        fun `throws InvalidConfigurationException when factName not in catalog`() {
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = listOf(
                    Condition(
                        factName = FactName("unknownFact"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.BooleanValue(true)
                    )
                ),
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
            every { factDefinitionRepository.findByName(FactName("unknownFact")) } returns null

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("unknownFact")
                .hasMessageContaining("não encontrado")
        }

        @Test
        fun `throws InvalidConfigurationException when fact is disabled`() {
            val disabledFact = keywordMatchedFact.copy(enabled = false)
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = listOf(
                    Condition(
                        factName = FactName("keywordMatched"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.BooleanValue(true)
                    )
                ),
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns disabledFact

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("desabilitado")
        }

        @Test
        fun `throws InvalidConfigurationException when fact not in supportedFacts`() {
            val ruleWithLimitedFacts = ruleDefinition.copy(
                supportedFacts = listOf(FactName("customerRisk")) // keywordMatched NOT supported
            )
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = listOf(
                    Condition(
                        factName = FactName("keywordMatched"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.BooleanValue(true)
                    )
                ),
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleWithLimitedFacts
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedFact

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("não é suportado")
        }

        @Test
        fun `throws InvalidConfigurationException when operator not supported`() {
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = listOf(
                    Condition(
                        factName = FactName("keywordMatched"),
                        operator = ComparisonOperator.GREATER_THAN, // not in keywordMatchedFact.supportedOperators
                        expectedValue = FactValue.BooleanValue(true)
                    )
                ),
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedFact

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("Operador")
                .hasMessageContaining("não é suportado")
        }

        @Test
        fun `throws InvalidConfigurationException when type is incompatible`() {
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = listOf(
                    Condition(
                        factName = FactName("keywordMatched"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.EnumValue("WRONG") // BOOLEAN fact expects BooleanValue
                    )
                ),
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedFact

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("incompatível")
        }

        @Test
        fun `throws InvalidConfigurationException when NUMBER fact gets StringValue`() {
            val numberFact = FactDefinition(
                id = UUID.randomUUID(),
                name = FactName("amount"),
                displayName = "Amount",
                entity = "Transaction",
                type = FactType.NUMBER,
                context = RuleContext.SCREENING,
                source = "Payment",
                supportedOperators = listOf(ComparisonOperator.EQUALS, ComparisonOperator.GREATER_THAN),
                enabled = true
            )
            val ruleDefWithAmount = ruleDefinition.copy(
                supportedFacts = listOf(FactName("amount"))
            )
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = listOf(
                    Condition(
                        factName = FactName("amount"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.StringValue("not a number")
                    )
                ),
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefWithAmount
            every { factDefinitionRepository.findByName(FactName("amount")) } returns numberFact

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("incompatível")
        }

        @Test
        fun `throws InvalidConfigurationException when STRING fact gets BooleanValue`() {
            val stringFact = FactDefinition(
                id = UUID.randomUUID(),
                name = FactName("description"),
                displayName = "Description",
                entity = "Transaction",
                type = FactType.STRING,
                context = RuleContext.SCREENING,
                source = "Payment",
                supportedOperators = listOf(ComparisonOperator.EQUALS, ComparisonOperator.CONTAINS),
                enabled = true
            )
            val ruleDefWithDesc = ruleDefinition.copy(
                supportedFacts = listOf(FactName("description"))
            )
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = listOf(
                    Condition(
                        factName = FactName("description"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.BooleanValue(true)
                    )
                ),
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefWithDesc
            every { factDefinitionRepository.findByName(FactName("description")) } returns stringFact

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("incompatível")
        }

        @Test
        fun `throws InvalidConfigurationException when MONEY fact gets NumberValue`() {
            val moneyFact = FactDefinition(
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
            val ruleDefWithMoney = ruleDefinition.copy(
                supportedFacts = listOf(FactName("txAmount"))
            )
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = listOf(
                    Condition(
                        factName = FactName("txAmount"),
                        operator = ComparisonOperator.GREATER_THAN,
                        expectedValue = FactValue.NumberValue(java.math.BigDecimal("5000"))
                    )
                ),
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefWithMoney
            every { factDefinitionRepository.findByName(FactName("txAmount")) } returns moneyFact

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("incompatível")
        }

        @Test
        fun `throws InvalidConfigurationException when more than 10 expressions`() {
            val tooManyExpressions = (1..11).map {
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            }
            val command = CreateRuleConfigurationCommand(
                ruleCode = RuleCode("KEYWORD_SCREENING"),
                expressions = tooManyExpressions,
                actions = listOf(Action.GENERATE_ALERT),
                createdBy = "analyst@company.com"
            )

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition

            assertThatThrownBy { service.create(command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("10")
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {

        private val configId = UUID.randomUUID()
        private val existingConfig = RuleConfiguration(
            id = configId,
            ruleId = ruleId,
            expressions = validExpressions,
            actions = listOf(Action.GENERATE_ALERT),
            active = false,
            draft = true,
            currentVersion = ConfigurationVersion(1),
            versions = listOf(
                ConfigurationVersionEntry(
                    version = ConfigurationVersion(1),
                    expressions = validExpressions,
                    actions = listOf(Action.GENERATE_ALERT),
                    active = false,
                    createdBy = "analyst@company.com",
                    createdAt = Instant.now()
                )
            ),
            createdBy = "analyst@company.com",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        @Test
        fun `increments version and adds new ConfigurationVersionEntry`() {
            val newExpressions: List<Expression> = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            )
            val command = UpdateRuleConfigurationCommand(
                expressions = newExpressions,
                actions = listOf(Action.GENERATE_ALERT),
                updatedBy = "another-analyst@company.com"
            )

            every { ruleConfigurationRepository.findById(configId) } returns existingConfig
            every { ruleDefinitionRepository.findAll() } returns listOf(ruleDefinition)
            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns keywordMatchedFact

            val saved = slot<RuleConfiguration>()
            every { ruleConfigurationRepository.save(capture(saved)) } answers { saved.captured }

            val result = service.update(configId, command)

            assertThat(result.currentVersion).isEqualTo(ConfigurationVersion(2))
            assertThat(result.versions).hasSize(2)
            assertThat(result.versions[1].version).isEqualTo(ConfigurationVersion(2))
            assertThat(result.versions[1].createdBy).isEqualTo("another-analyst@company.com")
            assertThat(result.expressions).isEqualTo(newExpressions)
        }

        @Test
        fun `throws RuleConfigurationNotFoundException when config not found`() {
            val command = UpdateRuleConfigurationCommand(
                expressions = validExpressions,
                actions = listOf(Action.GENERATE_ALERT),
                updatedBy = "analyst@company.com"
            )
            val nonExistentId = UUID.randomUUID()

            every { ruleConfigurationRepository.findById(nonExistentId) } returns null

            assertThatThrownBy { service.update(nonExistentId, command) }
                .isInstanceOf(RuleConfigurationNotFoundException::class.java)
        }

        @Test
        fun `validates expressions on update`() {
            val invalidExpressions: List<Expression> = listOf(
                Condition(
                    factName = FactName("unknownFact"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            )
            val command = UpdateRuleConfigurationCommand(
                expressions = invalidExpressions,
                actions = listOf(Action.GENERATE_ALERT),
                updatedBy = "analyst@company.com"
            )

            every { ruleConfigurationRepository.findById(configId) } returns existingConfig
            every { ruleDefinitionRepository.findAll() } returns listOf(ruleDefinition)
            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every { factDefinitionRepository.findByName(FactName("unknownFact")) } returns null

            assertThatThrownBy { service.update(configId, command) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("unknownFact")
        }
    }

    @Nested
    @DisplayName("activate")
    inner class Activate {

        private val configId = UUID.randomUUID()
        private val existingConfig = RuleConfiguration(
            id = configId,
            ruleId = ruleId,
            expressions = validExpressions,
            actions = listOf(Action.GENERATE_ALERT),
            active = false,
            draft = true,
            currentVersion = ConfigurationVersion(1),
            versions = listOf(
                ConfigurationVersionEntry(
                    version = ConfigurationVersion(1),
                    expressions = validExpressions,
                    actions = listOf(Action.GENERATE_ALERT),
                    active = false,
                    createdBy = "analyst@company.com",
                    createdAt = Instant.now()
                )
            ),
            createdBy = "analyst@company.com",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        @Test
        fun `activates configuration when dry-run exists and no active config for same ruleId`() {
            val dryRunLog = DryRunLog(
                id = UUID.randomUUID(),
                configurationId = configId,
                version = ConfigurationVersion(1),
                facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
                result = DryRunLogResult(decision = Decision.ALERT, actions = listOf(Action.GENERATE_ALERT)),
                executedBy = "analyst@company.com",
                createdAt = Instant.now()
            )

            every { ruleConfigurationRepository.findById(configId) } returns existingConfig
            every { dryRunLogRepository.findByConfigurationIdAndVersion(configId, ConfigurationVersion(1)) } returns listOf(dryRunLog)
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns null

            val saved = slot<RuleConfiguration>()
            every { ruleConfigurationRepository.save(capture(saved)) } answers { saved.captured }

            val result = service.activate(configId)

            assertThat(result.active).isTrue()
            assertThat(result.draft).isFalse()
        }

        @Test
        fun `throws InvalidConfigurationException when no dry-run exists`() {
            every { ruleConfigurationRepository.findById(configId) } returns existingConfig
            every { dryRunLogRepository.findByConfigurationIdAndVersion(configId, ConfigurationVersion(1)) } returns emptyList()

            assertThatThrownBy { service.activate(configId) }
                .isInstanceOf(InvalidConfigurationException::class.java)
                .hasMessageContaining("Dry-run obrigatório")
        }

        @Test
        fun `throws DuplicateActiveConfigException when another active config exists`() {
            val otherActiveConfig = existingConfig.copy(
                id = UUID.randomUUID(),
                active = true
            )
            val dryRunLog = DryRunLog(
                id = UUID.randomUUID(),
                configurationId = configId,
                version = ConfigurationVersion(1),
                facts = emptyMap(),
                result = DryRunLogResult(decision = Decision.ALERT, actions = listOf(Action.GENERATE_ALERT)),
                executedBy = "analyst@company.com",
                createdAt = Instant.now()
            )

            every { ruleConfigurationRepository.findById(configId) } returns existingConfig
            every { dryRunLogRepository.findByConfigurationIdAndVersion(configId, ConfigurationVersion(1)) } returns listOf(dryRunLog)
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns otherActiveConfig

            assertThatThrownBy { service.activate(configId) }
                .isInstanceOf(DuplicateActiveConfigException::class.java)
                .hasMessageContaining("Já existe uma configuração ativa")
        }

        @Test
        fun `throws RuleConfigurationNotFoundException when config not found`() {
            val nonExistentId = UUID.randomUUID()

            every { ruleConfigurationRepository.findById(nonExistentId) } returns null

            assertThatThrownBy { service.activate(nonExistentId) }
                .isInstanceOf(RuleConfigurationNotFoundException::class.java)
        }

        @Test
        fun `allows activation when same config is already the active one`() {
            val activeConfig = existingConfig.copy(active = true, draft = false)
            val dryRunLog = DryRunLog(
                id = UUID.randomUUID(),
                configurationId = configId,
                version = ConfigurationVersion(1),
                facts = emptyMap(),
                result = DryRunLogResult(decision = Decision.IGNORE, actions = emptyList()),
                executedBy = "analyst@company.com",
                createdAt = Instant.now()
            )

            every { ruleConfigurationRepository.findById(configId) } returns existingConfig
            every { dryRunLogRepository.findByConfigurationIdAndVersion(configId, ConfigurationVersion(1)) } returns listOf(dryRunLog)
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig.copy(id = configId)

            val saved = slot<RuleConfiguration>()
            every { ruleConfigurationRepository.save(capture(saved)) } answers { saved.captured }

            val result = service.activate(configId)

            assertThat(result.active).isTrue()
        }
    }

    @Nested
    @DisplayName("deactivate")
    inner class Deactivate {

        private val configId = UUID.randomUUID()
        private val activeConfig = RuleConfiguration(
            id = configId,
            ruleId = ruleId,
            expressions = validExpressions,
            actions = listOf(Action.GENERATE_ALERT),
            active = true,
            draft = false,
            currentVersion = ConfigurationVersion(1),
            versions = listOf(
                ConfigurationVersionEntry(
                    version = ConfigurationVersion(1),
                    expressions = validExpressions,
                    actions = listOf(Action.GENERATE_ALERT),
                    active = true,
                    createdBy = "analyst@company.com",
                    createdAt = Instant.now()
                )
            ),
            createdBy = "analyst@company.com",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        @Test
        fun `deactivates configuration`() {
            every { ruleConfigurationRepository.findById(configId) } returns activeConfig

            val saved = slot<RuleConfiguration>()
            every { ruleConfigurationRepository.save(capture(saved)) } answers { saved.captured }

            val result = service.deactivate(configId)

            assertThat(result.active).isFalse()
        }

        @Test
        fun `throws RuleConfigurationNotFoundException when config not found`() {
            val nonExistentId = UUID.randomUUID()

            every { ruleConfigurationRepository.findById(nonExistentId) } returns null

            assertThatThrownBy { service.deactivate(nonExistentId) }
                .isInstanceOf(RuleConfigurationNotFoundException::class.java)
        }
    }
}
