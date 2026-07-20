package br.com.alert.infrastructure.input.http

import br.com.alert.application.usecase.QueryAlertUseCase
import br.com.alert.application.usecase.UpdateAlertStatusUseCase
import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.model.vo.AlertId
import br.com.alert.infrastructure.input.http.handler.AlertExceptionHandler
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@DisplayName("AlertController")
class AlertControllerTest {

    private val queryAlertUseCase = mockk<QueryAlertUseCase>()
    private val updateAlertStatusUseCase = mockk<UpdateAlertStatusUseCase>()
    private val controller = AlertController(queryAlertUseCase, updateAlertStatusUseCase)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(AlertExceptionHandler())
        .build()
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    private val alertId = UUID.randomUUID()
    private val ruleId = UUID.randomUUID()
    private val now = Instant.now()

    private fun sampleAlert(status: AlertStatus = AlertStatus.OPEN) = Alert(
        id = AlertId(alertId),
        transactionId = TransactionId("TX-001"),
        ruleId = RuleId(ruleId),
        customerId = CustomerId("CUST-001"),
        facts = mapOf("amount" to 5000),
        configurationVersion = 2,
        traceId = TraceId("trace-1"),
        actions = listOf("GENERATE_ALERT"),
        explanation = mapOf("reason" to "high risk"),
        status = status,
        createdAt = now,
        updatedAt = now
    )

    @Test
    @DisplayName("GET /v1/alerts with transactionId returns alerts list")
    fun searchByTransactionIdReturnsAlerts() {
        every { queryAlertUseCase.findByTransactionId(TransactionId("TX-001")) } returns listOf(sampleAlert())

        mockMvc.get("/v1/alerts") {
            param("transactionId", "TX-001")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].transactionId") { value("TX-001") }
            jsonPath("$[0].status") { value("OPEN") }
            jsonPath("$[0].customerId") { value("CUST-001") }
        }
    }

    @Test
    @DisplayName("GET /v1/alerts with ruleId returns paginated result")
    fun searchByRuleIdReturnsPaginated() {
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(sampleAlert()), pageable, 1)
        every { queryAlertUseCase.findByRuleId(RuleId(ruleId), pageable) } returns page

        mockMvc.get("/v1/alerts") {
            param("ruleId", ruleId.toString())
            param("page", "0")
            param("size", "20")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].status") { value("OPEN") }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.totalPages") { value(1) }
        }
    }

    @Test
    @DisplayName("GET /v1/alerts without params returns 400")
    fun searchWithoutParamsReturns400() {
        mockMvc.get("/v1/alerts").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("GET /v1/alerts/{id} returns alert when found")
    fun findByIdReturns200() {
        every { queryAlertUseCase.findById(AlertId(alertId)) } returns sampleAlert()

        mockMvc.get("/v1/alerts/$alertId").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(alertId.toString()) }
            jsonPath("$.status") { value("OPEN") }
        }
    }

    @Test
    @DisplayName("GET /v1/alerts/{id} returns 404 when not found")
    fun findByIdReturns404() {
        every { queryAlertUseCase.findById(AlertId(alertId)) } returns null

        mockMvc.get("/v1/alerts/$alertId").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    @DisplayName("PATCH /v1/alerts/{id}/status updates alert status")
    fun updateStatusReturns200() {
        val updatedAlert = sampleAlert(AlertStatus.UNDER_REVIEW)
        every { updateAlertStatusUseCase.updateStatus(AlertId(alertId), AlertStatus.UNDER_REVIEW) } returns updatedAlert

        val requestBody = mapOf("status" to "UNDER_REVIEW")

        mockMvc.patch("/v1/alerts/$alertId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UNDER_REVIEW") }
        }
    }

    @Test
    @DisplayName("PATCH /v1/alerts/{id}/status with invalid status returns 400")
    fun updateStatusInvalidReturns400() {
        val requestBody = mapOf("status" to "INVALID_STATUS")

        mockMvc.patch("/v1/alerts/$alertId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("GET /v1/alerts with transactionId returns empty list when no alerts")
    fun searchByTransactionIdReturnsEmptyList() {
        every { queryAlertUseCase.findByTransactionId(TransactionId("TX-NONE")) } returns emptyList()

        mockMvc.get("/v1/alerts") {
            param("transactionId", "TX-NONE")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    @DisplayName("GET /v1/alerts with both transactionId and ruleId prefers transactionId")
    fun searchWithBothParamsUsesTransactionId() {
        every { queryAlertUseCase.findByTransactionId(TransactionId("TX-001")) } returns listOf(sampleAlert())

        mockMvc.get("/v1/alerts") {
            param("transactionId", "TX-001")
            param("ruleId", ruleId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].transactionId") { value("TX-001") }
        }
    }
}
