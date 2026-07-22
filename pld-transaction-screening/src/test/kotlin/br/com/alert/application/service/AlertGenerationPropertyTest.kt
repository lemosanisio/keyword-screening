package br.com.alert.application.service

import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.port.AlertRepository
import br.com.alert.domain.model.vo.AlertId
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Property-Based Tests for Alert Generation Correctness.
 *
 * **Property 5: Alert Generation Correctness**
 * **Validates: Requirements 12.2, 12.3, 12.4, 12.8**
 *
 * Properties verified:
 * 1. When event.actions contains GENERATE_ALERT → alert is created with status OPEN
 * 2. When event.actions does NOT contain GENERATE_ALERT → no alert created (listener logs DEBUG)
 * 3. Idempotency: calling createIfNotExists twice with same (transactionId, ruleId) → same alert returned, no second save
 * 4. Created alert has correct transactionId, ruleId, customerId from the event
 */
class AlertGenerationPropertyTest {

    private val alertRepository = mockk<AlertRepository>()
    private val alertService = AlertService(alertRepository)

    // --- Random Data Generators ---

    private fun randomTransactionId(): TransactionId {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val length = Random.nextInt(1, 65)
        val value = (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return TransactionId(value)
    }

    private fun randomCustomerId(): CustomerId {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val length = Random.nextInt(1, 65)
        val value = (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return CustomerId(value)
    }

    private fun randomTraceId(): TraceId {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val length = Random.nextInt(1, 37)
        val value = (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return TraceId(value)
    }

    private fun randomRuleId(): RuleId = RuleId(UUID.randomUUID())

    private fun randomConfigVersion(): ConfigurationVersion = ConfigurationVersion(Random.nextInt(1, 101))

    private fun randomExecutionTimeMs(): Long = Random.nextLong(1L, 5001L)

    /**
     * Generates a DecisionMadeEvent with GENERATE_ALERT action (simulating an ALERT decision).
     */
    private fun randomDecisionMadeEventWithAlert(): DecisionMadeEvent {
        val traceId = randomTraceId()
        return DecisionMadeEvent(
            eventId = EventId(UUID.randomUUID().toString()),
            traceId = traceId,
            timestamp = Instant.now(),
            transactionId = randomTransactionId(),
            customerId = randomCustomerId(),
            ruleId = randomRuleId(),
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            decision = Decision.ALERT,
            actions = listOf(Action.GENERATE_ALERT),
            facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
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
            configurationVersion = randomConfigVersion(),
            executionTimeMs = randomExecutionTimeMs(),
            explanation = DecisionExplanation(traceId = traceId, steps = emptyList())
        )
    }

    /**
     * Generates a DecisionMadeEvent with IGNORE action (no GENERATE_ALERT).
     */
    private fun randomDecisionMadeEventWithIgnore(): DecisionMadeEvent {
        val traceId = randomTraceId()
        return DecisionMadeEvent(
            eventId = EventId(UUID.randomUUID().toString()),
            traceId = traceId,
            timestamp = Instant.now(),
            transactionId = randomTransactionId(),
            customerId = randomCustomerId(),
            ruleId = randomRuleId(),
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            decision = Decision.IGNORE,
            actions = listOf(Action.IGNORE),
            facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(false)),
            matchedExpressions = emptyList(),
            configurationVersion = randomConfigVersion(),
            executionTimeMs = randomExecutionTimeMs(),
            explanation = DecisionExplanation(traceId = traceId, steps = emptyList())
        )
    }

    // --- Property Tests ---

    @RepeatedTest(200)
    @DisplayName("Property 5a: GENERATE_ALERT in actions creates alert with status OPEN")
    fun `property 5a - GENERATE_ALERT in actions creates alert with status OPEN`() {
        val event = randomDecisionMadeEventWithAlert()
        clearMocks(alertRepository)

        // No existing alert for this (transactionId, ruleId)
        every {
            alertRepository.findByTransactionIdAndRuleId(event.transactionId, event.ruleId)
        } returns null

        // Capture the saved alert
        every { alertRepository.save(any()) } answers { firstArg() }

        val result = alertService.createAlertIfNotExists(event)

        assertNotNull(result)
        assertEquals(AlertStatus.OPEN, result!!.status)

        verify(exactly = 1) { alertRepository.save(any()) }
    }

    @RepeatedTest(200)
    @DisplayName("Property 5b: NO GENERATE_ALERT in actions means no alert created")
    fun `property 5b - NO GENERATE_ALERT in actions means no alert created`() {
        val event = randomDecisionMadeEventWithIgnore()
        clearMocks(alertRepository)

        // DecisionMadeEventListener checks actions before calling service.
        // When GENERATE_ALERT is NOT in actions, the listener does NOT call createAlertIfNotExists.
        // This property verifies the listener behavior.
        val listener = br.com.alert.infrastructure.input.event.DecisionMadeEventListener(alertService, "LEGACY")
        listener.handle(event)

        // Verify: no interaction with the repository (no alert created)
        verify(exactly = 0) { alertRepository.findByTransactionIdAndRuleId(any(), any()) }
        verify(exactly = 0) { alertRepository.save(any()) }
    }

    @RepeatedTest(200)
    @DisplayName("Property 5c: idempotency - second call with same (transactionId, ruleId) returns existing alert without saving")
    fun `property 5c - idempotency - second call with same transactionId and ruleId returns existing alert without saving`() {
        val event = randomDecisionMadeEventWithAlert()
        clearMocks(alertRepository)

        // Simulate existing alert already persisted
        val existingAlert = Alert(
            id = AlertId(UUID.randomUUID()),
            transactionId = event.transactionId,
            ruleId = event.ruleId,
            customerId = event.customerId,
            facts = null,
            configurationVersion = event.configurationVersion.value,
            traceId = event.traceId,
            actions = event.actions.map { it.name },
            explanation = null,
            status = AlertStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        every {
            alertRepository.findByTransactionIdAndRuleId(event.transactionId, event.ruleId)
        } returns existingAlert

        val result = alertService.createAlertIfNotExists(event)

        // Same alert returned
        assertEquals(existingAlert, result)

        // No second save
        verify(exactly = 0) { alertRepository.save(any()) }
    }

    @RepeatedTest(200)
    @DisplayName("Property 5d: created alert has correct transactionId, ruleId, customerId from the event")
    fun `property 5d - created alert has correct transactionId, ruleId, customerId from the event`() {
        val event = randomDecisionMadeEventWithAlert()
        clearMocks(alertRepository)

        every {
            alertRepository.findByTransactionIdAndRuleId(event.transactionId, event.ruleId)
        } returns null

        every { alertRepository.save(any()) } answers { firstArg() }

        val result = alertService.createAlertIfNotExists(event)

        assertNotNull(result)
        assertEquals(event.transactionId, result!!.transactionId)
        assertEquals(event.ruleId, result.ruleId)
        assertEquals(event.customerId, result.customerId)
    }
}
