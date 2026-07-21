package br.com.screening.infrastructure.input.http

import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningResult
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.infrastructure.input.http.handler.GlobalExceptionHandler
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KeywordScreeningControllerTest {

    private val useCase = mockk<EvaluateKeywordScreeningUseCase>()
    private val controller = KeywordScreeningController(useCase)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    private val objectMapper = jacksonObjectMapper()

    @Test
    @DisplayName("valid request returns 200 with correct body")
    fun validRequestReturns200() {
        val command = EvaluateKeywordScreeningCommand(
            transactionId = TransactionId("TX-001"),
            customerId = CustomerId("CUST-42"),
            description = "pagamento terrorismo",
            correlationId = "correlation-001",
        )
        val useCaseResult = EvaluateKeywordScreeningResult(
            ruleCode = "KEYWORD_SCREENING",
            matched = true,
            matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
        )
        every { useCase.execute(command) } returns useCaseResult

        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "customerId" to "CUST-42",
            "description" to "pagamento terrorismo"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            header("X-Correlation-Id", "correlation-001")
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ruleCode") { value("KEYWORD_SCREENING") }
            jsonPath("$.matched") { value(true) }
            jsonPath("$.matches[0].term") { value("terrorismo") }
            jsonPath("$.matches[0].category") { value("TERRORISM") }
        }
    }

    @Test
    @DisplayName("transactionId absent returns 400")
    fun transactionIdAbsentReturns400() {
        val requestBody = mapOf(
            "customerId" to "CUST-42",
            "description" to "pagamento normal"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("transactionId blank returns 400")
    fun transactionIdBlankReturns400() {
        val requestBody = mapOf(
            "transactionId" to "   ",
            "customerId" to "CUST-42",
            "description" to "pagamento normal"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("description absent returns 400")
    fun descriptionAbsentReturns400() {
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "customerId" to "CUST-42"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("description empty returns 400")
    fun descriptionEmptyReturns400() {
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "customerId" to "CUST-42",
            "description" to ""
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("description exceeding 140 chars returns 400")
    fun descriptionExceeding140CharsReturns400() {
        val longDescription = "a".repeat(141)
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "customerId" to "CUST-42",
            "description" to longDescription
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("customerId absent returns 400")
    fun customerIdAbsentReturns400() {
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "description" to "pagamento normal"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("customerId blank returns 400")
    fun customerIdBlankReturns400() {
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "customerId" to "   ",
            "description" to "pagamento normal"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("customerId exceeding 64 chars returns 400")
    fun customerIdExceeding64CharsReturns400() {
        val longCustomerId = "a".repeat(65)
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "customerId" to longCustomerId,
            "description" to "pagamento normal"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("valid request with no match returns 200 with matched=false")
    fun validRequestNoMatchReturns200() {
        val command = EvaluateKeywordScreeningCommand(
            transactionId = TransactionId("TX-002"),
            customerId = CustomerId("CUST-42"),
            description = "pagamento normal"
        )
        val useCaseResult = EvaluateKeywordScreeningResult(
            ruleCode = "KEYWORD_SCREENING",
            matched = false,
            matches = emptyList()
        )
        every { useCase.execute(command) } returns useCaseResult

        val requestBody = mapOf(
            "transactionId" to "TX-002",
            "customerId" to "CUST-42",
            "description" to "pagamento normal"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.matched") { value(false) }
            jsonPath("$.matches") { isArray() }
            jsonPath("$.matches.length()") { value(0) }
        }
    }

    @Test
    @DisplayName("description with only spaces returns 400 (blank validation)")
    fun descriptionBlankReturns400() {
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "customerId" to "CUST-42",
            "description" to "   "
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("valid request with multiple matches returns all matches")
    fun validRequestMultipleMatches() {
        val command = EvaluateKeywordScreeningCommand(
            transactionId = TransactionId("TX-003"),
            customerId = CustomerId("CUST-42"),
            description = "terrorismo lavagem"
        )
        val useCaseResult = EvaluateKeywordScreeningResult(
            ruleCode = "KEYWORD_SCREENING",
            matched = true,
            matches = listOf(
                MatchResult("terrorismo", Category.TERRORISM),
                MatchResult("lavagem", Category.AML)
            )
        )
        every { useCase.execute(command) } returns useCaseResult

        val requestBody = mapOf(
            "transactionId" to "TX-003",
            "customerId" to "CUST-42",
            "description" to "terrorismo lavagem"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.matched") { value(true) }
            jsonPath("$.matches.length()") { value(2) }
            jsonPath("$.matches[0].category") { value("TERRORISM") }
            jsonPath("$.matches[1].category") { value("AML") }
        }
    }
}
