package br.com.decision.infrastructure.input.http

import br.com.decision.application.usecase.QueryDecisionExecutionUseCase
import br.com.decision.domain.model.*
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.input.http.handler.DecisionExceptionHandler
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@DisplayName("DecisionExecutionController")
class DecisionExecutionControllerTest {

    private val queryUseCase = mockk<QueryDecisionExecutionUseCase>()
    private val controller = DecisionExecutionController(queryUseCase)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(DecisionExceptionHandler())
        .build()
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    private fun buildExecution(id: UUID = UUID.randomUUID()): DecisionExecution {
        val ruleId = UUID.randomUUID()
        return DecisionExecution(
            id = id,
            transactionId = TransactionId("TX-001"),
            ruleId = RuleId(ruleId),
            configurationVersion = ConfigurationVersion(1),
            facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
            result = DecisionResult(
                decision = Decision.ALERT,
                actions = listOf(Action.GENERATE_ALERT),
                matchedExpressions = listOf(
                    ExpressionEvaluation(
                        factName = FactName("keywordMatched"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.BooleanValue(true),
                        actualValue = FactValue.BooleanValue(true),
                        satisfied = true,
                        justification = "Matched"
                    )
                ),
                failedExpressions = emptyList(),
                executionTimeMs = 20,
                configurationVersion = ConfigurationVersion(1),
                facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true))
            ),
            explanation = DecisionExplanation(traceId = TraceId("trace-1"), steps = emptyList()),
            executionTimeMs = 20,
            traceId = TraceId("trace-1"),
            timestamp = Instant.now()
        )
    }

    @Test
    @DisplayName("findById: existing execution returns 200")
    fun findByIdReturns200() {
        val id = UUID.randomUUID()
        val execution = buildExecution(id)
        every { queryUseCase.findById(id) } returns execution

        mockMvc.get("/v1/decision/executions/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(id.toString()) }
            jsonPath("$.decision") { value("ALERT") }
            jsonPath("$.traceId") { value("trace-1") }
        }
    }

    @Test
    @DisplayName("findById: non-existent execution returns 404")
    fun findByIdReturns404() {
        val id = UUID.randomUUID()
        every { queryUseCase.findById(id) } returns null

        mockMvc.get("/v1/decision/executions/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    @DisplayName("search by transactionId returns paginated results")
    fun searchByTransactionId() {
        val execution = buildExecution()
        val page = PageImpl(listOf(execution), PageRequest.of(0, 20), 1)
        every { queryUseCase.findByTransactionId(TransactionId("TX-001"), 0, 20) } returns page

        mockMvc.get("/v1/decision/executions") {
            param("transactionId", "TX-001")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    @Test
    @DisplayName("search by traceId returns single result")
    fun searchByTraceId() {
        val execution = buildExecution()
        every { queryUseCase.findByTraceId(TraceId("trace-1")) } returns execution

        mockMvc.get("/v1/decision/executions") {
            param("traceId", "trace-1")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("ALERT") }
        }
    }

    @Test
    @DisplayName("search without parameters returns 400")
    fun searchWithoutParamsReturns400() {
        mockMvc.get("/v1/decision/executions") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("search by ruleId returns paginated results")
    fun searchByRuleId() {
        val execution = buildExecution()
        val ruleId = execution.ruleId.value
        val page = PageImpl(listOf(execution), PageRequest.of(0, 20), 1)
        every { queryUseCase.findByRuleId(RuleId(ruleId), 0, 20) } returns page

        mockMvc.get("/v1/decision/executions") {
            param("ruleId", ruleId.toString())
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    @Test
    @DisplayName("search by decision returns paginated results")
    fun searchByDecision() {
        val execution = buildExecution()
        val page = PageImpl(listOf(execution), PageRequest.of(0, 20), 1)
        every { queryUseCase.findByDecision(Decision.ALERT, 0, 20) } returns page

        mockMvc.get("/v1/decision/executions") {
            param("decision", "ALERT")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    @Test
    @DisplayName("search by traceId when not found returns empty page")
    fun searchByTraceIdNotFound() {
        every { queryUseCase.findByTraceId(TraceId("nonexistent")) } returns null

        mockMvc.get("/v1/decision/executions") {
            param("traceId", "nonexistent")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.totalElements") { value(0) }
        }
    }

    @Test
    @DisplayName("findById: execution with MoneyValue fact serializes correctly")
    fun findByIdWithMoneyValueFact() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val execution = DecisionExecution(
            id = id,
            transactionId = TransactionId("TX-MONEY"),
            ruleId = RuleId(ruleId),
            configurationVersion = ConfigurationVersion(1),
            facts = mapOf(
                FactName("txAmount") to FactValue.MoneyValue(java.math.BigDecimal("5000.00"), "BRL")
            ),
            result = DecisionResult(
                decision = Decision.ALERT,
                actions = listOf(Action.GENERATE_ALERT),
                matchedExpressions = listOf(
                    ExpressionEvaluation(
                        factName = FactName("txAmount"),
                        operator = ComparisonOperator.GREATER_THAN,
                        expectedValue = FactValue.MoneyValue(java.math.BigDecimal("1000"), "BRL"),
                        actualValue = FactValue.MoneyValue(java.math.BigDecimal("5000.00"), "BRL"),
                        satisfied = true,
                        justification = "Amount exceeds threshold"
                    )
                ),
                failedExpressions = emptyList(),
                executionTimeMs = 10,
                configurationVersion = ConfigurationVersion(1),
                facts = mapOf(FactName("txAmount") to FactValue.MoneyValue(java.math.BigDecimal("5000.00"), "BRL"))
            ),
            explanation = DecisionExplanation(traceId = TraceId("trace-money"), steps = emptyList()),
            executionTimeMs = 10,
            traceId = TraceId("trace-money"),
            timestamp = Instant.now()
        )
        every { queryUseCase.findById(id) } returns execution

        mockMvc.get("/v1/decision/executions/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.facts.txAmount.amount") { exists() }
            jsonPath("$.facts.txAmount.currency") { value("BRL") }
        }
    }

    @Test
    @DisplayName("findById: execution with NumberValue fact serializes correctly")
    fun findByIdWithNumberValueFact() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val execution = DecisionExecution(
            id = id,
            transactionId = TransactionId("TX-NUM"),
            ruleId = RuleId(ruleId),
            configurationVersion = ConfigurationVersion(1),
            facts = mapOf(
                FactName("amount") to FactValue.NumberValue(java.math.BigDecimal("1500"))
            ),
            result = DecisionResult(
                decision = Decision.IGNORE,
                actions = emptyList(),
                matchedExpressions = emptyList(),
                failedExpressions = listOf(
                    ExpressionEvaluation(
                        factName = FactName("amount"),
                        operator = ComparisonOperator.GREATER_THAN,
                        expectedValue = FactValue.NumberValue(java.math.BigDecimal("10000")),
                        actualValue = FactValue.NumberValue(java.math.BigDecimal("1500")),
                        satisfied = false,
                        justification = "Amount below threshold"
                    )
                ),
                executionTimeMs = 5,
                configurationVersion = ConfigurationVersion(1),
                facts = mapOf(FactName("amount") to FactValue.NumberValue(java.math.BigDecimal("1500")))
            ),
            explanation = DecisionExplanation(traceId = TraceId("trace-num"), steps = emptyList()),
            executionTimeMs = 5,
            traceId = TraceId("trace-num"),
            timestamp = Instant.now()
        )
        every { queryUseCase.findById(id) } returns execution

        mockMvc.get("/v1/decision/executions/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.decision") { value("IGNORE") }
            jsonPath("$.failedExpressions[0].factName") { value("amount") }
        }
    }

    @Test
    @DisplayName("findById: execution with StringValue fact serializes correctly")
    fun findByIdWithStringValueFact() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val execution = DecisionExecution(
            id = id,
            transactionId = TransactionId("TX-STR"),
            ruleId = RuleId(ruleId),
            configurationVersion = ConfigurationVersion(1),
            facts = mapOf(
                FactName("description") to FactValue.StringValue("test payment")
            ),
            result = DecisionResult(
                decision = Decision.IGNORE,
                actions = emptyList(),
                matchedExpressions = emptyList(),
                failedExpressions = emptyList(),
                executionTimeMs = 3,
                configurationVersion = ConfigurationVersion(1),
                facts = mapOf(FactName("description") to FactValue.StringValue("test payment"))
            ),
            explanation = DecisionExplanation(traceId = TraceId("trace-str"), steps = emptyList()),
            executionTimeMs = 3,
            traceId = TraceId("trace-str"),
            timestamp = Instant.now()
        )
        every { queryUseCase.findById(id) } returns execution

        mockMvc.get("/v1/decision/executions/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.facts.description") { value("test payment") }
        }
    }

    @Test
    @DisplayName("findById: execution with EnumValue fact serializes correctly")
    fun findByIdWithEnumValueFact() {
        val id = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val execution = DecisionExecution(
            id = id,
            transactionId = TransactionId("TX-ENUM"),
            ruleId = RuleId(ruleId),
            configurationVersion = ConfigurationVersion(1),
            facts = mapOf(
                FactName("risk") to FactValue.EnumValue("HIGH")
            ),
            result = DecisionResult(
                decision = Decision.ALERT,
                actions = listOf(Action.GENERATE_ALERT),
                matchedExpressions = emptyList(),
                failedExpressions = emptyList(),
                executionTimeMs = 2,
                configurationVersion = ConfigurationVersion(1),
                facts = mapOf(FactName("risk") to FactValue.EnumValue("HIGH"))
            ),
            explanation = DecisionExplanation(traceId = TraceId("trace-enum"), steps = emptyList()),
            executionTimeMs = 2,
            traceId = TraceId("trace-enum"),
            timestamp = Instant.now()
        )
        every { queryUseCase.findById(id) } returns execution

        mockMvc.get("/v1/decision/executions/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.facts.risk") { value("HIGH") }
        }
    }

    @Test
    @DisplayName("search by transactionId with custom page and size")
    fun searchByTransactionIdWithCustomPagination() {
        val execution = buildExecution()
        val page = PageImpl(listOf(execution), PageRequest.of(2, 5), 11)
        every { queryUseCase.findByTransactionId(TransactionId("TX-001"), 2, 5) } returns page

        mockMvc.get("/v1/decision/executions") {
            param("transactionId", "TX-001")
            param("page", "2")
            param("size", "5")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(2) }
            jsonPath("$.size") { value(5) }
            jsonPath("$.totalElements") { value(11) }
            jsonPath("$.totalPages") { value(3) }
        }
    }
}
