package br.com.decision.infrastructure.input.event

import br.com.decision.application.usecase.ExecuteDecisionUseCase
import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.FactName
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Property-Based Tests for DetectionEvent Validation.
 *
 * **Property 11: DetectionEvent Validation**
 * **Validates: Requirements 4.4, 4.5, 4.6**
 *
 * Properties verified:
 * 1. Valid event (non-blank transactionId ≤100, non-blank customerId ≤64, existing ruleCode)
 *    → ExecuteDecisionUseCase invoked
 * 2. Non-existing ruleCode → discarded
 *
 * Note: Properties for blank/oversized transactionId and customerId were removed because
 * Value Objects (TransactionId, CustomerId) now enforce these constraints at construction time.
 * It is impossible to create a DetectionEvent with invalid transactionId/customerId.
 * Those constraints are validated via unit tests on the VOs themselves.
 */
class DetectionEventValidationPropertyTest {

    // --- Test dependencies ---

    private val executeDecisionUseCase = mockk<ExecuteDecisionUseCase>(relaxed = true)
    private val ruleDefinitionRepository = mockk<RuleDefinitionRepository>()

    private val listener = DetectionEventListener(
        executeDecisionUseCase = executeDecisionUseCase,
        ruleDefinitionRepository = ruleDefinitionRepository
    )

    private val knownRuleCodes = listOf("KEYWORD_SCREENING", "SANCTIONS_CHECK", "AML_RULE")

    private fun ruleDefinitionFor(code: String) = RuleDefinition(
        id = RuleId(UUID.randomUUID()),
        code = RuleCode(code),
        name = "$code Rule",
        description = "Rule definition for $code",
        context = RuleContext.SCREENING,
        category = RuleCategory.KEYWORD_SCREENING,
        supportedFacts = listOf(FactName("keywordMatched"), FactName("customerRisk")),
        supportedActions = listOf(Action.GENERATE_ALERT, Action.IGNORE),
        status = RuleStatus.ACTIVE,
        createdAt = Instant.now()
    )

    // Setup known rule codes in repository
    @BeforeEach
    fun setup() {
        clearMocks(executeDecisionUseCase, ruleDefinitionRepository)
        knownRuleCodes.forEach { code ->
            every { ruleDefinitionRepository.findByCode(RuleCode(code)) } returns ruleDefinitionFor(code)
        }
    }

    // --- Random generators ---

    private fun randomValidTransactionId(): TransactionId {
        val length = Random.nextInt(1, 65)
        val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val value = (1..length).map { chars.random() }.joinToString("")
        return TransactionId(value)
    }

    private fun randomValidCustomerId(): CustomerId {
        val length = Random.nextInt(1, 65)
        val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val value = (1..length).map { chars.random() }.joinToString("")
        return CustomerId(value)
    }

    private fun randomKnownRuleCode(): String = knownRuleCodes.random()

    private fun randomDetectionResult(): DetectionResult {
        val matched = Random.nextBoolean()
        val matches = if (matched) {
            val count = Random.nextInt(1, 4)
            (1..count).map {
                DetectionMatch(
                    term = "term-${Random.nextInt(1, 101)}",
                    category = listOf("AML", "TERRORISM", "FRAUD", "SANCTIONS").random()
                )
            }
        } else emptyList()
        return DetectionResult(matched = matched, matches = matches)
    }

    private fun buildEvent(
        transactionId: TransactionId,
        customerId: CustomerId,
        ruleCode: String,
        detectionResult: DetectionResult = DetectionResult(matched = true, matches = listOf(DetectionMatch("lavagem", "AML")))
    ) = DetectionEvent(
        eventId = EventId(UUID.randomUUID().toString()),
        traceId = TraceId(UUID.randomUUID().toString()),
        timestamp = Instant.now(),
        transactionId = transactionId,
        customerId = customerId,
        ruleCode = RuleCode(ruleCode),
        detectionResult = detectionResult
    )

    private fun randomUnknownRuleCode(): String {
        val length = Random.nextInt(1, 21)
        val chars = ('A'..'Z') + ('0'..'9')
        val suffix = (1..length).map { chars.random() }.joinToString("")
        return "UNKNOWN_$suffix"
    }

    // --- Property Tests ---

    @RepeatedTest(200)
    @DisplayName("Property 11.1: valid event → ExecuteDecisionUseCase invoked")
    fun `Property 11-1 valid event triggers ExecuteDecisionUseCase`() {
        clearMocks(executeDecisionUseCase, answers = false, recordedCalls = true)

        val txId = randomValidTransactionId()
        val custId = randomValidCustomerId()
        val ruleCode = randomKnownRuleCode()
        val detResult = randomDetectionResult()

        val event = buildEvent(
            transactionId = txId,
            customerId = custId,
            ruleCode = ruleCode,
            detectionResult = detResult
        )
        listener.handle(event)

        verify(exactly = 1) { executeDecisionUseCase.execute(any()) }
    }

    @RepeatedTest(200)
    @DisplayName("Property 11.6: non-existing ruleCode → discarded")
    fun `Property 11-6 non-existing ruleCode is discarded`() {
        clearMocks(executeDecisionUseCase, ruleDefinitionRepository, answers = false, recordedCalls = true)

        // Re-setup known codes after clear
        knownRuleCodes.forEach { code ->
            every { ruleDefinitionRepository.findByCode(RuleCode(code)) } returns ruleDefinitionFor(code)
        }

        val txId = randomValidTransactionId()
        val custId = randomValidCustomerId()
        val unknownCode = randomUnknownRuleCode()

        // Unknown code returns null
        every { ruleDefinitionRepository.findByCode(RuleCode(unknownCode)) } returns null

        val event = buildEvent(
            transactionId = txId,
            customerId = custId,
            ruleCode = unknownCode
        )
        listener.handle(event)

        verify(exactly = 0) { executeDecisionUseCase.execute(any()) }
    }
}
