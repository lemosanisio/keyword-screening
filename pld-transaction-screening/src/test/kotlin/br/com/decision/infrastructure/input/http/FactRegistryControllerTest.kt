package br.com.decision.infrastructure.input.http

import br.com.decision.domain.model.EntityDefinition
import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.port.EntityDefinitionRepository
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.infrastructure.input.http.handler.DecisionExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

@DisplayName("FactRegistryController")
class FactRegistryControllerTest {

    private val factDefinitionRepository = mockk<FactDefinitionRepository>()
    private val entityDefinitionRepository = mockk<EntityDefinitionRepository>()
    private val controller = FactRegistryController(factDefinitionRepository, entityDefinitionRepository)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(DecisionExceptionHandler())
        .build()

    private fun buildFact() = FactDefinition(
        id = UUID.randomUUID(),
        name = FactName("transactionAmount"),
        displayName = "Valor da Transação",
        entity = "transaction",
        type = FactType.NUMBER,
        context = RuleContext.TRANSACTION,
        source = "core-banking",
        supportedOperators = listOf(ComparisonOperator.GREATER_THAN, ComparisonOperator.LESS_THAN),
        enabled = true
    )

    private fun buildEntity() = EntityDefinition(
        id = UUID.randomUUID(),
        name = "transaction",
        displayName = "Transação PIX",
        sourceSystem = "core-banking",
        factNames = listOf(FactName("amount"), FactName("currency"))
    )

    @Test
    @DisplayName("listFacts: returns all facts when no filters")
    fun listFactsReturnsAll() {
        val fact = buildFact()
        every { factDefinitionRepository.findAll() } returns listOf(fact)

        mockMvc.get("/v1/decision/facts") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("transactionAmount") }
            jsonPath("$[0].type") { value("NUMBER") }
            jsonPath("$[0].enabled") { value(true) }
        }
    }

    @Test
    @DisplayName("listFacts: with enabled=true filter")
    fun listFactsWithEnabledFilter() {
        val fact = buildFact()
        every { factDefinitionRepository.findEnabled() } returns listOf(fact)

        mockMvc.get("/v1/decision/facts") {
            param("enabled", "true")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].enabled") { value(true) }
        }
    }

    @Test
    @DisplayName("listFacts: with entity filter")
    fun listFactsWithEntityFilter() {
        val fact = buildFact()
        every { factDefinitionRepository.findAll() } returns listOf(fact)

        mockMvc.get("/v1/decision/facts") {
            param("entity", "transaction")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].entity") { value("transaction") }
        }
    }

    @Test
    @DisplayName("listEntities: returns all entities")
    fun listEntitiesReturnsAll() {
        val entity = buildEntity()
        every { entityDefinitionRepository.findAll() } returns listOf(entity)

        mockMvc.get("/v1/decision/entities") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("transaction") }
            jsonPath("$[0].displayName") { value("Transação PIX") }
            jsonPath("$[0].sourceSystem") { value("core-banking") }
            jsonPath("$[0].factNames[0]") { value("amount") }
        }
    }

    @Test
    @DisplayName("listEntities: empty list returns 200 with empty array")
    fun listEntitiesEmpty() {
        every { entityDefinitionRepository.findAll() } returns emptyList()

        mockMvc.get("/v1/decision/entities") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    @DisplayName("listFacts: with enabled=false filter shows only disabled facts")
    fun listFactsWithEnabledFalseFilter() {
        val enabledFact = buildFact()
        val disabledFact = buildFact().copy(enabled = false, name = FactName("disabledFact"))
        every { factDefinitionRepository.findAll() } returns listOf(enabledFact, disabledFact)

        mockMvc.get("/v1/decision/facts") {
            param("enabled", "false")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].enabled") { value(false) }
        }
    }

    @Test
    @DisplayName("listFacts: with entity filter that doesn't match returns empty list")
    fun listFactsWithEntityFilterNoMatch() {
        val fact = buildFact() // entity = "transaction"
        every { factDefinitionRepository.findAll() } returns listOf(fact)

        mockMvc.get("/v1/decision/facts") {
            param("entity", "customer")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    @DisplayName("listFacts: with entity filter case-insensitive")
    fun listFactsWithEntityFilterCaseInsensitive() {
        val fact = buildFact() // entity = "transaction"
        every { factDefinitionRepository.findAll() } returns listOf(fact)

        mockMvc.get("/v1/decision/facts") {
            param("entity", "TRANSACTION")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].entity") { value("transaction") }
        }
    }

    @Test
    @DisplayName("listFacts: with both entity and enabled=true filters")
    fun listFactsWithEntityAndEnabledFilter() {
        val fact = buildFact()
        every { factDefinitionRepository.findEnabled() } returns listOf(fact)

        mockMvc.get("/v1/decision/facts") {
            param("entity", "transaction")
            param("enabled", "true")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    @DisplayName("listFacts: empty result with enabled=true")
    fun listFactsWithEnabledTrueEmpty() {
        every { factDefinitionRepository.findEnabled() } returns emptyList()

        mockMvc.get("/v1/decision/facts") {
            param("enabled", "true")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }
}
