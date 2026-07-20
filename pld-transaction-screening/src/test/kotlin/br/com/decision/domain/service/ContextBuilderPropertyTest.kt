package br.com.decision.domain.service

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionMatch
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import kotlin.random.Random

/**
 * Property-based tests for [ContextBuilder].
 *
 * **Property 10: Context Builder Selective Resolution**
 * **Validates: Requirements 3.1, 3.3, 3.6**
 *
 * Verifies:
 * 1. A resolver is invoked iff at least one of its producedFacts is in requiredFacts
 * 2. A resolver failure does NOT propagate as exception — remaining resolvers still execute
 * 3. Failed resolver → the fact it produces is absent from the returned FactSet
 * 4. Successful resolver → all its produced facts appear in the FactSet
 */
class ContextBuilderPropertyTest {

    // Finite pool of fact names to draw from
    private val factPool = listOf(
        "keywordMatched", "customerRisk", "pep", "segment",
        "country", "amount", "currency", "channel", "riskScore", "accountAge"
    ).map { FactName(it) }

    private val entities = listOf("Screening", "Risk", "Customer", "Transaction", "Account")

    private data class ResolverConfig(
        val producedFacts: Set<FactName>,
        val shouldFail: Boolean,
        val entity: String
    )

    /**
     * Generates a non-empty subset of the factPool.
     */
    private fun randomFactSubset(): Set<FactName> {
        val included = factPool.filter { Random.nextBoolean() }.toSet()
        return included.ifEmpty { setOf(factPool[Random.nextInt(factPool.size)]) }
    }

    private fun randomRequiredFacts(): List<FactName> = randomFactSubset().toList()

    private fun randomResolverConfigs(withFailures: Boolean = false): List<ResolverConfig> {
        val count = Random.nextInt(1, 6)
        return (0 until count).map { idx ->
            val facts = factPool.filter { Random.nextBoolean() }.toSet()
                .ifEmpty { setOf(factPool[Random.nextInt(factPool.size)]) }
            ResolverConfig(
                producedFacts = facts,
                shouldFail = if (withFailures) Random.nextBoolean() else false,
                entity = entities[idx % entities.size]
            )
        }
    }

    private fun buildTrackingResolver(config: ResolverConfig): TrackingResolver {
        val facts = config.producedFacts.map { factName ->
            Fact(factName, FactValue.BooleanValue(true), config.entity, Instant.now())
        }
        return TrackingResolver(
            producedFacts = config.producedFacts,
            entity = config.entity,
            result = facts,
            exception = if (config.shouldFail) RuntimeException("Simulated failure") else null
        )
    }

    private fun detectionEvent() = DetectionEvent(
        eventId = EventId("evt-prop-test"),
        traceId = TraceId("trace-prop-test"),
        timestamp = Instant.now(),
        transactionId = TransactionId("TX-PROP"),
        customerId = CustomerId("CUST-PROP"),
        ruleCode = RuleCode("KEYWORD_SCREENING"),
        detectionResult = DetectionResult(
            matched = true,
            matches = listOf(DetectionMatch("test", "AML"))
        )
    )

    // Property 10.1: A resolver is invoked iff at least one of its producedFacts is in requiredFacts
    @RepeatedTest(200)
    @DisplayName("only resolvers with matching producedFacts are invoked")
    fun `only resolvers with matching producedFacts are invoked`() {
        val requiredFacts = randomRequiredFacts()
        val configs = randomResolverConfigs()
        val resolvers = configs.map { buildTrackingResolver(it) }
        val builder = ContextBuilder(resolvers)

        builder.buildContext(detectionEvent(), requiredFacts)

        val requiredSet = requiredFacts.toSet()
        resolvers.forEachIndexed { idx, resolver ->
            val hasOverlap = configs[idx].producedFacts.any { it in requiredSet }
            assertEquals(hasOverlap, resolver.invoked)
        }
    }

    // Property 10.2: Resolver failure does NOT propagate as exception — remaining resolvers execute
    @RepeatedTest(200)
    @DisplayName("resolver failure does not propagate and remaining resolvers still execute")
    fun `resolver failure does not propagate and remaining resolvers still execute`() {
        val requiredFacts = randomRequiredFacts()
        val configs = randomResolverConfigs(withFailures = true)
        val resolvers = configs.map { buildTrackingResolver(it) }
        val builder = ContextBuilder(resolvers)

        // Should NOT throw — failures are captured internally
        builder.buildContext(detectionEvent(), requiredFacts)

        val requiredSet = requiredFacts.toSet()
        // All relevant resolvers were invoked regardless of earlier failures
        resolvers.forEachIndexed { idx, resolver ->
            val hasOverlap = configs[idx].producedFacts.any { it in requiredSet }
            assertEquals(hasOverlap, resolver.invoked)
        }
    }

    // Property 10.3: Failed resolver → fact absent from returned FactSet
    // (unless another successful resolver also produces the same fact)
    @RepeatedTest(200)
    @DisplayName("failed resolver produces absent facts in FactSet")
    fun `failed resolver produces absent facts in FactSet`() {
        val requiredFacts = randomRequiredFacts()
        val configs = randomResolverConfigs(withFailures = true)
        val resolvers = configs.map { buildTrackingResolver(it) }
        val builder = ContextBuilder(resolvers)

        val factSet = builder.buildContext(detectionEvent(), requiredFacts)

        val requiredSet = requiredFacts.toSet()

        // Collect facts that are produced by at least one successful relevant resolver
        val factsFromSuccessfulResolvers = configs
            .filter { config ->
                config.producedFacts.any { it in requiredSet } && !config.shouldFail
            }
            .flatMap { it.producedFacts }
            .toSet()

        configs.forEachIndexed { idx, config ->
            val hasOverlap = config.producedFacts.any { it in requiredSet }
            if (hasOverlap && config.shouldFail) {
                // Facts exclusively produced by failing resolvers should be absent
                config.producedFacts
                    .filter { it !in factsFromSuccessfulResolvers }
                    .forEach { factName ->
                        assertEquals(false, factSet.facts.containsKey(factName))
                    }
            }
        }
    }

    // Property 10.4: Successful resolver → all produced facts appear in FactSet
    @RepeatedTest(200)
    @DisplayName("successful resolver produces all its facts in FactSet")
    fun `successful resolver produces all its facts in FactSet`() {
        val requiredFacts = randomRequiredFacts()
        val configs = randomResolverConfigs()
        val resolvers = configs.map { buildTrackingResolver(it) }
        val builder = ContextBuilder(resolvers)

        val factSet = builder.buildContext(detectionEvent(), requiredFacts)

        val requiredSet = requiredFacts.toSet()
        configs.forEachIndexed { idx, config ->
            val hasOverlap = config.producedFacts.any { it in requiredSet }
            if (hasOverlap && !config.shouldFail) {
                // Successful resolver's facts should all be present
                config.producedFacts.forEach { factName ->
                    assertEquals(true, factSet.facts.containsKey(factName))
                }
            }
        }
    }

    /**
     * Resolver that tracks whether it was invoked.
     */
    private class TrackingResolver(
        override val producedFacts: Set<FactName>,
        override val entity: String,
        private val result: List<Fact>,
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
