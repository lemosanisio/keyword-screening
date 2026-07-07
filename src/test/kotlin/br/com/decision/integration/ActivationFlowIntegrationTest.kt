package br.com.decision.integration

import br.com.decision.application.usecase.CreateRuleConfigurationCommand
import br.com.decision.application.usecase.ExecuteDryRunCommand
import br.com.decision.application.usecase.ExecuteDryRunUseCase
import br.com.decision.application.usecase.ManageRuleConfigurationUseCase
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.port.CustomerRiskPort
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Integration test para o fluxo de ativação de configurações de regras.
 * Verifica que:
 * 1. Uma config draft pode ser ativada após dry-run bem-sucedido
 * 2. Tentar ativar sem dry-run prévio é rejeitado com InvalidConfigurationException
 *
 * Validates: Requirements 18.1, 18.2
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivationFlowIntegrationTest {

    @Autowired
    private lateinit var manageRuleConfigurationUseCase: ManageRuleConfigurationUseCase

    @Autowired
    private lateinit var executeDryRunUseCase: ExecuteDryRunUseCase

    @MockBean
    private lateinit var customerRiskPort: CustomerRiskPort

    companion object {
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("keyword_screening_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Test
    @DisplayName("create draft config → execute dry-run → activate succeeds (config active=true, draft=false)")
    fun `create draft config then execute dry-run then activate succeeds`() {
        // Step 1: Create draft configuration
        val createCommand = CreateRuleConfigurationCommand(
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            expressions = listOf(
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
            ),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        val draftConfig = manageRuleConfigurationUseCase.create(createCommand)
        assertTrue(draftConfig.draft)
        assertFalse(draftConfig.active)

        // Step 2: Execute dry-run with valid facts
        val dryRunCommand = ExecuteDryRunCommand(
            configurationId = draftConfig.id,
            facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR")
            ),
            executedBy = "analyst@test.com"
        )

        val dryRunResult = executeDryRunUseCase.execute(dryRunCommand)
        assertEquals(br.com.decision.domain.model.enums.Decision.ALERT, dryRunResult.decision)

        // Step 3: Activate configuration
        val activatedConfig = manageRuleConfigurationUseCase.activate(draftConfig.id)

        assertTrue(activatedConfig.active)
        assertFalse(activatedConfig.draft)
        assertEquals(draftConfig.id, activatedConfig.id)
        assertEquals(draftConfig.currentVersion, activatedConfig.currentVersion)
    }

    @Test
    @DisplayName("create draft config → try to activate WITHOUT prior dry-run → rejected with 422 (InvalidConfigurationException)")
    fun `create draft config then try to activate without prior dry-run is rejected`() {
        // Step 1: Create draft configuration
        val createCommand = CreateRuleConfigurationCommand(
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            expressions = listOf(
                Condition(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true)
                )
            ),
            actions = listOf(Action.GENERATE_ALERT),
            createdBy = "analyst@test.com"
        )

        val draftConfig = manageRuleConfigurationUseCase.create(createCommand)
        assertTrue(draftConfig.draft)
        assertFalse(draftConfig.active)

        // Step 2: Try to activate without dry-run → should throw InvalidConfigurationException
        val exception = assertThrows<InvalidConfigurationException> {
            manageRuleConfigurationUseCase.activate(draftConfig.id)
        }

        assertEquals("Dry-run obrigatório antes da ativação", exception.message)
    }
}
