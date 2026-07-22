package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.DecisionResult
import br.com.decision.domain.model.EvaluationOutcome
import br.com.decision.domain.model.EvaluationStatus
import br.com.decision.domain.model.FactQuality
import br.com.decision.domain.model.FactResult
import br.com.decision.domain.model.RecommendedRoute
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DecisionExecutionTriStateMapperTest {
    private val mapper = DecisionExecutionMapper(jacksonObjectMapper())

    @Test
    fun `round trip preserves indeterminate semantics`() {
        val result = DecisionResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            executionTimeMs = 3,
            configurationVersion = ConfigurationVersion(2),
            facts = emptyMap(),
            factResults = listOf(
                FactResult(
                    name = FactName("customerRisk"),
                    quality = FactQuality.UNKNOWN,
                    source = "LEGACY_REST",
                    reasonCode = "SOURCE_DID_NOT_PROVIDE",
                ),
            ),
            evaluationStatus = EvaluationStatus.INDETERMINATE,
            evaluationOutcome = EvaluationOutcome.NO_SIGNAL,
            reviewRequired = true,
            recommendedRoute = RecommendedRoute.DERIVED_TO_ANALYST,
        )
        val execution = DecisionExecution(
            id = UUID.randomUUID(),
            transactionId = TransactionId("TX-1"),
            ruleId = RuleId(UUID.randomUUID()),
            configurationVersion = ConfigurationVersion(2),
            facts = emptyMap(),
            result = result,
            explanation = DecisionExplanation(TraceId("trace"), emptyList()),
            executionTimeMs = 3,
            traceId = TraceId("trace"),
            timestamp = Instant.now(),
        )

        val restored = mapper.toDomain(mapper.toEntity(execution)).result

        assertThat(restored.evaluationStatus).isEqualTo(EvaluationStatus.INDETERMINATE)
        assertThat(restored.reviewRequired).isTrue()
        assertThat(restored.recommendedRoute).isEqualTo(RecommendedRoute.DERIVED_TO_ANALYST)
        assertThat(restored.factResults.single().quality).isEqualTo(FactQuality.UNKNOWN)
        assertThat(restored.factResults.single().reasonCode).isEqualTo("SOURCE_DID_NOT_PROVIDE")
    }
}
