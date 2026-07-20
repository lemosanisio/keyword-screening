package br.com.decision.infrastructure.input.http.handler

import br.com.decision.application.usecase.ManageRuleConfigurationUseCase
import br.com.decision.domain.exception.DuplicateActiveConfigException
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.exception.RuleConfigurationNotFoundException
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.infrastructure.input.http.RuleConfigurationController
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class DecisionExceptionHandlerTest {

    private val useCase = mockk<ManageRuleConfigurationUseCase>()
    private val ruleConfigRepo = mockk<RuleConfigurationRepository>()
    private val ruleDefRepo = mockk<RuleDefinitionRepository>()
    private val controller = RuleConfigurationController(useCase, ruleConfigRepo, ruleDefRepo)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(DecisionExceptionHandler())
        .build()

    @Test
    @DisplayName("InvalidConfigurationException returns 422 with ErrorResponse")
    fun `InvalidConfigurationException returns 422 with ErrorResponse`() {
        every { useCase.create(any()) } throws InvalidConfigurationException("Fact 'xyz' não existe no catálogo")

        val requestBody = """
            {
                "expressions": [
                    {"type": "CONDITION", "factName": "xyz", "operator": "EQUALS", "expectedValue": true}
                ],
                "actions": ["GENERATE_ALERT"],
                "createdBy": "analyst@company.com"
            }
        """.trimIndent()

        mockMvc.post("/v1/decision/rules/KEYWORD_SCREENING/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.status") { value(422) }
            jsonPath("$.error") { value("Unprocessable Entity") }
            jsonPath("$.message") { value("Fact 'xyz' não existe no catálogo") }
            jsonPath("$.timestamp") { exists() }
            jsonPath("$.details") { value(null) }
        }
    }

    @Test
    @DisplayName("RuleConfigurationNotFoundException returns 404 with ErrorResponse")
    fun `RuleConfigurationNotFoundException returns 404 with ErrorResponse`() {
        val configId = UUID.randomUUID()
        every { useCase.activate(configId) } throws RuleConfigurationNotFoundException(
            "Configuração $configId não encontrada"
        )

        mockMvc.post("/v1/decision/rule-configurations/$configId/activate") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
            jsonPath("$.error") { value("Not Found") }
            jsonPath("$.message") { value("Configuração $configId não encontrada") }
            jsonPath("$.timestamp") { exists() }
            jsonPath("$.details") { value(null) }
        }
    }

    @Test
    @DisplayName("DuplicateActiveConfigException returns 409 with ErrorResponse")
    fun `DuplicateActiveConfigException returns 409 with ErrorResponse`() {
        val configId = UUID.randomUUID()
        every { useCase.activate(configId) } throws DuplicateActiveConfigException(
            "Já existe configuração ativa para esta regra"
        )

        mockMvc.post("/v1/decision/rule-configurations/$configId/activate") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isConflict() }
            jsonPath("$.status") { value(409) }
            jsonPath("$.error") { value("Conflict") }
            jsonPath("$.message") { value("Já existe configuração ativa para esta regra") }
            jsonPath("$.timestamp") { exists() }
            jsonPath("$.details") { value(null) }
        }
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns 400 with details")
    fun `MethodArgumentNotValidException returns 400 with details`() {
        val requestBody = """
            {
                "expressions": [],
                "actions": [],
                "createdBy": ""
            }
        """.trimIndent()

        mockMvc.post("/v1/decision/rules/KEYWORD_SCREENING/configurations") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
            jsonPath("$.error") { value("Bad Request") }
            jsonPath("$.message") { value("Erro de validação nos campos da requisição") }
            jsonPath("$.timestamp") { exists() }
            jsonPath("$.details") { exists() }
        }
    }
}
