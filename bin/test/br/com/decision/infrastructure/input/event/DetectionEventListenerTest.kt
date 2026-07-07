package br.com.decision.infrastructure.input.event

import br.com.decision.application.usecase.ExecuteDecisionCommand
import br.com.decision.application.usecase.ExecuteDecisionUseCase
import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId

@DisplayName("DetectionEventListener — Unit Tests")
class DetectionEventListenerTest {

    private val executeDecisionUseCase = mockk<ExecuteDecisionUseCase>(relaxed = true)
    private val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()

    private val listener = DetectionEventListener(
        executeDecisionUseCase = executeDecisionUseCase,
        ruleDefinitionRepository = ruleDefinitionRepository
    )

    private val ruleDefinition = RuleDefinition(
        id = RuleId(UUID.randomUUID()),
        code = RuleCode("KEYWORD_SCREENING"),
        name = "Keyword Screening",
        description = "Detecção de keywords suspeitas",
        context = RuleContext.SCREENING,
        category = RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("keywordMatched"), FactName("customerRisk")),
        supportedActions = listOf(Action.GENERATE_ALERT, Action.IGNORE),
        status = RuleStatus.ACTIVE,
        createdAt = Instant.now()
    )

    private fun buildEvent(
        transactionId: TransactionId = TransactionId("TX-001"),
        customerId: CustomerId = CustomerId("CUST-42"),
        ruleCode: RuleCode = RuleCode("KEYWORD_SCREENING"),
        matched: Boolean = true
    ) = DetectionEvent(
        eventId = EventId(UUID.randomUUID().toString()),
        traceId = TraceId(UUID.randomUUID().toString()),
        timestamp = Instant.now(),
        transactionId = transactionId,
        customerId = customerId,
        ruleCode = ruleCode,
        detectionResult = DetectionResult(
            matched = matched,
            matches = if (matched) listOf(DetectionMatch("lavagem", "AML")) else emptyList()
        )
    )

    @Nested
    @DisplayName("Valid events — should invoke use case")
    inner class ValidEvents {

        @Test
        fun `invokes ExecuteDecisionUseCase with correct command for valid event`() {
            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition

            val commandSlot = slot<ExecuteDecisionCommand>()
            every { executeDecisionUseCase.execute(capture(commandSlot)) } returns mockk()

            val event = buildEvent()
            listener.handle(event)

            verify(exactly = 1) { executeDecisionUseCase.execute(any()) }

            val captured = commandSlot.captured
            assertThat(captured.transactionId).isEqualTo(TransactionId("TX-001"))
            assertThat(captured.customerId).isEqualTo(CustomerId("CUST-42"))
            assertThat(captured.ruleCode).isEqualTo(RuleCode("KEYWORD_SCREENING"))
            assertThat(captured.detectionResult.matched).isTrue()
            assertThat(captured.detectionResult.matches).hasSize(1)
            assertThat(captured.detectionResult.matches[0].term).isEqualTo("lavagem")
        }

        @Test
        fun `processes event with maximum allowed field lengths`() {
            val maxTransactionId = "a".repeat(64)
            val maxCustomerId = "b".repeat(64)

            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition

            val event = buildEvent(transactionId = TransactionId(maxTransactionId), customerId = CustomerId(maxCustomerId))
            listener.handle(event)

            verify(exactly = 1) { executeDecisionUseCase.execute(any()) }
        }

        @Test
        fun `processes event with unmatched detection result`() {
            every { ruleDefinitionRepository.findByCode(RuleCode("KEYWORD_SCREENING")) } returns ruleDefinition

            val event = buildEvent(matched = false)
            listener.handle(event)

            verify(exactly = 1) { executeDecisionUseCase.execute(any()) }
        }
    }

    @Nested
    @DisplayName("Value Object validation — blank/oversized values rejected at construction")
    inner class ValueObjectValidation {

        @Test
        fun `TransactionId rejects blank value`() {
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                TransactionId("")
            }
        }

        @Test
        fun `TransactionId rejects whitespace-only value`() {
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                TransactionId("   ")
            }
        }

        @Test
        fun `TransactionId rejects value exceeding 100 characters`() {
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                TransactionId("a".repeat(101))
            }
        }

        @Test
        fun `CustomerId rejects blank value`() {
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                CustomerId("")
            }
        }

        @Test
        fun `CustomerId rejects whitespace-only value`() {
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                CustomerId("   ")
            }
        }

        @Test
        fun `CustomerId rejects value exceeding 64 characters`() {
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                CustomerId("b".repeat(65))
            }
        }
    }

    @Nested
    @DisplayName("Invalid ruleCode — should discard")
    inner class InvalidRuleCode {

        @Test
        fun `discards event when ruleCode is blank`() {
            val event = buildEvent(ruleCode = RuleCode(""))
            listener.handle(event)

            verify(exactly = 0) { executeDecisionUseCase.execute(any()) }
        }

        @Test
        fun `discards event when ruleCode is only whitespace`() {
            val event = buildEvent(ruleCode = RuleCode("   "))
            listener.handle(event)

            verify(exactly = 0) { executeDecisionUseCase.execute(any()) }
        }

        @Test
        fun `discards event when ruleCode does not exist in Rule Catalog`() {
            every { ruleDefinitionRepository.findByCode(RuleCode("NONEXISTENT_RULE")) } returns null

            val event = buildEvent(ruleCode = RuleCode("NONEXISTENT_RULE"))
            listener.handle(event)

            verify(exactly = 0) { executeDecisionUseCase.execute(any()) }
        }
    }
}
