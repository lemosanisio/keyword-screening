package br.com.decision.domain.service

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.ResolverOutcome
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.CustomerId

@DisplayName("ContextBuilder — Unit Tests")
class ContextBuilderTest {

    @Nested
    @DisplayName("Selective resolver invocation")
    inner class SelectiveInvocation {

        @Test
        fun `invokes only resolvers whose producedFacts intersect with requiredFacts`() {
            val screeningResolver = StubResolver(
                producedFacts = setOf(FactName("keywordMatched")),
                entity = "Screening",
                result = listOf(
                    Fact(FactName("keywordMatched"), FactValue.BooleanValue(true), "Screening", Instant.now())
                )
            )
            val customerResolver = StubResolver(
                producedFacts = setOf(FactName("customerRisk")),
                entity = "Risk",
                result = listOf(
                    Fact(FactName("customerRisk"), FactValue.EnumValue("AR"), "Risk", Instant.now())
                )
            )

            val builder = ContextBuilder(listOf(screeningResolver, customerResolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = listOf(FactName("keywordMatched"))
            )

            assertThat(screeningResolver.invoked).isTrue()
            assertThat(customerResolver.invoked).isFalse()
            assertThat(factSet.facts).containsKey(FactName("keywordMatched"))
            assertThat(factSet.facts).doesNotContainKey(FactName("customerRisk"))
        }

        @Test
        fun `invokes all resolvers when all facts are required`() {
            val screeningResolver = StubResolver(
                producedFacts = setOf(FactName("keywordMatched")),
                entity = "Screening",
                result = listOf(
                    Fact(FactName("keywordMatched"), FactValue.BooleanValue(true), "Screening", Instant.now())
                )
            )
            val customerResolver = StubResolver(
                producedFacts = setOf(FactName("customerRisk")),
                entity = "Risk",
                result = listOf(
                    Fact(FactName("customerRisk"), FactValue.EnumValue("MR"), "Risk", Instant.now())
                )
            )

            val builder = ContextBuilder(listOf(screeningResolver, customerResolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = listOf(FactName("keywordMatched"), FactName("customerRisk"))
            )

            assertThat(screeningResolver.invoked).isTrue()
            assertThat(customerResolver.invoked).isTrue()
            assertThat(factSet.facts).hasSize(2)
        }

        @Test
        fun `invokes no resolvers when requiredFacts is empty`() {
            val resolver = StubResolver(
                producedFacts = setOf(FactName("keywordMatched")),
                entity = "Screening",
                result = listOf(
                    Fact(FactName("keywordMatched"), FactValue.BooleanValue(true), "Screening", Instant.now())
                )
            )

            val builder = ContextBuilder(listOf(resolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = emptyList()
            )

            assertThat(resolver.invoked).isFalse()
            assertThat(factSet.facts).isEmpty()
            assertThat(factSet.resolverResults).isEmpty()
        }

        @Test
        fun `does not invoke resolver when required fact does not match any resolver`() {
            val resolver = StubResolver(
                producedFacts = setOf(FactName("keywordMatched")),
                entity = "Screening",
                result = listOf(
                    Fact(FactName("keywordMatched"), FactValue.BooleanValue(true), "Screening", Instant.now())
                )
            )

            val builder = ContextBuilder(listOf(resolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = listOf(FactName("nonExistentFact"))
            )

            assertThat(resolver.invoked).isFalse()
            assertThat(factSet.facts).isEmpty()
        }
    }

    @Nested
    @DisplayName("Exception handling")
    inner class ExceptionHandling {

        @Test
        fun `captures resolver exception and continues without propagating`() {
            val failingResolver = StubResolver(
                producedFacts = setOf(FactName("customerRisk")),
                entity = "Risk",
                exception = RuntimeException("Connection timeout")
            )
            val successResolver = StubResolver(
                producedFacts = setOf(FactName("keywordMatched")),
                entity = "Screening",
                result = listOf(
                    Fact(FactName("keywordMatched"), FactValue.BooleanValue(true), "Screening", Instant.now())
                )
            )

            val builder = ContextBuilder(listOf(failingResolver, successResolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = listOf(FactName("customerRisk"), FactName("keywordMatched"))
            )

            assertThat(factSet.facts).containsKey(FactName("keywordMatched"))
            assertThat(factSet.facts).doesNotContainKey(FactName("customerRisk"))
        }

        @Test
        fun `records Failure outcome when resolver throws exception`() {
            val failingResolver = StubResolver(
                producedFacts = setOf(FactName("customerRisk")),
                entity = "Risk",
                exception = RuntimeException("Service unavailable")
            )

            val builder = ContextBuilder(listOf(failingResolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = listOf(FactName("customerRisk"))
            )

            assertThat(factSet.resolverResults).hasSize(1)
            val result = factSet.resolverResults.first()
            assertThat(result.result).isInstanceOf(ResolverOutcome.Failure::class.java)
            val failure = result.result as ResolverOutcome.Failure
            assertThat(failure.factName).isEqualTo(FactName("customerRisk"))
            assertThat(failure.error).isEqualTo("RuntimeException")
            assertThat(failure.reason).isEqualTo("Service unavailable")
        }
    }

    @Nested
    @DisplayName("ResolverResult tracking")
    inner class ResolverResultTracking {

        @Test
        fun `records Success outcome with timing for each resolved fact`() {
            val resolver = StubResolver(
                producedFacts = setOf(FactName("keywordMatched")),
                entity = "Screening",
                result = listOf(
                    Fact(FactName("keywordMatched"), FactValue.BooleanValue(true), "Screening", Instant.now())
                )
            )

            val builder = ContextBuilder(listOf(resolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = listOf(FactName("keywordMatched"))
            )

            assertThat(factSet.resolverResults).hasSize(1)
            val result = factSet.resolverResults.first()
            assertThat(result.resolverName).isEqualTo("StubResolver")
            assertThat(result.entity).isEqualTo("Screening")
            assertThat(result.startedAt).isNotNull()
            assertThat(result.finishedAt).isNotNull()
            assertThat(result.finishedAt).isAfterOrEqualTo(result.startedAt)
            assertThat(result.durationMs).isGreaterThanOrEqualTo(0)
            assertThat(result.result).isInstanceOf(ResolverOutcome.Success::class.java)
            val success = result.result as ResolverOutcome.Success
            assertThat(success.factName).isEqualTo(FactName("keywordMatched"))
            assertThat(success.value).isEqualTo(FactValue.BooleanValue(true))
        }

        @Test
        fun `records timing for failed resolvers`() {
            val failingResolver = StubResolver(
                producedFacts = setOf(FactName("customerRisk")),
                entity = "Risk",
                exception = IllegalStateException("Timeout")
            )

            val builder = ContextBuilder(listOf(failingResolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = listOf(FactName("customerRisk"))
            )

            val result = factSet.resolverResults.first()
            assertThat(result.startedAt).isNotNull()
            assertThat(result.finishedAt).isAfterOrEqualTo(result.startedAt)
            assertThat(result.durationMs).isGreaterThanOrEqualTo(0)
        }

        @Test
        fun `resolver that produces multiple facts creates one result per fact`() {
            val multiFactResolver = StubResolver(
                producedFacts = setOf(FactName("factA"), FactName("factB")),
                entity = "Multi",
                result = listOf(
                    Fact(FactName("factA"), FactValue.StringValue("valueA"), "Multi", Instant.now()),
                    Fact(FactName("factB"), FactValue.StringValue("valueB"), "Multi", Instant.now())
                )
            )

            val builder = ContextBuilder(listOf(multiFactResolver))

            val factSet = builder.buildContext(
                event = detectionEvent(),
                requiredFacts = listOf(FactName("factA"), FactName("factB"))
            )

            assertThat(factSet.facts).hasSize(2)
            assertThat(factSet.resolverResults).hasSize(2)
            assertThat(factSet.resolverResults.all { it.result is ResolverOutcome.Success }).isTrue()
        }
    }

    // --- Test Helpers ---

    private fun detectionEvent() = DetectionEvent(
        eventId = EventId("evt-001"),
        traceId = TraceId("trace-001"),
        timestamp = Instant.now(), transactionId = TransactionId("TX-001"), customerId = CustomerId("CUST-42"), ruleCode = RuleCode("KEYWORD_SCREENING"),
        detectionResult = DetectionResult(
            matched = true,
            matches = listOf(DetectionMatch("lavagem", "AML"))
        )
    )

    private class StubResolver(
        override val producedFacts: Set<FactName>,
        override val entity: String,
        private val result: List<Fact> = emptyList(),
        private val exception: Exception? = null
    ) : FactResolver {
        var invoked = false
            private set

        override fun resolve(event: DetectionEvent): List<Fact> {
            invoked = true
            if (exception != null) throw exception
            return result
        }
    }
}
