package br.com.alert.application.service

import br.com.alert.application.usecase.CreateAlertCommand
import br.com.alert.domain.exception.AlertNotFoundException
import br.com.alert.domain.exception.InvalidAlertTransitionException
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
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId

class AlertServiceTest {

    private val alertRepository = mockk<AlertRepository>(relaxed = false)
    private val alertService = AlertService(alertRepository)

    @BeforeEach
    fun setUp() {
        clearMocks(alertRepository)
    }

    // --- createIfNotExists ---

    @Test
    @DisplayName("createIfNotExists returns existing alert when already exists (idempotency)")
    fun `createIfNotExists returns existing alert when already exists`() {
        val transactionId = TransactionId("TX-001")
        val ruleId = RuleId(UUID.randomUUID())
        val existingAlert = Alert(
            id = AlertId(UUID.randomUUID()),
            transactionId = transactionId,
            ruleId = ruleId, customerId = CustomerId("CUST-42"),
            facts = mapOf("keywordMatched" to true),
            configurationVersion = 1,
            traceId = TraceId("trace-1"),
            actions = listOf("GENERATE_ALERT"),
            explanation = null,
            status = AlertStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        every { alertRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns existingAlert

        val command = CreateAlertCommand(
            transactionId = transactionId,
            ruleId = ruleId, customerId = CustomerId("CUST-42"),
            facts = mapOf("keywordMatched" to true),
            configurationVersion = 1,
            traceId = TraceId("trace-1"),
            actions = listOf("GENERATE_ALERT"),
            explanation = null
        )

        val result = alertService.createIfNotExists(command)

        assertEquals(existingAlert, result)
        verify(exactly = 0) { alertRepository.save(any()) }
    }

    @Test
    @DisplayName("createIfNotExists creates new alert when none exists")
    fun `createIfNotExists creates new alert when none exists`() {
        val transactionId = TransactionId("TX-002")
        val ruleId = RuleId(UUID.randomUUID())
        val alertSlot = slot<Alert>()

        every { alertRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns null
        every { alertRepository.save(capture(alertSlot)) } answers { alertSlot.captured }

        val command = CreateAlertCommand(
            transactionId = transactionId,
            ruleId = ruleId, customerId = CustomerId("CUST-99"),
            facts = mapOf("keywordMatched" to true, "customerRisk" to "AR"),
            configurationVersion = 3,
            traceId = TraceId("trace-2"),
            actions = listOf("GENERATE_ALERT"),
            explanation = mapOf("decision" to "ALERT")
        )

        val result = alertService.createIfNotExists(command)

        assertEquals(transactionId, result.transactionId)
        assertEquals(ruleId, result.ruleId)
        assertEquals(CustomerId("CUST-99"), result.customerId)
        assertEquals(mapOf("keywordMatched" to true, "customerRisk" to "AR"), result.facts)
        assertEquals(3, result.configurationVersion)
        assertEquals(TraceId("trace-2"), result.traceId)
        assertEquals(listOf("GENERATE_ALERT"), result.actions)
        assertEquals(mapOf("decision" to "ALERT"), result.explanation)
        assertEquals(AlertStatus.OPEN, result.status)
        verify(exactly = 1) { alertRepository.save(any()) }
    }

    @Test
    @DisplayName("createIfNotExists creates alert with null optional fields")
    fun `createIfNotExists creates alert with null optional fields`() {
        val transactionId = TransactionId("TX-003")
        val ruleId = RuleId(UUID.randomUUID())
        val alertSlot = slot<Alert>()

        every { alertRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns null
        every { alertRepository.save(capture(alertSlot)) } answers { alertSlot.captured }

        val command = CreateAlertCommand(
            transactionId = transactionId,
            ruleId = ruleId, customerId = CustomerId("CUST-01"),
            facts = null,
            configurationVersion = null,
            traceId = null,
            actions = null,
            explanation = null
        )

        val result = alertService.createIfNotExists(command)

        assertNull(result.facts)
        assertNull(result.configurationVersion)
        assertNull(result.traceId)
        assertNull(result.actions)
        assertNull(result.explanation)
        assertEquals(AlertStatus.OPEN, result.status)
    }

    // --- updateStatus ---

    @Test
    @DisplayName("updateStatus transitions OPEN to UNDER_REVIEW successfully")
    fun `updateStatus transitions OPEN to UNDER_REVIEW successfully`() {
        val alertId = AlertId(UUID.randomUUID())
        val existingAlert = Alert(
            id = alertId, transactionId = TransactionId("TX-001"),
            ruleId = RuleId(UUID.randomUUID()), customerId = CustomerId("CUST-42"),
            facts = null,
            configurationVersion = 1,
            traceId = null,
            actions = null,
            explanation = null,
            status = AlertStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val alertSlot = slot<Alert>()

        every { alertRepository.findById(alertId) } returns existingAlert
        every { alertRepository.save(capture(alertSlot)) } answers { alertSlot.captured }

        val result = alertService.updateStatus(alertId, AlertStatus.UNDER_REVIEW)

        assertEquals(AlertStatus.UNDER_REVIEW, result.status)
        verify(exactly = 1) { alertRepository.save(any()) }
    }

    @Test
    @DisplayName("updateStatus transitions UNDER_REVIEW to CLOSED successfully")
    fun `updateStatus transitions UNDER_REVIEW to CLOSED successfully`() {
        val alertId = AlertId(UUID.randomUUID())
        val existingAlert = Alert(
            id = alertId, transactionId = TransactionId("TX-001"),
            ruleId = RuleId(UUID.randomUUID()), customerId = CustomerId("CUST-42"),
            facts = null,
            configurationVersion = 1,
            traceId = null,
            actions = null,
            explanation = null,
            status = AlertStatus.UNDER_REVIEW,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val alertSlot = slot<Alert>()

        every { alertRepository.findById(alertId) } returns existingAlert
        every { alertRepository.save(capture(alertSlot)) } answers { alertSlot.captured }

        val result = alertService.updateStatus(alertId, AlertStatus.CLOSED)

        assertEquals(AlertStatus.CLOSED, result.status)
    }

    @Test
    @DisplayName("updateStatus transitions UNDER_REVIEW to FALSE_POSITIVE successfully")
    fun `updateStatus transitions UNDER_REVIEW to FALSE_POSITIVE successfully`() {
        val alertId = AlertId(UUID.randomUUID())
        val existingAlert = Alert(
            id = alertId, transactionId = TransactionId("TX-001"),
            ruleId = RuleId(UUID.randomUUID()), customerId = CustomerId("CUST-42"),
            facts = null,
            configurationVersion = 1,
            traceId = null,
            actions = null,
            explanation = null,
            status = AlertStatus.UNDER_REVIEW,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val alertSlot = slot<Alert>()

        every { alertRepository.findById(alertId) } returns existingAlert
        every { alertRepository.save(capture(alertSlot)) } answers { alertSlot.captured }

        val result = alertService.updateStatus(alertId, AlertStatus.FALSE_POSITIVE)

        assertEquals(AlertStatus.FALSE_POSITIVE, result.status)
    }

    @Test
    @DisplayName("updateStatus throws AlertNotFoundException when alert does not exist")
    fun `updateStatus throws AlertNotFoundException when alert does not exist`() {
        val alertId = AlertId(UUID.randomUUID())
        every { alertRepository.findById(alertId) } returns null

        assertThrows(AlertNotFoundException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.UNDER_REVIEW)
        }
    }

    @Test
    @DisplayName("updateStatus throws InvalidAlertTransitionException for invalid transition OPEN to CLOSED")
    fun `updateStatus throws InvalidAlertTransitionException for invalid transition OPEN to CLOSED`() {
        val alertId = AlertId(UUID.randomUUID())
        val existingAlert = Alert(
            id = alertId, transactionId = TransactionId("TX-001"),
            ruleId = RuleId(UUID.randomUUID()), customerId = CustomerId("CUST-42"),
            facts = null,
            configurationVersion = 1,
            traceId = null,
            actions = null,
            explanation = null,
            status = AlertStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        every { alertRepository.findById(alertId) } returns existingAlert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.CLOSED)
        }
    }

    @Test
    @DisplayName("updateStatus throws InvalidAlertTransitionException for terminal state CLOSED")
    fun `updateStatus throws InvalidAlertTransitionException for terminal state CLOSED`() {
        val alertId = AlertId(UUID.randomUUID())
        val existingAlert = Alert(
            id = alertId, transactionId = TransactionId("TX-001"),
            ruleId = RuleId(UUID.randomUUID()), customerId = CustomerId("CUST-42"),
            facts = null,
            configurationVersion = 1,
            traceId = null,
            actions = null,
            explanation = null,
            status = AlertStatus.CLOSED,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        every { alertRepository.findById(alertId) } returns existingAlert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.OPEN)
        }
    }

    @Test
    @DisplayName("updateStatus throws InvalidAlertTransitionException for terminal state FALSE_POSITIVE")
    fun `updateStatus throws InvalidAlertTransitionException for terminal state FALSE_POSITIVE`() {
        val alertId = AlertId(UUID.randomUUID())
        val existingAlert = Alert(
            id = alertId, transactionId = TransactionId("TX-001"),
            ruleId = RuleId(UUID.randomUUID()), customerId = CustomerId("CUST-42"),
            facts = null,
            configurationVersion = 1,
            traceId = null,
            actions = null,
            explanation = null,
            status = AlertStatus.FALSE_POSITIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        every { alertRepository.findById(alertId) } returns existingAlert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.UNDER_REVIEW)
        }
    }


    // --- Alert.transitionTo() branch coverage ---

    @Test
    @DisplayName("Alert.transitionTo OPEN -> UNDER_REVIEW succeeds")
    fun `transitionTo OPEN to UNDER_REVIEW succeeds`() {
        val alert = buildAlert(AlertStatus.OPEN)
        val result = alert.transitionTo(AlertStatus.UNDER_REVIEW)
        assertEquals(AlertStatus.UNDER_REVIEW, result.status)
    }

    @Test
    @DisplayName("Alert.transitionTo OPEN -> CLOSED throws")
    fun `transitionTo OPEN to CLOSED throws`() {
        val alert = buildAlert(AlertStatus.OPEN)
        assertThrows(IllegalArgumentException::class.java) {
            alert.transitionTo(AlertStatus.CLOSED)
        }
    }

    @Test
    @DisplayName("Alert.transitionTo OPEN -> FALSE_POSITIVE throws")
    fun `transitionTo OPEN to FALSE_POSITIVE throws`() {
        val alert = buildAlert(AlertStatus.OPEN)
        assertThrows(IllegalArgumentException::class.java) {
            alert.transitionTo(AlertStatus.FALSE_POSITIVE)
        }
    }

    @Test
    @DisplayName("Alert.transitionTo OPEN -> OPEN throws")
    fun `transitionTo OPEN to OPEN throws`() {
        val alert = buildAlert(AlertStatus.OPEN)
        assertThrows(IllegalArgumentException::class.java) {
            alert.transitionTo(AlertStatus.OPEN)
        }
    }

    @Test
    @DisplayName("Alert.transitionTo UNDER_REVIEW -> CLOSED succeeds")
    fun `transitionTo UNDER_REVIEW to CLOSED succeeds`() {
        val alert = buildAlert(AlertStatus.UNDER_REVIEW)
        val result = alert.transitionTo(AlertStatus.CLOSED)
        assertEquals(AlertStatus.CLOSED, result.status)
    }

    @Test
    @DisplayName("Alert.transitionTo UNDER_REVIEW -> FALSE_POSITIVE succeeds")
    fun `transitionTo UNDER_REVIEW to FALSE_POSITIVE succeeds`() {
        val alert = buildAlert(AlertStatus.UNDER_REVIEW)
        val result = alert.transitionTo(AlertStatus.FALSE_POSITIVE)
        assertEquals(AlertStatus.FALSE_POSITIVE, result.status)
    }

    @Test
    @DisplayName("Alert.transitionTo UNDER_REVIEW -> OPEN throws")
    fun `transitionTo UNDER_REVIEW to OPEN throws`() {
        val alert = buildAlert(AlertStatus.UNDER_REVIEW)
        assertThrows(IllegalArgumentException::class.java) {
            alert.transitionTo(AlertStatus.OPEN)
        }
    }

    @Test
    @DisplayName("Alert.transitionTo UNDER_REVIEW -> UNDER_REVIEW throws")
    fun `transitionTo UNDER_REVIEW to UNDER_REVIEW throws`() {
        val alert = buildAlert(AlertStatus.UNDER_REVIEW)
        assertThrows(IllegalArgumentException::class.java) {
            alert.transitionTo(AlertStatus.UNDER_REVIEW)
        }
    }

    @Test
    @DisplayName("Alert.transitionTo CLOSED -> any status throws (terminal)")
    fun `transitionTo CLOSED to any throws`() {
        val alert = buildAlert(AlertStatus.CLOSED)
        AlertStatus.entries.forEach { target ->
            assertThrows(IllegalArgumentException::class.java) {
                alert.transitionTo(target)
            }
        }
    }

    @Test
    @DisplayName("Alert.transitionTo FALSE_POSITIVE -> any status throws (terminal)")
    fun `transitionTo FALSE_POSITIVE to any throws`() {
        val alert = buildAlert(AlertStatus.FALSE_POSITIVE)
        AlertStatus.entries.forEach { target ->
            assertThrows(IllegalArgumentException::class.java) {
                alert.transitionTo(target)
            }
        }
    }

    // --- AlertStatus.canTransitionTo exhaustive coverage ---

    @Test
    @DisplayName("canTransitionTo covers all from-to combinations")
    fun `canTransitionTo all combinations`() {
        // OPEN -> only UNDER_REVIEW is true
        assertTrue(AlertStatus.OPEN.canTransitionTo(AlertStatus.UNDER_REVIEW))
        assertEquals(false, AlertStatus.OPEN.canTransitionTo(AlertStatus.OPEN))
        assertEquals(false, AlertStatus.OPEN.canTransitionTo(AlertStatus.CLOSED))
        assertEquals(false, AlertStatus.OPEN.canTransitionTo(AlertStatus.FALSE_POSITIVE))

        // UNDER_REVIEW -> CLOSED or FALSE_POSITIVE
        assertEquals(false, AlertStatus.UNDER_REVIEW.canTransitionTo(AlertStatus.OPEN))
        assertEquals(false, AlertStatus.UNDER_REVIEW.canTransitionTo(AlertStatus.UNDER_REVIEW))
        assertTrue(AlertStatus.UNDER_REVIEW.canTransitionTo(AlertStatus.CLOSED))
        assertTrue(AlertStatus.UNDER_REVIEW.canTransitionTo(AlertStatus.FALSE_POSITIVE))

        // CLOSED -> nothing
        AlertStatus.entries.forEach { target ->
            assertEquals(false, AlertStatus.CLOSED.canTransitionTo(target))
        }

        // FALSE_POSITIVE -> nothing
        AlertStatus.entries.forEach { target ->
            assertEquals(false, AlertStatus.FALSE_POSITIVE.canTransitionTo(target))
        }
    }

    // --- createAlertIfNotExists from DecisionMadeEvent ---

    @Test
    @DisplayName("createAlertIfNotExists from DecisionMadeEvent creates alert")
    fun `createAlertIfNotExists from event creates alert`() {
        val transactionId = TransactionId("TX-EVENT-001")
        val ruleId = RuleId(UUID.randomUUID())
        val alertSlot = slot<Alert>()

        every { alertRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns null
        every { alertRepository.save(capture(alertSlot)) } answers { alertSlot.captured }

        val event = DecisionMadeEvent(
            eventId = EventId("evt-1"),
            traceId = TraceId("trace-evt"),
            timestamp = Instant.now(),
            transactionId = transactionId,
            customerId = CustomerId("CUST-EVENT"),
            ruleId = ruleId,
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            decision = Decision.ALERT,
            actions = listOf(Action.GENERATE_ALERT),
            facts = mapOf(
                FactName("keywordMatched") to FactValue.BooleanValue(true),
                FactName("customerRisk") to FactValue.EnumValue("AR"),
                FactName("amount") to FactValue.NumberValue(BigDecimal("5000")),
                FactName("description") to FactValue.StringValue("test"),
                FactName("txAmount") to FactValue.MoneyValue(BigDecimal("10000"), "BRL")
            ),
            matchedExpressions = listOf(
                ExpressionEvaluation(
                    factName = FactName("keywordMatched"),
                    operator = ComparisonOperator.EQUALS,
                    expectedValue = FactValue.BooleanValue(true),
                    actualValue = FactValue.BooleanValue(true),
                    satisfied = true,
                    justification = "matched"
                )
            ),
            configurationVersion = ConfigurationVersion(2),
            executionTimeMs = 15L,
            explanation = DecisionExplanation(traceId = TraceId("trace-evt"), steps = emptyList())
        )

        val result = alertService.createAlertIfNotExists(event)

        assertNotNull(result)
        assertEquals(transactionId, result!!.transactionId)
        assertEquals(ruleId, result.ruleId)
        assertEquals(CustomerId("CUST-EVENT"), result.customerId)
        // Verify factValueToSerializable covers all branches
        val facts = result.facts!!
        assertEquals(true, facts["keywordMatched"])
        assertEquals("AR", facts["customerRisk"])
        assertEquals(BigDecimal("5000"), facts["amount"])
        assertEquals("test", facts["description"])
        @Suppress("UNCHECKED_CAST")
        val moneyMap = facts["txAmount"] as Map<String, Any?>
        assertEquals(BigDecimal("10000"), moneyMap["amount"])
        assertEquals("BRL", moneyMap["currency"])
    }

    @Test
    @DisplayName("createAlertIfNotExists from event returns existing alert (idempotent)")
    fun `createAlertIfNotExists from event returns existing`() {
        val transactionId = TransactionId("TX-EXIST")
        val ruleId = RuleId(UUID.randomUUID())
        val existingAlert = buildAlert(AlertStatus.OPEN).copy(
            transactionId = transactionId, ruleId = ruleId
        )

        every { alertRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns existingAlert

        val event = DecisionMadeEvent(
            eventId = EventId("evt-2"),
            traceId = TraceId("trace-2"),
            timestamp = Instant.now(),
            transactionId = transactionId,
            customerId = CustomerId("CUST-X"),
            ruleId = ruleId,
            ruleCode = RuleCode("RULE"),
            decision = Decision.ALERT,
            actions = listOf(Action.GENERATE_ALERT),
            facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
            matchedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1),
            executionTimeMs = 10L,
            explanation = DecisionExplanation(traceId = TraceId("trace-2"), steps = emptyList())
        )

        val result = alertService.createAlertIfNotExists(event)

        // Returns existing alert (not null) — idempotent behavior
        assertEquals(existingAlert, result)
        verify(exactly = 0) { alertRepository.save(any()) }
    }

    // --- updateStatus additional invalid transitions ---

    @Test
    @DisplayName("updateStatus throws for OPEN -> FALSE_POSITIVE")
    fun `updateStatus throws for OPEN to FALSE_POSITIVE`() {
        val alertId = AlertId(UUID.randomUUID())
        val alert = buildAlert(AlertStatus.OPEN).copy(id = alertId)
        every { alertRepository.findById(alertId) } returns alert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.FALSE_POSITIVE)
        }
    }

    @Test
    @DisplayName("updateStatus throws for CLOSED -> CLOSED")
    fun `updateStatus throws for CLOSED to CLOSED`() {
        val alertId = AlertId(UUID.randomUUID())
        val alert = buildAlert(AlertStatus.CLOSED).copy(id = alertId)
        every { alertRepository.findById(alertId) } returns alert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.CLOSED)
        }
    }

    @Test
    @DisplayName("updateStatus throws for FALSE_POSITIVE -> FALSE_POSITIVE")
    fun `updateStatus throws for FALSE_POSITIVE to FALSE_POSITIVE`() {
        val alertId = AlertId(UUID.randomUUID())
        val alert = buildAlert(AlertStatus.FALSE_POSITIVE).copy(id = alertId)
        every { alertRepository.findById(alertId) } returns alert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.FALSE_POSITIVE)
        }
    }

    @Test
    @DisplayName("updateStatus throws for FALSE_POSITIVE -> CLOSED")
    fun `updateStatus throws for FALSE_POSITIVE to CLOSED`() {
        val alertId = AlertId(UUID.randomUUID())
        val alert = buildAlert(AlertStatus.FALSE_POSITIVE).copy(id = alertId)
        every { alertRepository.findById(alertId) } returns alert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.CLOSED)
        }
    }

    @Test
    @DisplayName("updateStatus throws for CLOSED -> FALSE_POSITIVE")
    fun `updateStatus throws for CLOSED to FALSE_POSITIVE`() {
        val alertId = AlertId(UUID.randomUUID())
        val alert = buildAlert(AlertStatus.CLOSED).copy(id = alertId)
        every { alertRepository.findById(alertId) } returns alert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.FALSE_POSITIVE)
        }
    }

    @Test
    @DisplayName("updateStatus throws for CLOSED -> UNDER_REVIEW")
    fun `updateStatus throws for CLOSED to UNDER_REVIEW`() {
        val alertId = AlertId(UUID.randomUUID())
        val alert = buildAlert(AlertStatus.CLOSED).copy(id = alertId)
        every { alertRepository.findById(alertId) } returns alert

        assertThrows(InvalidAlertTransitionException::class.java) {
            alertService.updateStatus(alertId, AlertStatus.UNDER_REVIEW)
        }
    }

    // --- Helper ---

    private fun buildAlert(status: AlertStatus = AlertStatus.OPEN) = Alert(
        id = AlertId(UUID.randomUUID()),
        transactionId = TransactionId("TX-TEST"),
        ruleId = RuleId(UUID.randomUUID()),
        customerId = CustomerId("CUST-TEST"),
        facts = null,
        configurationVersion = 1,
        traceId = null,
        actions = null,
        explanation = null,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
