package br.com.decision.domain.service

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.model.ResolverOutcome
import br.com.decision.domain.model.ResolverResult
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Context Builder — orquestra Fact Resolvers.
 * Identifica quais Facts são necessários e invoca apenas os resolvers relevantes.
 * Captura exceções de resolvers individuais sem propagar — fact ausente é logado.
 *
 * Classe pura (sem @Service) — registrada via @Configuration.
 */
class ContextBuilder(
    private val resolvers: List<FactResolver>
) {

    private val logger = LoggerFactory.getLogger(ContextBuilder::class.java)

    /**
     * Constrói o contexto de decisão invocando apenas os resolvers cujos
     * producedFacts fazem interseção com os requiredFacts.
     *
     * @param event Evento de detecção contendo dados para resolução
     * @param requiredFacts Lista de FactNames necessários para avaliação
     * @return FactSet com facts resolvidos e resultados por resolver
     */
    fun buildContext(
        event: DetectionEvent,
        requiredFacts: List<FactName>
    ): FactSet {
        val requiredSet = requiredFacts.toSet()

        val relevantResolvers = resolvers.filter { resolver ->
            resolver.producedFacts.any { it in requiredSet }
        }

        val allFacts = mutableMapOf<FactName, FactValue>()
        val allResults = mutableListOf<ResolverResult>()

        for (resolver in relevantResolvers) {
            val startedAt = Instant.now()
            try {
                val facts = resolver.resolve(event)
                val finishedAt = Instant.now()
                val durationMs = Duration.between(startedAt, finishedAt).toMillis()

                for (fact in facts) {
                    allFacts[fact.name] = fact.value
                    allResults.add(
                        ResolverResult(
                            resolverName = resolver::class.simpleName ?: "Unknown",
                            entity = resolver.entity,
                            sourceSystem = resolver.entity,
                            startedAt = startedAt,
                            finishedAt = finishedAt,
                            durationMs = durationMs,
                            result = ResolverOutcome.Success(
                                factName = fact.name,
                                value = fact.value
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                val finishedAt = Instant.now()
                val durationMs = Duration.between(startedAt, finishedAt).toMillis()

                logger.error(
                    "Resolver {} falhou para evento transactionId={}: {}",
                    resolver::class.simpleName,
                    event.transactionId.value,
                    e.message,
                    e
                )

                for (factName in resolver.producedFacts.filter { it in requiredSet }) {
                    allResults.add(
                        ResolverResult(
                            resolverName = resolver::class.simpleName ?: "Unknown",
                            entity = resolver.entity,
                            sourceSystem = resolver.entity,
                            startedAt = startedAt,
                            finishedAt = finishedAt,
                            durationMs = durationMs,
                            result = ResolverOutcome.Failure(
                                factName = factName,
                                error = e::class.simpleName ?: "Exception",
                                reason = e.message ?: "Unknown error"
                            )
                        )
                    )
                }
            }
        }

        return FactSet(
            facts = allFacts,
            resolverResults = allResults
        )
    }
}

/**
 * Conjunto de fatos resolvidos pelo ContextBuilder com metadados de resolução.
 */
data class FactSet(
    val facts: Map<FactName, FactValue>,
    val resolverResults: List<ResolverResult>
)
