package br.com.decision.application.service

import br.com.decision.application.usecase.ExecuteDecisionCommand
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ConfigurationVersionEntry
import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.DecisionResult
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.port.DecisionExecutionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.service.DecisionEngine
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.DomainEvent
import br.com.shared.domain.DomainEventPublisher
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.QueryTimeoutException
import java.time.Instant
import java.util.UUID
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import br.com.evaluation.infrastructure.CanonicalSnapshot
import br.com.evaluation.infrastructure.SnapshotCanonicalizer
import br.com.evaluation.infrastructure.TransactionEvaluationRepository
import br.com.evaluation.infrastructure.TransactionIdentityResolver
import br.com.evaluation.infrastructure.TransactionEvaluationLock

@DisplayName("DecisionService — Unit Tests")
class DecisionServiceTest {

    private val decisionEngine = mockk<DecisionEngine>()
    private val decisionExecutionRepository = mockk<DecisionExecutionRepository>()
    private val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()
    private val ruleConfigurationRepository = mockk<RuleConfigurationRepository>()
    private val domainEventPublisher = mockk<DomainEventPublisher>()
    private val transactionIdentityResolver = mockk<TransactionIdentityResolver>()
    private val snapshotCanonicalizer = mockk<SnapshotCanonicalizer>()
    private val transactionEvaluationRepository = mockk<TransactionEvaluationRepository>(relaxed = true)
    private val transactionEvaluationLock = mockk<TransactionEvaluationLock>(relaxed = true)

    init {
        every { transactionIdentityResolver.resolve(any(), any()) } answers { secondArg() }
        every { snapshotCanonicalizer.canonicalize(any()) } returns CanonicalSnapshot("{}", "0".repeat(64))
        every { transactionEvaluationRepository.findDecisionExecutionId(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns null
    }

    private val decisionService = DecisionService(
        decisionEngine = decisionEngine,
        decisionExecutionRepository = decisionExecutionRepository,
        ruleDefinitionRepository = ruleDefinitionRepository,
        ruleConfigurationRepository = ruleConfigurationRepository,
        domainEventPublisher = domainEventPublisher,
        transactionIdentityResolver = transactionIdentityResolver,
        snapshotCanonicalizer = snapshotCanonicalizer,
        transactionEvaluationRepository = transactionEvaluationRepository,
        transactionEvaluationLock = transactionEvaluationLock,
    )

    private val ruleId = RuleId(UUID.randomUUID())
    private val ruleCode = RuleCode("KEYWORD_SCREENING")

    private val ruleDefinition = RuleDefinition(
        id = ruleId,
        code = ruleCode,
        name = "Keyword Screening",
        description = "Detecção de keywords suspeitas",
        context = RuleContext.SCREENING,
        category = RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("keywordMatched"), FactName("customerRisk")),
        supportedActions = listOf(Action.GENERATE_ALERT, Action.IGNORE),
        status = RuleStatus.ACTIVE,
        createdAt = Instant.now()
    )

    private val activeConfig = RuleConfiguration(
        id = UUID.randomUUID(),
        ruleId = ruleId,
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
                createdBy = "analyst@company.com",
                createdAt = Instant.now()
            )
        ),
        createdBy = "analyst@company.com",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun buildCommand(
        transactionId: TransactionId = TransactionId("TX-001"),
        customerId: CustomerId = CustomerId("CUST-42"),
        ruleCode: RuleCode = RuleCode("KEYWORD_SCREENING"),
        matched: Boolean = true
    ) = ExecuteDecisionCommand(
        transactionId = transactionId,
        customerId = customerId,
        ruleCode = ruleCode,
        detectionResult = DetectionResult(
            matched = matched,
            matches = if (matched) listOf(DetectionMatch("lavagem", "AML")) else emptyList()
        )
    )

    private fun buildDecisionResult(
        decision: Decision = Decision.ALERT,
        actions: List<Action> = listOf(Action.GENERATE_ALERT)
    ) = DecisionResult(
        decision = decision,
        actions = actions,
        matchedExpressions = listOf(
            ExpressionEvaluation(
                factName = FactName("keywordMatched"),
                operator = ComparisonOperator.EQUALS,
                expectedValue = FactValue.BooleanValue(true),
                actualValue = FactValue.BooleanValue(true),
                satisfied = true,
                justification = "keywordMatched EQUALS true → satisfied"
            )
        ),
        failedExpressions = emptyList(),
        executionTimeMs = 15L,
        configurationVersion = ConfigurationVersion(1),
        facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
        explanation = DecisionExplanation(traceId = TraceId("trace-123"), steps = emptyList())
    )

    @Nested
    @DisplayName("RuleDefinition not found")
    inner class RuleDefinitionNotFound {

        @Test
        fun `returns IGNORE when RuleDefinition is not found`() {
            every { ruleDefinitionRepository.findByCode(RuleCode("UNKNOWN_RULE")) } returns null

            val command = buildCommand(ruleCode = RuleCode("UNKNOWN_RULE"))
            val result = decisionService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()

            verify(exactly = 0) { decisionExecutionRepository.findByTransactionIdAndRuleId(any(), any()) }
            verify(exactly = 0) { decisionEngine.evaluate(any(), any(), any()) }
            verify(exactly = 0) { domainEventPublisher.publish(any()) }
        }
    }

    @Nested
    @DisplayName("Idempotency — existing execution")
    inner class Idempotency {

        @Test
        fun `returns existing result without re-execution when execution already exists`() {
            val existingResult = buildDecisionResult()
            val existingExecution = DecisionExecution(
                id = UUID.randomUUID(), transactionId = TransactionId("TX-001"),
                ruleId = ruleId,
                configurationVersion = ConfigurationVersion(1),
                facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
                result = existingResult,
                explanation = DecisionExplanation(traceId = TraceId("trace-existing"), steps = emptyList()),
                executionTimeMs = 12L,
                traceId = TraceId("trace-existing"),
                timestamp = Instant.now()
            )

            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns existingExecution

            val command = buildCommand()
            val result = decisionService.execute(command)

            assertThat(result).isEqualTo(existingResult)

            // No re-execution, no new persistence, no new event
            verify(exactly = 0) { decisionEngine.evaluate(any(), any(), any()) }
            verify(exactly = 0) { decisionExecutionRepository.save(any()) }
            verify(exactly = 0) { domainEventPublisher.publish(any()) }
        }
    }

    @Nested
    @DisplayName("No active configuration")
    inner class NoActiveConfiguration {

        @Test
        fun `returns IGNORE and persists execution when no active config exists`() {
            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns null
            every { decisionExecutionRepository.save(any()) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just Runs

            val command = buildCommand()
            val result = decisionService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()

            // Should persist the IGNORE execution
            verify(exactly = 1) { decisionExecutionRepository.save(any()) }
            // Should NOT invoke DecisionEngine
            verify(exactly = 0) { decisionEngine.evaluate(any(), any(), any()) }
            // Completion is published even when no configuration can raise a signal.
            verify(exactly = 1) { domainEventPublisher.publish(any()) }
        }
    }

    @Nested
    @DisplayName("Full decision flow — ALERT")
    inner class FullDecisionFlowAlert {

        @Test
        fun `executes full flow and returns ALERT when conditions satisfied`() {
            val decisionResult = buildDecisionResult(Decision.ALERT, listOf(Action.GENERATE_ALERT))
            val eventSlot = slot<DomainEvent>()

            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig
            every { decisionEngine.evaluate(any(), eq(activeConfig), any()) } returns decisionResult
            every { decisionExecutionRepository.save(any()) } answers { firstArg() }
            every { domainEventPublisher.publish(capture(eventSlot)) } just Runs

            val command = buildCommand()
            val result = decisionService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.ALERT)
            assertThat(result.actions).containsExactly(Action.GENERATE_ALERT)

            // Verify persistence
            verify(exactly = 1) { decisionExecutionRepository.save(any()) }

            // Verify event publication
            verify(exactly = 1) { domainEventPublisher.publish(any()) }
            val publishedEvent = eventSlot.captured as DecisionMadeEvent
            assertThat(publishedEvent.transactionId).isEqualTo(TransactionId("TX-001"))
            assertThat(publishedEvent.customerId).isEqualTo(CustomerId("CUST-42"))
            assertThat(publishedEvent.ruleId).isEqualTo(ruleId)
            assertThat(publishedEvent.ruleCode).isEqualTo(ruleCode)
            assertThat(publishedEvent.decision).isEqualTo(Decision.ALERT)
            assertThat(publishedEvent.actions).containsExactly(Action.GENERATE_ALERT)
        }

        @Test
        fun `persists DecisionExecution with correct data`() {
            val decisionResult = buildDecisionResult()
            val executionSlot = slot<DecisionExecution>()

            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig
            every { decisionEngine.evaluate(any(), eq(activeConfig), any()) } returns decisionResult
            every { decisionExecutionRepository.save(capture(executionSlot)) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just Runs

            val command = buildCommand()
            decisionService.execute(command)

            val savedExecution = executionSlot.captured
            assertThat(savedExecution.transactionId).isEqualTo(TransactionId("TX-001"))
            assertThat(savedExecution.ruleId).isEqualTo(ruleId)
            assertThat(savedExecution.configurationVersion).isEqualTo(ConfigurationVersion(1))
            assertThat(savedExecution.result).isEqualTo(decisionResult)
            assertThat(savedExecution.traceId.value).isNotBlank()
            assertThat(savedExecution.id).isNotNull()
            assertThat(savedExecution.timestamp).isNotNull()
        }
    }

    @Nested
    @DisplayName("Full decision flow — IGNORE")
    inner class FullDecisionFlowIgnore {

        @Test
        fun `executes full flow and returns IGNORE when conditions not satisfied`() {
            val decisionResult = buildDecisionResult(Decision.IGNORE, emptyList()).copy(
                matchedExpressions = emptyList(),
                failedExpressions = listOf(
                    ExpressionEvaluation(
                        factName = FactName("keywordMatched"),
                        operator = ComparisonOperator.EQUALS,
                        expectedValue = FactValue.BooleanValue(true),
                        actualValue = FactValue.BooleanValue(false),
                        satisfied = false,
                        justification = "keywordMatched EQUALS true → NOT satisfied"
                    )
                )
            )

            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig
            every { decisionEngine.evaluate(any(), eq(activeConfig), any()) } returns decisionResult
            every { decisionExecutionRepository.save(any()) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just Runs

            val command = buildCommand(matched = false)
            val result = decisionService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            assertThat(result.actions).isEmpty()

            // IGNORE decisions are also persisted and event published
            verify(exactly = 1) { decisionExecutionRepository.save(any()) }
            verify(exactly = 1) { domainEventPublisher.publish(any()) }
        }
    }

    @Nested
    @DisplayName("Retry for persistence failures")
    inner class PersistenceRetry {

        @Test
        fun `retries and succeeds on second attempt`() {
            val decisionResult = buildDecisionResult()

            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig
            every { decisionEngine.evaluate(any(), eq(activeConfig), any()) } returns decisionResult
            every { decisionExecutionRepository.save(any()) } throws
                QueryTimeoutException("timeout") andThen { it.invocation.args[0] as DecisionExecution }
            every { domainEventPublisher.publish(any()) } just Runs

            val command = buildCommand()
            val result = decisionService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.ALERT)
            verify(exactly = 2) { decisionExecutionRepository.save(any()) }
        }

        @Test
        fun `throws after 3 failed persistence attempts`() {
            val decisionResult = buildDecisionResult()

            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig
            every { decisionEngine.evaluate(any(), eq(activeConfig), any()) } returns decisionResult
            every { decisionExecutionRepository.save(any()) } throws QueryTimeoutException("timeout")

            val command = buildCommand()

            assertThatThrownBy { decisionService.execute(command) }
                .isInstanceOf(DataAccessException::class.java)

            verify(exactly = 3) { decisionExecutionRepository.save(any()) }
        }

        @Test
        fun `retries for no-active-config IGNORE persistence too`() {
            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns null
            every { decisionExecutionRepository.save(any()) } throws
                QueryTimeoutException("timeout") andThenThrows
                QueryTimeoutException("timeout") andThen { it.invocation.args[0] as DecisionExecution }
            every { domainEventPublisher.publish(any()) } just Runs

            val command = buildCommand()
            val result = decisionService.execute(command)

            assertThat(result.decision).isEqualTo(Decision.IGNORE)
            verify(exactly = 3) { decisionExecutionRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("DetectionEvent construction")
    inner class DetectionEventConstruction {

        @Test
        fun `passes correct DetectionEvent to DecisionEngine`() {
            val decisionResult = buildDecisionResult()
            val eventSlot = slot<DetectionEvent>()

            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every {
                decisionExecutionRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), ruleId)
            } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig
            every { decisionEngine.evaluate(capture(eventSlot), eq(activeConfig), any()) } returns decisionResult
            every { decisionExecutionRepository.save(any()) } answers { firstArg() }
            every { domainEventPublisher.publish(any()) } just Runs

            val command = buildCommand(transactionId = TransactionId("TX-001"), customerId = CustomerId("CUST-42"), ruleCode = RuleCode("KEYWORD_SCREENING"),
                matched = true
            )
            decisionService.execute(command)

            val capturedEvent = eventSlot.captured
            assertThat(capturedEvent.transactionId).isEqualTo(TransactionId("TX-001"))
            assertThat(capturedEvent.customerId).isEqualTo(CustomerId("CUST-42"))
            assertThat(capturedEvent.ruleCode).isEqualTo(RuleCode("KEYWORD_SCREENING"))
            assertThat(capturedEvent.detectionResult.matched).isTrue()
            assertThat(capturedEvent.detectionResult.matches).hasSize(1)
            assertThat(capturedEvent.detectionResult.matches[0].term).isEqualTo("lavagem")
            assertThat(capturedEvent.traceId.value).isNotBlank()
        }
    }

    @Nested
    @DisplayName("TraceId generation")
    inner class TraceIdGeneration {

        @Test
        fun `generates unique traceId for each execution`() {
            val decisionResult = buildDecisionResult()
            val traceIds = mutableListOf<String>()

            every { ruleDefinitionRepository.findByCode(ruleCode) } returns ruleDefinition
            every { decisionExecutionRepository.findByTransactionIdAndRuleId(any(), any()) } returns null
            every { ruleConfigurationRepository.findActiveByRuleId(ruleId) } returns activeConfig
            every { decisionEngine.evaluate(any(), eq(activeConfig), any()) } returns decisionResult
            every { decisionExecutionRepository.save(any()) } answers {
                val exec = firstArg<DecisionExecution>()
                traceIds.add(exec.traceId.value)
                exec
            }
            every { domainEventPublisher.publish(any()) } just Runs

            decisionService.execute(buildCommand(transactionId = TransactionId("TX-001")))
            decisionService.execute(buildCommand(transactionId = TransactionId("TX-002")))

            assertThat(traceIds).hasSize(2)
            assertThat(traceIds[0]).isNotEqualTo(traceIds[1])
        }
    }
}
