package br.com.decision.infrastructure.input.http

import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.infrastructure.input.http.handler.DecisionExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@DisplayName("RuleCatalogController")
class RuleCatalogControllerTest {

    private val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()
    private val controller = RuleCatalogController(ruleDefinitionRepository)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(DecisionExceptionHandler())
        .build()

    private fun buildRule() = RuleDefinition(
        id = RuleId(UUID.randomUUID()),
        code = RuleCode("KEYWORD_ALERT"),
        name = "Keyword Alert",
        description = "Alert on keyword match",
        context = RuleContext.SCREENING,
        category = RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("keywordMatched")),
        supportedActions = listOf(Action.GENERATE_ALERT),
        status = RuleStatus.ACTIVE,
        createdAt = Instant.now()
    )

    @Test
    @DisplayName("listRules: returns all rules")
    fun listRulesReturnsAll() {
        val rule = buildRule()
        every { ruleDefinitionRepository.findByContextAndCategory(null, null) } returns listOf(rule)

        mockMvc.get("/v1/decision/rules") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].code") { value("KEYWORD_ALERT") }
            jsonPath("$[0].name") { value("Keyword Alert") }
            jsonPath("$[0].context") { value("SCREENING") }
            jsonPath("$[0].status") { value("ACTIVE") }
        }
    }

    @Test
    @DisplayName("listRules: with context filter")
    fun listRulesWithContextFilter() {
        val rule = buildRule()
        every {
            ruleDefinitionRepository.findByContextAndCategory(RuleContext.SCREENING, null)
        } returns listOf(rule)

        mockMvc.get("/v1/decision/rules") {
            param("context", "SCREENING")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].context") { value("SCREENING") }
        }
    }

    @Test
    @DisplayName("getRuleByCode: existing rule returns 200")
    fun getRuleByCodeReturns200() {
        val rule = buildRule()
        every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_ALERT")) } returns rule

        mockMvc.get("/v1/decision/rules/KEYWORD_ALERT") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("KEYWORD_ALERT") }
        }
    }

    @Test
    @DisplayName("getRuleByCode: non-existent rule returns 404")
    fun getRuleByCodeReturns404() {
        every { ruleDefinitionRepository.findByCode(RuleCode("NONEXIST")) } returns null

        mockMvc.get("/v1/decision/rules/NONEXIST") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    @DisplayName("listRules: with category filter")
    fun listRulesWithCategoryFilter() {
        val rule = buildRule()
        every {
            ruleDefinitionRepository.findByContextAndCategory(null, RuleCategory.KEYWORD_SCREENING)
        } returns listOf(rule)

        mockMvc.get("/v1/decision/rules") {
            param("category", "KEYWORD_SCREENING")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].category") { value("KEYWORD_SCREENING") }
        }
    }

    @Test
    @DisplayName("listRules: with both context and category filters")
    fun listRulesWithBothFilters() {
        val rule = buildRule()
        every {
            ruleDefinitionRepository.findByContextAndCategory(RuleContext.SCREENING, RuleCategory.KEYWORD_SCREENING)
        } returns listOf(rule)

        mockMvc.get("/v1/decision/rules") {
            param("context", "SCREENING")
            param("category", "KEYWORD_SCREENING")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].context") { value("SCREENING") }
            jsonPath("$[0].category") { value("KEYWORD_SCREENING") }
        }
    }

    @Test
    @DisplayName("listRules: empty result returns 200 with empty array")
    fun listRulesEmpty() {
        every { ruleDefinitionRepository.findByContextAndCategory(null, null) } returns emptyList()

        mockMvc.get("/v1/decision/rules") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }
}
