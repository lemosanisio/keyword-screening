package br.com.screening.infrastructure.input.http

import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningResult
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.infrastructure.input.http.handler.GlobalExceptionHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KeywordScreeningControllerTest : StringSpec({

    val useCase = mockk<EvaluateKeywordScreeningUseCase>()
    val controller = KeywordScreeningController(useCase)
    val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()
    val objectMapper = jacksonObjectMapper()

    "valid request returns 200 with correct body" {
        val command = EvaluateKeywordScreeningCommand(
            transactionId = "TX-001",
            description = "pagamento terrorismo"
        )
        val useCaseResult = EvaluateKeywordScreeningResult(
            ruleCode = "KEYWORD_SCREENING",
            matched = true,
            matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
        )
        every { useCase.execute(command) } returns useCaseResult

        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "description" to "pagamento terrorismo"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.ruleCode") { value("KEYWORD_SCREENING") }
            jsonPath("$.matched") { value(true) }
            jsonPath("$.matches[0].term") { value("terrorismo") }
            jsonPath("$.matches[0].category") { value("TERRORISM") }
        }
    }

    "transactionId absent returns 400" {
        val requestBody = mapOf(
            "description" to "pagamento normal"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    "transactionId blank returns 400" {
        val requestBody = mapOf(
            "transactionId" to "   ",
            "description" to "pagamento normal"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    "description absent returns 400" {
        val requestBody = mapOf(
            "transactionId" to "TX-001"
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    "description empty returns 400" {
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "description" to ""
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    "description exceeding 140 chars returns 400" {
        val longDescription = "a".repeat(141)
        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "description" to longDescription
        )

        mockMvc.post("/v1/rules/keyword-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }
})
