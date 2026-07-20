package br.com.decision.infrastructure.input.http

import br.com.decision.application.usecase.DryRunResult
import br.com.decision.application.usecase.ExecuteDryRunUseCase
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.enums.*
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.infrastructure.input.http.handler.DecisionExceptionHandler
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.util.UUID

@DisplayName("DryRunController")
class DryRunControllerTest {

    private val executeDryRunUseCase = mockk<ExecuteDryRunUseCase>()
    private val factDefinitionRepository = mockk<FactDefinitionRepository>()
    private val controller = DryRunController(executeDryRunUseCase, factDefinitionRepository)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(DecisionExceptionHandler())
        .build()
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    @Test
    @DisplayName("executeDryRun: valid request returns 200")
    fun executeDryRunValidReturns200() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("keywordMatched"),
            displayName = "Keyword Matched",
            entity = "screening",
            type = FactType.BOOLEAN,
            context = RuleContext.SCREENING,
            source = "keyword-screening",
            supportedOperators = listOf(ComparisonOperator.EQUALS),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("keywordMatched")) } returns factDef

        val dryRunResult = DryRunResult(
            decision = Decision.ALERT,
            actions = listOf(Action.GENERATE_ALERT),
            matchedExpressions = listOf(
                ExpressionEvaluation(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true),
                    actualValue = FactValue.BooleanValue(true),
                    satisfied = true,
                    justification = "Keyword matched"
                )
            ),
            failedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1)
        )
        every { executeDryRunUseCase.execute(any()) } returns dryRunResult

        val requestBody = mapOf("facts" to mapOf("keywordMatched" to true))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("ALERT") }
            jsonPath("$.actions[0]") { value("GENERATE_ALERT") }
            jsonPath("$.configurationVersion") { value(1) }
        }
    }

    @Test
    @DisplayName("executeDryRun: empty facts returns 400")
    fun executeDryRunEmptyFactsReturns400() {
        val configId = UUID.randomUUID()
        val requestBody = mapOf("facts" to emptyMap<String, Any>())

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("executeDryRun: invalid configuration returns 422")
    fun executeDryRunInvalidConfigReturns422() {
        val configId = UUID.randomUUID()
        every { factDefinitionRepository.findByName(any()) } returns null
        every { executeDryRunUseCase.execute(any()) } throws
            InvalidConfigurationException("Fact 'unknown' não encontrado no catálogo")

        val requestBody = mapOf("facts" to mapOf("unknown" to "value"))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.status") { value(422) }
        }
    }

    @Test
    @DisplayName("executeDryRun: coerces ENUM fact correctly")
    fun executeDryRunCoercesEnumFact() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("risk"),
            displayName = "Risk Level",
            entity = "customer",
            type = FactType.ENUM,
            context = RuleContext.CUSTOMER,
            source = "pld",
            supportedOperators = listOf(ComparisonOperator.EQUALS),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("risk")) } returns factDef
        val dryRunResult = DryRunResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1)
        )
        every { executeDryRunUseCase.execute(any()) } returns dryRunResult
        val requestBody = mapOf("facts" to mapOf("risk" to "HIGH"))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("IGNORE") }
        }
    }

    @Test
    @DisplayName("executeDryRun: coerces STRING fact correctly")
    fun executeDryRunCoercesStringFact() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("description"),
            displayName = "Description",
            entity = "transaction",
            type = FactType.STRING,
            context = RuleContext.TRANSACTION,
            source = "core",
            supportedOperators = listOf(ComparisonOperator.EQUALS),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("description")) } returns factDef
        val dryRunResult = DryRunResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1)
        )
        every { executeDryRunUseCase.execute(any()) } returns dryRunResult
        val requestBody = mapOf("facts" to mapOf("description" to "pagamento normal"))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("IGNORE") }
        }
    }

    @Test
    @DisplayName("executeDryRun: coerces MONEY fact correctly")
    fun executeDryRunCoercesMoneyFact() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("txAmount"),
            displayName = "Transaction Amount",
            entity = "transaction",
            type = FactType.MONEY,
            context = RuleContext.TRANSACTION,
            source = "core",
            supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("txAmount")) } returns factDef
        val dryRunResult = DryRunResult(
            decision = Decision.ALERT,
            actions = listOf(Action.GENERATE_ALERT),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1)
        )
        every { executeDryRunUseCase.execute(any()) } returns dryRunResult
        val requestBody = mapOf("facts" to mapOf("txAmount" to mapOf("amount" to 5000.00, "currency" to "BRL")))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("ALERT") }
        }
    }

    @Test
    @DisplayName("executeDryRun: BOOLEAN fact with invalid string returns 422")
    fun executeDryRunBooleanFactInvalidString() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("flag"),
            displayName = "Flag",
            entity = "screening",
            type = FactType.BOOLEAN,
            context = RuleContext.SCREENING,
            source = "system",
            supportedOperators = listOf(ComparisonOperator.EQUALS),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("flag")) } returns factDef
        val requestBody = mapOf("facts" to mapOf("flag" to "not_a_boolean"))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: BOOLEAN fact with valid string 'true'")
    fun executeDryRunBooleanFactValidString() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("flag"),
            displayName = "Flag",
            entity = "screening",
            type = FactType.BOOLEAN,
            context = RuleContext.SCREENING,
            source = "system",
            supportedOperators = listOf(ComparisonOperator.EQUALS),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("flag")) } returns factDef
        val dryRunResult = DryRunResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1)
        )
        every { executeDryRunUseCase.execute(any()) } returns dryRunResult
        // JSON deserializes "true" string — this tests the String branch in coerceToFactValue BOOLEAN
        val requestBody = """{"facts":{"flag":"true"}}"""

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    @DisplayName("executeDryRun: NUMBER fact with invalid string returns 422")
    fun executeDryRunNumberFactInvalidString() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("amount"),
            displayName = "Amount",
            entity = "transaction",
            type = FactType.NUMBER,
            context = RuleContext.TRANSACTION,
            source = "core",
            supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("amount")) } returns factDef
        val requestBody = """{"facts":{"amount":"not_a_number"}}"""

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: NUMBER fact with valid string coercion")
    fun executeDryRunNumberFactValidString() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("amount"),
            displayName = "Amount",
            entity = "transaction",
            type = FactType.NUMBER,
            context = RuleContext.TRANSACTION,
            source = "core",
            supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("amount")) } returns factDef
        val dryRunResult = DryRunResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1)
        )
        every { executeDryRunUseCase.execute(any()) } returns dryRunResult
        val requestBody = """{"facts":{"amount":"3500.50"}}"""

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    @DisplayName("executeDryRun: MONEY fact missing amount returns 422")
    fun executeDryRunMoneyFactMissingAmount() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("txAmount"),
            displayName = "Transaction Amount",
            entity = "transaction",
            type = FactType.MONEY,
            context = RuleContext.TRANSACTION,
            source = "core",
            supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("txAmount")) } returns factDef
        val requestBody = mapOf("facts" to mapOf("txAmount" to mapOf("currency" to "BRL")))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: MONEY fact missing currency returns 422")
    fun executeDryRunMoneyFactMissingCurrency() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("txAmount"),
            displayName = "Transaction Amount",
            entity = "transaction",
            type = FactType.MONEY,
            context = RuleContext.TRANSACTION,
            source = "core",
            supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("txAmount")) } returns factDef
        val requestBody = mapOf("facts" to mapOf("txAmount" to mapOf("amount" to 1000)))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: MONEY fact with non-map value returns 422")
    fun executeDryRunMoneyFactNonMap() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("txAmount"),
            displayName = "Transaction Amount",
            entity = "transaction",
            type = FactType.MONEY,
            context = RuleContext.TRANSACTION,
            source = "core",
            supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("txAmount")) } returns factDef
        val requestBody = """{"facts":{"txAmount":"not_a_map"}}"""

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: unknown fact uses inference (Boolean)")
    fun executeDryRunUnknownFactInfersBoolean() {
        val configId = UUID.randomUUID()
        every { factDefinitionRepository.findByName(FactName("unknownBool")) } returns null
        every { executeDryRunUseCase.execute(any()) } throws
            InvalidConfigurationException("Fact 'unknownBool' não encontrado no catálogo")
        val requestBody = mapOf("facts" to mapOf("unknownBool" to true))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: unknown fact infers Number")
    fun executeDryRunUnknownFactInfersNumber() {
        val configId = UUID.randomUUID()
        every { factDefinitionRepository.findByName(FactName("unknownNum")) } returns null
        every { executeDryRunUseCase.execute(any()) } throws
            InvalidConfigurationException("Fact 'unknownNum' não encontrado no catálogo")
        val requestBody = mapOf("facts" to mapOf("unknownNum" to 42))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: unknown fact infers Money from map with amount and currency")
    fun executeDryRunUnknownFactInfersMoney() {
        val configId = UUID.randomUUID()
        every { factDefinitionRepository.findByName(FactName("unknownMoney")) } returns null
        every { executeDryRunUseCase.execute(any()) } throws
            InvalidConfigurationException("Fact 'unknownMoney' não encontrado no catálogo")
        val requestBody = mapOf("facts" to mapOf("unknownMoney" to mapOf("amount" to 100, "currency" to "USD")))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: unknown fact infers String from map without amount/currency")
    fun executeDryRunUnknownFactInfersStringFromMap() {
        val configId = UUID.randomUUID()
        every { factDefinitionRepository.findByName(FactName("unknownMap")) } returns null
        every { executeDryRunUseCase.execute(any()) } throws
            InvalidConfigurationException("Fact 'unknownMap' não encontrado no catálogo")
        val requestBody = mapOf("facts" to mapOf("unknownMap" to mapOf("key" to "value")))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    @DisplayName("executeDryRun: coerces NUMBER fact correctly")
    fun executeDryRunCoercesNumberFact() {
        val configId = UUID.randomUUID()
        val factDef = FactDefinition(
            id = UUID.randomUUID(),
            name = FactName("amount"),
            displayName = "Amount",
            entity = "transaction",
            type = FactType.NUMBER,
            context = RuleContext.TRANSACTION,
            source = "core",
            supportedOperators = listOf(ComparisonOperator.GREATER_THAN),
            enabled = true
        )
        every { factDefinitionRepository.findByName(FactName("amount")) } returns factDef

        val dryRunResult = DryRunResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1)
        )
        every { executeDryRunUseCase.execute(any()) } returns dryRunResult

        val requestBody = mapOf("facts" to mapOf("amount" to 5000))

        mockMvc.post("/v1/decision/rule-configurations/$configId/dry-run") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("IGNORE") }
        }
    }
}
