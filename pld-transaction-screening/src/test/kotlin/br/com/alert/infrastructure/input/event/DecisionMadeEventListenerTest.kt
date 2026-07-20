package br.com.alert.infrastructure.input.event

import br.com.alert.application.usecase.CreateAlertUseCase
import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.model.vo.AlertId
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TransactionId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TraceId

class DecisionMadeEventListenerTest {

    private val createAlertUseCase = mockk<CreateAlertUseCase>(relaxed = true)
    private val listener = DecisionMadeEventListener(createAlertUseCase)

    @BeforeEach
    fun setUp() {
        clearMocks(createAlertUseCase)
    }

    private fun buildEvent(actions: List<Action> = listOf(Action.GENERATE_ALERT)): DecisionMadeEvent {
        return DecisionMadeEvent(
            eventId = EventId(UUID.randomUUID().toString()),
            traceId = TraceId("trace-${UUID.randomUUID()}"),
            timestamp = Instant.now(),
            transactionId = TransactionId("TX-${UUID.randomUUID().toString().take(8)}"),
            customerId = CustomerId("CUST-42"),
            ruleId = RuleId(UUID.randomUUID()),
            ruleCode = RuleCode("KEYWORD_SCREENING"),
            decision = if (Action.GENERATE_ALERT in actions) Decision.ALERT else Decision.IGNORE,
            actions = actions,
            facts = mapOf(FactName("keywordMatched") to FactValue.BooleanValue(true)),
            matchedExpressions = emptyList(),
            configurationVersion = ConfigurationVersion(1),
            executionTimeMs = 10,
            explanation = DecisionExplanation(traceId = TraceId("trace-1"), steps = emptyList())
        )
    }

    @Test
    @DisplayName("handle should invoke createAlertIfNotExists when GENERATE_ALERT in actions")
    fun `handle should invoke createAlertIfNotExists when GENERATE_ALERT in actions`() {
        val event = buildEvent(actions = listOf(Action.GENERATE_ALERT))

        listener.handle(event)

        verify(exactly = 1) { createAlertUseCase.createAlertIfNotExists(event) }
    }

    @Test
    @DisplayName("handle should NOT invoke createAlertIfNotExists when IGNORE action only")
    fun `handle should NOT invoke createAlertIfNotExists when IGNORE action only`() {
        val event = buildEvent(actions = listOf(Action.IGNORE))

        listener.handle(event)

        verify(exactly = 0) { createAlertUseCase.createAlertIfNotExists(any()) }
    }

    @Test
    @DisplayName("handle should NOT invoke createAlertIfNotExists when actions is empty")
    fun `handle should NOT invoke createAlertIfNotExists when actions is empty`() {
        val event = buildEvent(actions = emptyList())

        listener.handle(event)

        verify(exactly = 0) { createAlertUseCase.createAlertIfNotExists(any()) }
    }

    @Test
    @DisplayName("handle should invoke createAlertIfNotExists when GENERATE_ALERT is among multiple actions")
    fun `handle should invoke createAlertIfNotExists when GENERATE_ALERT is among multiple actions`() {
        val event = buildEvent(actions = listOf(Action.GENERATE_ALERT, Action.REVIEW))

        listener.handle(event)

        verify(exactly = 1) { createAlertUseCase.createAlertIfNotExists(event) }
    }
}
