package br.com.decision.domain.service

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.model.Condition
import br.com.decision.domain.model.ContextBuildingStep
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.DecisionResult
import br.com.decision.domain.model.DecisionStep
import br.com.decision.domain.model.EvaluationStep
import br.com.decision.domain.model.EvaluationStatus
import br.com.decision.domain.model.EvaluationOutcome
import br.com.decision.domain.model.RecommendedRoute
import br.com.decision.domain.model.PersistenceStep
import br.com.decision.domain.model.PublicationStep
import br.com.decision.domain.model.ReceptionStep
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleIdentificationStep
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.TraceId
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Decision Engine — orquestrador principal de decisão.
 *
 * Responsabilidades:
 * 1. Verificar se a configuração está ativa
 * 2. Identificar facts requeridos pelas expressions
 * 3. Invocar ContextBuilder para construir o contexto de decisão
 * 4. Invocar RuleEngine para avaliar regras contra fatos
 * 5. Montar DecisionResult com DecisionExplanation completa (7 etapas)
 *
 * NÃO é um @Service Spring — é um domain service puro registrado via @Configuration.
 */
class DecisionEngine(
    private val contextBuilder: ContextBuilder,
    private val ruleEngine: RuleEngine
) {

    private val logger = LoggerFactory.getLogger(DecisionEngine::class.java)

    /**
     * Avalia um evento de detecção contra uma configuração de regra.
     *
     * @param event Evento de detecção do Screening Context
     * @param configuration Configuração ativa da regra
     * @param traceId Identificador de rastreamento para a explicação
     * @return DecisionResult com decisão, ações e explicação completa
     */
    fun evaluate(
        event: DetectionEvent,
        configuration: RuleConfiguration,
        traceId: TraceId
    ): DecisionResult {
        val startTime = System.currentTimeMillis()

        // Step 1: Reception
        val receptionStep = ReceptionStep(
            timestamp = Instant.now(),
            transactionId = event.transactionId,
            customerId = event.customerId,
            ruleCode = event.ruleCode,
            eventPayload = buildEventPayload(event)
        )

        // Check if configuration is active — if not, return IGNORE without evaluation
        if (!configuration.active) {
            logger.debug(
                "Configuration for ruleId={} is inactive. Returning IGNORE for transactionId={}",
                configuration.ruleId,
                event.transactionId.value
            )
            val executionTimeMs = System.currentTimeMillis() - startTime
            return buildIgnoreResultForInactiveConfig(
                event, configuration, traceId, receptionStep, executionTimeMs
            )
        }

        // Step 2: Rule Identification
        val ruleIdentificationStep = RuleIdentificationStep(
            timestamp = Instant.now(),
            ruleDefinition = event.ruleCode,
            ruleName = event.ruleCode.value,
            configurationVersion = configuration.currentVersion,
            expressions = configuration.expressions,
            actions = configuration.actions
        )

        // Identify required facts from configuration expressions (only Condition types in MVP)
        val requiredFacts = extractRequiredFacts(configuration)

        // Step 3: Build context
        val factSet = contextBuilder.buildContext(event, requiredFacts)
        val contextBuildingStep = ContextBuildingStep(
            timestamp = Instant.now(),
            resolverResults = factSet.resolverResults
        )

        // Step 4: Evaluate rules
        val ruleEvaluationResult = ruleEngine.evaluate(factSet.facts, configuration.expressions)
        val evaluationStep = EvaluationStep(
            timestamp = Instant.now(),
            evaluations = ruleEvaluationResult.evaluations
        )

        // Step 5: Determine decision
        val decision: Decision
        val actions: List<Action>
        val justification: String

        if (ruleEvaluationResult.outcome == RuleEvaluationOutcome.TRUE) {
            decision = Decision.ALERT
            actions = configuration.actions
            justification = "Todas as condições satisfeitas — ação(ões): ${actions.joinToString()}"
        } else if (ruleEvaluationResult.outcome == RuleEvaluationOutcome.INDETERMINATE) {
            decision = Decision.IGNORE
            actions = emptyList()
            justification = "Avaliação indeterminada por fatos não presentes — revisão humana requerida"
        } else {
            decision = Decision.IGNORE
            actions = emptyList()
            val failedCount = ruleEvaluationResult.evaluations.count { !it.satisfied }
            justification = "$failedCount condição(ões) não satisfeita(s) — decisão IGNORE"
        }

        val decisionStep = DecisionStep(
            timestamp = Instant.now(),
            decision = decision,
            actions = actions,
            justification = justification
        )

        // Step 6: Persistence (placeholder — real ID assigned by service)
        val persistenceStep = PersistenceStep(
            timestamp = Instant.now(),
            executionId = UUID.randomUUID()
        )

        // Step 7: Publication (placeholder)
        val publicationStep = PublicationStep(
            timestamp = Instant.now(),
            eventId = EventId(UUID.randomUUID().toString())
        )

        val executionTimeMs = System.currentTimeMillis() - startTime

        // Build explanation
        val explanation = DecisionExplanation(
            traceId = traceId,
            steps = listOf(
                receptionStep,
                ruleIdentificationStep,
                contextBuildingStep,
                evaluationStep,
                decisionStep,
                persistenceStep,
                publicationStep
            )
        )

        // Partition evaluations into matched/failed
        val matchedExpressions = ruleEvaluationResult.evaluations.filter { it.outcome == br.com.decision.domain.model.ExpressionOutcome.TRUE }
        val failedExpressions = ruleEvaluationResult.evaluations.filter { it.outcome != br.com.decision.domain.model.ExpressionOutcome.TRUE }
        val indeterminate = ruleEvaluationResult.outcome == RuleEvaluationOutcome.INDETERMINATE

        return DecisionResult(
            decision = decision,
            actions = actions,
            matchedExpressions = matchedExpressions,
            failedExpressions = failedExpressions,
            executionTimeMs = executionTimeMs,
            configurationVersion = configuration.currentVersion,
            facts = factSet.facts,
            explanation = explanation,
            factResults = factSet.factResults,
            evaluationStatus = if (indeterminate) EvaluationStatus.INDETERMINATE else EvaluationStatus.COMPLETED,
            evaluationOutcome = if (Action.GENERATE_ALERT in actions) EvaluationOutcome.SIGNAL_RAISED else EvaluationOutcome.NO_SIGNAL,
            reviewRequired = indeterminate || Action.REVIEW in actions,
            recommendedRoute = if (indeterminate || Action.REVIEW in actions) {
                RecommendedRoute.DERIVED_TO_ANALYST
            } else {
                null
            },
        )
    }

    /**
     * Extrai os FactNames requeridos das expressions da configuração.
     * No MVP, apenas Condition é utilizado — cada Condition referencia um FactName.
     */
    private fun extractRequiredFacts(configuration: RuleConfiguration): List<FactName> {
        return configuration.expressions
            .filterIsInstance<Condition>()
            .map { it.factName }
            .distinct()
    }

    /**
     * Constrói o payload do evento como mapa para a etapa de recepção.
     */
    private fun buildEventPayload(event: DetectionEvent): Map<String, Any> {
        return mapOf(
            "transactionId" to event.transactionId.value,
            "customerId" to event.customerId.value,
            "ruleCode" to event.ruleCode.value,
            "matched" to event.detectionResult.matched,
            "matchCount" to event.detectionResult.matches.size
        )
    }

    /**
     * Monta um DecisionResult de IGNORE para configuração inativa.
     * Inclui explicação mínima (7 steps) sem avaliação de fatos.
     */
    private fun buildIgnoreResultForInactiveConfig(
        event: DetectionEvent,
        configuration: RuleConfiguration,
        traceId: TraceId,
        receptionStep: ReceptionStep,
        executionTimeMs: Long
    ): DecisionResult {
        val ruleIdentificationStep = RuleIdentificationStep(
            timestamp = Instant.now(),
            ruleDefinition = event.ruleCode,
            ruleName = event.ruleCode.value,
            configurationVersion = configuration.currentVersion,
            expressions = configuration.expressions,
            actions = configuration.actions
        )

        val contextBuildingStep = ContextBuildingStep(
            timestamp = Instant.now(),
            resolverResults = emptyList()
        )

        val evaluationStep = EvaluationStep(
            timestamp = Instant.now(),
            evaluations = emptyList()
        )

        val decisionStep = DecisionStep(
            timestamp = Instant.now(),
            decision = Decision.IGNORE,
            actions = emptyList(),
            justification = "Configuração inativa — avaliação não realizada"
        )

        val persistenceStep = PersistenceStep(
            timestamp = Instant.now(),
            executionId = UUID.randomUUID()
        )

        val publicationStep = PublicationStep(
            timestamp = Instant.now(),
            eventId = EventId(UUID.randomUUID().toString())
        )

        val explanation = DecisionExplanation(
            traceId = traceId,
            steps = listOf(
                receptionStep,
                ruleIdentificationStep,
                contextBuildingStep,
                evaluationStep,
                decisionStep,
                persistenceStep,
                publicationStep
            )
        )

        return DecisionResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            executionTimeMs = executionTimeMs,
            configurationVersion = configuration.currentVersion,
            facts = emptyMap(),
            explanation = explanation
        )
    }
}
