package br.com.decision.infrastructure.input.http

import br.com.decision.application.usecase.ManageRuleConfigurationUseCase
import br.com.decision.domain.model.*
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.infrastructure.input.http.handler.DecisionExceptionHandler
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@DisplayName("RuleConfigurationController")
class RuleConfigurationControllerTest {

    private val manageUseCase = mockk<ManageRuleConfigurationUseCase>()
    private val ruleConfigRepository = mockk<RuleConfigurationRepository>()
    private val ruleDefRepository = mockk<RuleDefinitionRepository>()
    private val controller = RuleConfigurationController(manageUseCase, ruleConfigRepository, ruleDefRepository)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(DecisionExceptionHandler())
        .build()
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    private val configId = UUID.randomUUID()
    private val ruleId = UUID.randomUUID()
    private val now = Instant.now()

    private fun buildConfig() = RuleConfiguration(
        id = configId,
        ruleId = RuleId(ruleId),
        expressions = listOf(
            Condition(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(true)
            )
        ),
        actions = listOf(Action.GENERATE_ALERT),
        active = true,
        draft = false,
        currentVersion = ConfigurationVersion(1),
        versions = emptyList(),
        createdBy = "analyst",
        createdAt = now,
        updatedAt = now
    )

    private fun buildRuleDef() = RuleDefinition(
        id = RuleId(ruleId),
        code = RuleCode("KEYWORD_ALERT"),
        name = "Keyword Alert",
        description = "Alert on keyword",
        context = br.com.decision.domain.model.enums.RuleContext.SCREENING,
        category = br.com.decision.domain.model.enums.RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("keywordMatched")),
        supportedActions = listOf(Action.GENERATE_ALERT),
        status = br.com.decision.domain.model.enums.RuleStatus.ACTIVE,
        createdAt = now
    )

    @Test
    @DisplayName("create: valid request returns 201")
    fun createReturns201() {
        val config = buildConfig()
        every { manageUseCase.create(any()) } returns config

        val requestBody = mapOf(
            "expressions" to listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "keywordMatched",
                    "operator" to "EQUALS",
                    "expectedValue" to true
                )
            ),
            "actions" to listOf("GENERATE_ALERT"),
            "createdBy" to "analyst"
        )

        mockMvc.post("/v1/decision/rules/KEYWORD_ALERT/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(configId.toString()) }
            jsonPath("$.ruleCode") { value("KEYWORD_ALERT") }
            jsonPath("$.active") { value(true) }
        }
    }

    @Test
    @DisplayName("create: missing createdBy returns 400")
    fun createMissingCreatedByReturns400() {
        val requestBody = mapOf(
            "expressions" to listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "keywordMatched",
                    "operator" to "EQUALS",
                    "expectedValue" to true
                )
            ),
            "actions" to listOf("GENERATE_ALERT"),
            "createdBy" to ""
        )

        mockMvc.post("/v1/decision/rules/KEYWORD_ALERT/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("getById: existing config returns 200")
    fun getByIdReturns200() {
        val config = buildConfig()
        val ruleDef = buildRuleDef()
        every { ruleConfigRepository.findById(configId) } returns config
        every { ruleDefRepository.findAll() } returns listOf(ruleDef)

        mockMvc.get("/v1/decision/rule-configurations/$configId") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(configId.toString()) }
            jsonPath("$.ruleCode") { value("KEYWORD_ALERT") }
        }
    }

    @Test
    @DisplayName("getById: non-existent config returns 404")
    fun getByIdReturns404() {
        val id = UUID.randomUUID()
        every { ruleConfigRepository.findById(id) } returns null

        mockMvc.get("/v1/decision/rule-configurations/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    @DisplayName("activate: returns 200 with updated config")
    fun activateReturns200() {
        val config = buildConfig()
        val ruleDef = buildRuleDef()
        every { manageUseCase.activate(configId) } returns config
        every { ruleDefRepository.findAll() } returns listOf(ruleDef)

        mockMvc.post("/v1/decision/rule-configurations/$configId/activate") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(true) }
        }
    }

    @Test
    @DisplayName("deactivate: returns 200 with updated config")
    fun deactivateReturns200() {
        val deactivated = buildConfig().copy(active = false)
        val ruleDef = buildRuleDef()
        every { manageUseCase.deactivate(configId) } returns deactivated
        every { ruleDefRepository.findAll() } returns listOf(ruleDef)

        mockMvc.post("/v1/decision/rule-configurations/$configId/deactivate") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(false) }
        }
    }

    @Test
    @DisplayName("update: valid request returns 200")
    fun updateReturns200() {
        val config = buildConfig()
        val ruleDef = buildRuleDef()
        every { manageUseCase.update(configId, any()) } returns config
        every { ruleDefRepository.findAll() } returns listOf(ruleDef)

        val requestBody = mapOf(
            "expressions" to listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "keywordMatched",
                    "operator" to "EQUALS",
                    "expectedValue" to true
                )
            ),
            "actions" to listOf("GENERATE_ALERT"),
            "updatedBy" to "analyst"
        )

        mockMvc.put("/v1/decision/rule-configurations/$configId") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(configId.toString()) }
        }
    }

    @Test
    @DisplayName("getVersionHistory: returns 200 with version list")
    fun getVersionHistoryReturns200() {
        val config = buildConfig().copy(
            versions = listOf(
                ConfigurationVersionEntry(
                    version = ConfigurationVersion(1),
                    expressions = listOf(
                        Condition(
                            factName = FactName("keywordMatched"),
                            operator = ComparisonOperator.EQUALS,
                            expectedValue = FactValue.BooleanValue(true)
                        )
                    ),
                    actions = listOf(Action.GENERATE_ALERT),
                    active = true,
                    createdBy = "analyst",
                    createdAt = now
                )
            )
        )
        every { ruleConfigRepository.findById(configId) } returns config

        mockMvc.get("/v1/decision/rule-configurations/$configId/versions") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].version") { value(1) }
            jsonPath("$[0].active") { value(true) }
        }
    }

    @Test
    @DisplayName("getVersionHistory: non-existent config returns 404")
    fun getVersionHistoryReturns404() {
        val id = UUID.randomUUID()
        every { ruleConfigRepository.findById(id) } returns null

        mockMvc.get("/v1/decision/rule-configurations/$id/versions") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    @DisplayName("listByRuleCode: returns empty list when rule definition not found")
    fun listByRuleCodeReturnsEmptyWhenRuleNotFound() {
        every { ruleDefRepository.findByCode(RuleCode("NONEXIST")) } returns null

        mockMvc.get("/v1/decision/rules/NONEXIST/configurations") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    @DisplayName("listByRuleCode: returns configurations for existing rule")
    fun listByRuleCodeReturnsConfigs() {
        val ruleDef = buildRuleDef()
        val config = buildConfig()
        every { ruleDefRepository.findByCode(RuleCode("KEYWORD_ALERT")) } returns ruleDef
        every { ruleConfigRepository.findByRuleId(RuleId(ruleId)) } returns listOf(config)

        mockMvc.get("/v1/decision/rules/KEYWORD_ALERT/configurations") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(configId.toString()) }
            jsonPath("$[0].ruleCode") { value("KEYWORD_ALERT") }
        }
    }

    @Test
    @DisplayName("getById: resolveRuleCode returns UNKNOWN when no definition matches")
    fun getByIdReturnsUnknownRuleCode() {
        val config = buildConfig()
        every { ruleConfigRepository.findById(configId) } returns config
        every { ruleDefRepository.findAll() } returns emptyList()

        mockMvc.get("/v1/decision/rule-configurations/$configId") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.ruleCode") { value("UNKNOWN") }
        }
    }

    @Test
    @DisplayName("create: with Number expectedValue")
    fun createWithNumberExpectedValue() {
        val config = buildConfig().copy(
            expressions = listOf(
                Condition(
                    factName = FactName("amount"),
                    operator = ComparisonOperator.GREATER_THAN,
                    expectedValue = FactValue.NumberValue(java.math.BigDecimal("1000"))
                )
            )
        )
        every { manageUseCase.create(any()) } returns config

        val requestBody = mapOf(
            "expressions" to listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "amount",
                    "operator" to "GREATER_THAN",
                    "expectedValue" to 1000
                )
            ),
            "actions" to listOf("GENERATE_ALERT"),
            "createdBy" to "analyst"
        )

        mockMvc.post("/v1/decision/rules/KEYWORD_ALERT/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    @DisplayName("create: with String expectedValue (becomes EnumValue)")
    fun createWithStringExpectedValue() {
        val config = buildConfig().copy(
            expressions = listOf(
                Condition(
                    factName = FactName("risk"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.EnumValue("HIGH")
                )
            )
        )
        every { manageUseCase.create(any()) } returns config

        val requestBody = mapOf(
            "expressions" to listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "risk",
                    "operator" to "EQUALS",
                    "expectedValue" to "HIGH"
                )
            ),
            "actions" to listOf("GENERATE_ALERT"),
            "createdBy" to "analyst"
        )

        mockMvc.post("/v1/decision/rules/KEYWORD_ALERT/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    @DisplayName("create: with Money expectedValue (map with amount + currency)")
    fun createWithMoneyExpectedValue() {
        val config = buildConfig().copy(
            expressions = listOf(
                Condition(
                    factName = FactName("txAmount"),
                    operator = ComparisonOperator.GREATER_THAN,
                    expectedValue = FactValue.MoneyValue(java.math.BigDecimal("5000"), "BRL")
                )
            )
        )
        every { manageUseCase.create(any()) } returns config

        val requestBody = mapOf(
            "expressions" to listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "txAmount",
                    "operator" to "GREATER_THAN",
                    "expectedValue" to mapOf("amount" to 5000, "currency" to "BRL")
                )
            ),
            "actions" to listOf("GENERATE_ALERT"),
            "createdBy" to "analyst"
        )

        mockMvc.post("/v1/decision/rules/KEYWORD_ALERT/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    @DisplayName("create: with map expectedValue that is not Money (becomes StringValue)")
    fun createWithMapExpectedValueWithoutMoney() {
        val config = buildConfig().copy(
            expressions = listOf(
                Condition(
                    factName = FactName("data"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.StringValue("{key=value}")
                )
            )
        )
        every { manageUseCase.create(any()) } returns config

        val requestBody = mapOf(
            "expressions" to listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "data",
                    "operator" to "EQUALS",
                    "expectedValue" to mapOf("key" to "value")
                )
            ),
            "actions" to listOf("GENERATE_ALERT"),
            "createdBy" to "analyst"
        )

        mockMvc.post("/v1/decision/rules/KEYWORD_ALERT/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    @DisplayName("create: with List-like expectedValue defaults to StringValue via else branch")
    fun createWithUnrecognizedExpectedValue() {
        val config = buildConfig()
        every { manageUseCase.create(any()) } returns config

        // Passing an integer array as expectedValue — triggers else branch → StringValue
        val requestBody = mapOf(
            "expressions" to listOf(
                mapOf(
                    "type" to "CONDITION",
                    "factName" to "keywordMatched",
                    "operator" to "EQUALS",
                    "expectedValue" to listOf(1, 2, 3)
                )
            ),
            "actions" to listOf("GENERATE_ALERT"),
            "createdBy" to "analyst"
        )

        mockMvc.post("/v1/decision/rules/KEYWORD_ALERT/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isCreated() }
        }
    }
}
