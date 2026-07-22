package br.com.decision.application.service

import br.com.decision.application.usecase.EvaluateRuleSetCommand
import br.com.decision.application.usecase.EvaluateRuleSetUseCase
import br.com.decision.application.usecase.RuleSetEvaluationResult
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.RuleSetEvaluatedEvent
import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.DecisionResult
import br.com.decision.domain.model.EvaluationOutcome
import br.com.decision.domain.model.EvaluationStageException
import br.com.decision.domain.model.EvaluationStatus
import br.com.decision.domain.model.FailureStage
import br.com.decision.domain.model.RecommendedRoute
import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.RuleEvaluationOutcome
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.port.DecisionExecutionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.service.DecisionEngine
import br.com.evaluation.domain.ExecutionLink
import br.com.evaluation.domain.RiskContext
import br.com.evaluation.domain.RuleEvaluationReference
import br.com.evaluation.domain.TransactionEvaluation
import br.com.evaluation.infrastructure.SnapshotCanonicalizer
import br.com.evaluation.infrastructure.TransactionEvaluationLock
import br.com.evaluation.infrastructure.TransactionEvaluationRepository
import br.com.evaluation.infrastructure.TransactionIdentityResolver
import br.com.evaluation.infrastructure.IntakeValidator
import br.com.shared.domain.DomainEventPublisher
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.PrefixedUlid
import br.com.shared.domain.valueobject.TraceId
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Avalia uma transação contra o ruleset efetivo (todas as regras ACTIVE com
 * configuração ativa), congelando a versão canônica do ruleset na avaliação.
 *
 * Produz um único aggregate TransactionEvaluation, uma DecisionExecution por regra
 * e publica RuleSetEvaluatedEvent (outbox: completion v2 + um sinal por regra
 * acionada + no máximo um pedido de revisão). DecisionMadeEvent por regra mantém
 * o workflow de Alert legado sem evaluation anexada.
 */
@Service
@Transactional
class RuleSetEvaluationService(
    private val ruleDefinitionRepository: RuleDefinitionRepository,
    private val ruleConfigurationRepository: RuleConfigurationRepository,
    private val decisionEngine: DecisionEngine,
    private val decisionExecutionRepository: DecisionExecutionRepository,
    private val transactionIdentityResolver: TransactionIdentityResolver,
    private val snapshotCanonicalizer: SnapshotCanonicalizer,
    private val transactionEvaluationRepository: TransactionEvaluationRepository,
    private val transactionEvaluationLock: TransactionEvaluationLock,
    private val domainEventPublisher: DomainEventPublisher,
    private val intakeValidator: IntakeValidator,
) : EvaluateRuleSetUseCase {

    private val logger = LoggerFactory.getLogger(RuleSetEvaluationService::class.java)

    override fun execute(command: EvaluateRuleSetCommand): RuleSetEvaluationResult {
        val correlationId = command.correlationId ?: PrefixedUlid.ulid()
        val traceId = TraceId(correlationId)
        val contractTransactionId = transactionIdentityResolver.resolve(command.sourceSystem, command.transactionId.value)
        transactionEvaluationLock.acquire("transaction", contractTransactionId, command.purpose)

        // Validar intake antes de criar aggregate
        val intakeResult = intakeValidator.validate(
            IntakeValidator.IntakeInput(
                sourceSystem = command.sourceSystem,
                externalTransactionId = command.transactionId.value,
                transactionVersion = command.transactionVersion,
                purpose = command.purpose,
                snapshot = evaluationSnapshot(command),
                correlationId = correlationId,
            )
        )
        if (intakeResult is IntakeValidator.IntakeResult.Quarantined) {
            logger.warn("Intake quarantined: {}", intakeResult.reasonDetail)
            return RuleSetEvaluationResult(null, null, null, null, null, null, emptyList())
        }

        val rules = activeRules()
        if (rules.isEmpty()) {
            logger.debug("Nenhuma regra ativa com configuração ativa; nada a avaliar")
            return RuleSetEvaluationResult(null, null, null, null, null, null, emptyList())
        }
        val rulesetVersion = rules.joinToString(";") { (definition, config) ->
            "${definition.code.value}:${config.currentVersion.value}"
        }
        val snapshotHash = snapshotCanonicalizer.canonicalize(evaluationSnapshot(command)).hash

        transactionEvaluationRepository.findDecisionExecutionId(
            transactionId = contractTransactionId,
            externalTransactionId = command.transactionId.value,
            sourceSystem = command.sourceSystem,
            transactionVersion = command.transactionVersion,
            rulesetVersion = rulesetVersion,
            purpose = command.purpose,
            evaluationRequestId = command.evaluationRequestId,
            inputEventId = command.inputEventId?.takeIf(INPUT_EVENT_ID_REGEX::matches),
            snapshotHash = snapshotHash,
        )?.let { existingExecutionId ->
            decisionExecutionRepository.findById(existingExecutionId)?.evaluationId?.let { evaluationId ->
                return replayResult(evaluationId, rulesetVersion, rules)
            }
        }

        val evaluationId = PrefixedUlid.next("evl_")
        val startedAt = System.currentTimeMillis()
        val outcomes = mutableListOf<Pair<RuleDefinition, DecisionResult>>()
        val executions = mutableListOf<DecisionExecution>()

        for ((definition, config) in rules) {
            val result = try {
                decisionEngine.evaluate(buildDetectionEvent(command, definition, traceId), config, traceId)
            } catch (failure: Exception) {
                if (failure is DataAccessException) throw failure
                return handleFailure(
                    command, rules, definition, config, traceId, correlationId,
                    contractTransactionId, evaluationId, rulesetVersion, failure,
                )
            }
            val execution = decisionExecutionRepository.save(
                DecisionExecution(
                    id = UUID.randomUUID(),
                    transactionId = command.transactionId,
                    ruleId = definition.id,
                    configurationVersion = result.configurationVersion,
                    facts = result.facts,
                    result = result,
                    explanation = result.explanation ?: DecisionExplanation(traceId = traceId, steps = emptyList()),
                    executionTimeMs = result.executionTimeMs,
                    traceId = traceId,
                    timestamp = Instant.now(),
                    evaluationId = evaluationId,
                    partyId = command.customerId.value.takeIf(::isTypedPartyId),
                    correlationId = correlationId,
                    causationId = command.causationId,
                ),
            )
            outcomes += definition to result
            executions += execution
        }

        val evaluation = buildAggregateEvaluation(
            command = command,
            evaluationId = evaluationId,
            contractTransactionId = contractTransactionId,
            rulesetVersion = rulesetVersion,
            outcomes = outcomes,
            executions = executions,
            correlationId = correlationId,
        )
        transactionEvaluationRepository.save(evaluation)

        val latencyMs = System.currentTimeMillis() - startedAt
        domainEventPublisher.publish(
            RuleSetEvaluatedEvent(
                eventId = EventId(PrefixedUlid.ulid()),
                traceId = traceId,
                timestamp = evaluation.evaluatedAt,
                evaluation = evaluation,
                ruleOutcomes = outcomes.map(::toOutcome),
                correlationId = correlationId,
                causationId = command.causationId,
                executionTimeMs = latencyMs,
            ),
        )
        outcomes.forEach { (definition, result) ->
            publishLegacyDecisionMade(command, definition, result, traceId, correlationId)
        }

        return RuleSetEvaluationResult(
            evaluationId = evaluationId,
            executionStatus = evaluation.executionStatus,
            evaluationOutcome = evaluation.evaluationOutcome,
            reviewRequired = evaluation.reviewRequired,
            recommendedRoute = evaluation.recommendedRoute,
            rulesetVersion = rulesetVersion,
            ruleOutcomes = outcomes.map(::toOutcome),
        )
    }

    private fun activeRules(): List<Pair<RuleDefinition, RuleConfiguration>> =
        ruleDefinitionRepository.findAll()
            .filter { it.status == RuleStatus.ACTIVE }
            .mapNotNull { definition ->
                ruleConfigurationRepository.findActiveByRuleId(definition.id)?.let { definition to it }
            }
            .sortedBy { it.first.code.value }

    private fun replayResult(
        evaluationId: String,
        rulesetVersion: String,
        rules: List<Pair<RuleDefinition, RuleConfiguration>>,
    ): RuleSetEvaluationResult {
        val executions = decisionExecutionRepository.findByEvaluationId(evaluationId)
        val byRuleId = rules.associate { it.first.id to it.first }
        val outcomes = executions.mapNotNull { execution ->
            val definition = byRuleId[execution.ruleId] ?: return@mapNotNull null
            definition to execution.result
        }
        val status = when {
            executions.any { it.result.evaluationStatus == EvaluationStatus.FAILED } -> EvaluationStatus.FAILED
            executions.any { it.result.evaluationStatus == EvaluationStatus.INDETERMINATE } -> EvaluationStatus.INDETERMINATE
            else -> EvaluationStatus.COMPLETED
        }
        val signalRaised = outcomes.any { it.second.evaluationOutcome == EvaluationOutcome.SIGNAL_RAISED }
        val reviewRequired = outcomes.any { it.second.reviewRequired }
        return RuleSetEvaluationResult(
            evaluationId = evaluationId,
            executionStatus = status,
            evaluationOutcome = if (status == EvaluationStatus.FAILED) {
                null
            } else if (signalRaised) {
                EvaluationOutcome.SIGNAL_RAISED
            } else {
                EvaluationOutcome.NO_SIGNAL
            },
            reviewRequired = (reviewRequired || status == EvaluationStatus.INDETERMINATE).takeUnless {
                status == EvaluationStatus.FAILED
            },
            recommendedRoute = RecommendedRoute.DERIVED_TO_ANALYST.takeUnless {
                status == EvaluationStatus.FAILED || !(reviewRequired || status == EvaluationStatus.INDETERMINATE)
            },
            rulesetVersion = rulesetVersion,
            ruleOutcomes = outcomes.map(::toOutcome),
        )
    }

    private fun handleFailure(
        command: EvaluateRuleSetCommand,
        rules: List<Pair<RuleDefinition, RuleConfiguration>>,
        failedDefinition: RuleDefinition,
        failedConfig: RuleConfiguration,
        traceId: TraceId,
        correlationId: String,
        contractTransactionId: String,
        evaluationId: String,
        rulesetVersion: String,
        failure: Exception,
    ): RuleSetEvaluationResult {
        val stage = (failure as? EvaluationStageException)?.stage ?: FailureStage.RULE_EVALUATION
        val failureCode = (failure.cause ?: failure)::class.simpleName ?: "UNKNOWN_FAILURE"
        logger.warn(
            "Avaliação de ruleset FAILED: stage={}, code={}, transactionId={}, ruleCode={}",
            stage, failureCode, command.transactionId.value, failedDefinition.code.value,
        )
        val failedResult = DecisionResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            executionTimeMs = 0L,
            configurationVersion = failedConfig.currentVersion,
            facts = emptyMap(),
            explanation = DecisionExplanation(traceId = traceId, steps = emptyList()),
            factResults = emptyList(),
            evaluationStatus = EvaluationStatus.FAILED,
            evaluationOutcome = EvaluationOutcome.NO_SIGNAL,
            reviewRequired = false,
            recommendedRoute = null,
        )
        val execution = decisionExecutionRepository.save(
            DecisionExecution(
                id = UUID.randomUUID(),
                transactionId = command.transactionId,
                ruleId = failedDefinition.id,
                configurationVersion = failedConfig.currentVersion,
                facts = emptyMap(),
                result = failedResult,
                explanation = failedResult.explanation!!,
                executionTimeMs = 0L,
                traceId = traceId,
                timestamp = Instant.now(),
                evaluationId = evaluationId,
                partyId = command.customerId.value.takeIf(::isTypedPartyId),
                correlationId = correlationId,
                causationId = command.causationId,
            ),
        )
        val snapshot = evaluationSnapshot(command)
        val canonicalSnapshot = snapshotCanonicalizer.canonicalize(snapshot)
        val evaluation = TransactionEvaluation(
            evaluationId = evaluationId,
            decisionExecutionId = execution.id,
            transactionId = contractTransactionId,
            sourceSystem = command.sourceSystem,
            externalTransactionId = command.transactionId.value,
            transactionVersion = command.transactionVersion,
            purpose = command.purpose,
            evaluationRequestId = command.evaluationRequestId,
            inputEventId = command.inputEventId?.takeIf(INPUT_EVENT_ID_REGEX::matches) ?: PrefixedUlid.ulid(),
            inputEventSchemaVersion = command.inputEventSchemaVersion,
            snapshot = snapshot,
            snapshotRef = PrefixedUlid.next("snp_"),
            snapshotFormatVersion = "transaction-snapshot-v1",
            snapshotHash = canonicalSnapshot.hash,
            rulesetVersion = rulesetVersion,
            riskContext = RiskContext(
                source = "LEGACY_REST",
                quality = "UNKNOWN",
                reasonCode = "RISK_NOT_EVALUATED",
            ),
            facts = emptyList(),
            rulesExecuted = rules.map { (definition, config) ->
                RuleEvaluationReference(definition.code.value, config.currentVersion.value)
            },
            rulesTriggered = emptyList(),
            executionStatus = EvaluationStatus.FAILED,
            evaluationOutcome = null,
            reviewRequired = null,
            recommendedRoute = null,
            explanation = listOf(mapOf("code" to "EVALUATION_FAILED", "detail" to failureCode)),
            partyId = command.customerId.value.takeIf(::isTypedPartyId),
            correlationId = correlationId,
            causationId = command.causationId,
            evaluatedAt = execution.timestamp,
            failureStage = stage,
            failureCode = failureCode,
            executions = listOf(ExecutionLink(execution.id, failedDefinition.code.value)),
        )
        transactionEvaluationRepository.save(evaluation)
        domainEventPublisher.publish(
            RuleSetEvaluatedEvent(
                eventId = EventId(PrefixedUlid.ulid()),
                traceId = traceId,
                timestamp = evaluation.evaluatedAt,
                evaluation = evaluation,
                ruleOutcomes = emptyList(),
                correlationId = correlationId,
                causationId = command.causationId,
                executionTimeMs = 0L,
            ),
        )
        return RuleSetEvaluationResult(
            evaluationId = evaluationId,
            executionStatus = EvaluationStatus.FAILED,
            evaluationOutcome = null,
            reviewRequired = null,
            recommendedRoute = null,
            rulesetVersion = rulesetVersion,
            ruleOutcomes = emptyList(),
        )
    }

    private fun buildAggregateEvaluation(
        command: EvaluateRuleSetCommand,
        evaluationId: String,
        contractTransactionId: String,
        rulesetVersion: String,
        outcomes: List<Pair<RuleDefinition, DecisionResult>>,
        executions: List<DecisionExecution>,
        correlationId: String,
    ): TransactionEvaluation {
        val snapshot = evaluationSnapshot(command)
        val canonicalSnapshot = snapshotCanonicalizer.canonicalize(snapshot)
        val indeterminate = outcomes.any { it.second.evaluationStatus == EvaluationStatus.INDETERMINATE }
        val triggered = outcomes.filter { it.second.evaluationOutcome == EvaluationOutcome.SIGNAL_RAISED }
        val reviewRequired = indeterminate || outcomes.any { it.second.reviewRequired }
        val riskFact = outcomes.asSequence()
            .flatMap { it.second.factResults }
            .firstOrNull { it.name.value == "customerRisk" }
        val facts = outcomes.flatMap { it.second.factResults }.distinctBy { it.name.value }
        val primary = executions.first()

        return TransactionEvaluation(
            evaluationId = evaluationId,
            decisionExecutionId = primary.id,
            transactionId = contractTransactionId,
            sourceSystem = command.sourceSystem,
            externalTransactionId = command.transactionId.value,
            transactionVersion = command.transactionVersion,
            purpose = command.purpose,
            evaluationRequestId = command.evaluationRequestId,
            inputEventId = command.inputEventId?.takeIf(INPUT_EVENT_ID_REGEX::matches) ?: PrefixedUlid.ulid(),
            inputEventSchemaVersion = command.inputEventSchemaVersion,
            snapshot = snapshot,
            snapshotRef = PrefixedUlid.next("snp_"),
            snapshotFormatVersion = "transaction-snapshot-v1",
            snapshotHash = canonicalSnapshot.hash,
            rulesetVersion = rulesetVersion,
            riskContext = RiskContext(
                source = riskFact?.source ?: "LEGACY_REST",
                quality = riskFact?.quality?.name ?: "UNKNOWN",
                reasonCode = riskFact?.reasonCode ?: "RISK_NOT_EVALUATED",
            ),
            facts = facts,
            rulesExecuted = outcomes.map { (definition, result) ->
                RuleEvaluationReference(definition.code.value, result.configurationVersion.value)
            },
            rulesTriggered = triggered.map { (definition, result) ->
                RuleEvaluationReference(definition.code.value, result.configurationVersion.value, "KEYWORD_MATCH")
            },
            executionStatus = if (indeterminate) EvaluationStatus.INDETERMINATE else EvaluationStatus.COMPLETED,
            evaluationOutcome = if (triggered.isNotEmpty()) EvaluationOutcome.SIGNAL_RAISED else EvaluationOutcome.NO_SIGNAL,
            reviewRequired = reviewRequired,
            recommendedRoute = if (reviewRequired) RecommendedRoute.DERIVED_TO_ANALYST else null,
            explanation = outcomes.map { (definition, result) ->
                mapOf("code" to "${definition.code.value}:${result.decision.name}")
            },
            partyId = command.customerId.value.takeIf(::isTypedPartyId),
            correlationId = correlationId,
            causationId = command.causationId,
            evaluatedAt = primary.timestamp,
            executions = executions.mapIndexed { index, execution ->
                ExecutionLink(execution.id, outcomes[index].first.code.value)
            },
        )
    }

    private fun buildDetectionEvent(
        command: EvaluateRuleSetCommand,
        definition: RuleDefinition,
        traceId: TraceId,
    ): DetectionEvent = DetectionEvent(
        eventId = EventId(PrefixedUlid.ulid()),
        traceId = traceId,
        timestamp = Instant.now(),
        transactionId = command.transactionId,
        customerId = command.customerId,
        ruleCode = definition.code,
        detectionResult = command.detectionResult,
        inputEventId = command.inputEventId,
        inputEventSchemaVersion = command.inputEventSchemaVersion,
        transactionVersion = command.transactionVersion,
        purpose = command.purpose,
        sourceSystem = command.sourceSystem,
        transactionSnapshot = command.transactionSnapshot,
        evaluationRequestId = command.evaluationRequestId,
    )

    private fun publishLegacyDecisionMade(
        command: EvaluateRuleSetCommand,
        definition: RuleDefinition,
        result: DecisionResult,
        traceId: TraceId,
        correlationId: String,
    ) {
        domainEventPublisher.publish(
            DecisionMadeEvent(
                eventId = EventId(PrefixedUlid.ulid()),
                traceId = traceId,
                timestamp = Instant.now(),
                transactionId = command.transactionId,
                customerId = command.customerId,
                ruleId = definition.id,
                ruleCode = definition.code,
                decision = result.decision,
                actions = result.actions,
                facts = result.facts,
                matchedExpressions = result.matchedExpressions,
                configurationVersion = result.configurationVersion,
                executionTimeMs = result.executionTimeMs,
                explanation = result.explanation ?: DecisionExplanation(traceId = traceId, steps = emptyList()),
                evaluationId = null,
                correlationId = correlationId,
                causationId = command.causationId,
                evaluation = null,
            ),
        )
    }

    private fun toOutcome(pair: Pair<RuleDefinition, DecisionResult>): RuleEvaluationOutcome {
        val (definition, result) = pair
        return RuleEvaluationOutcome(
            ruleCode = definition.code.value,
            ruleVersion = result.configurationVersion.value,
            decision = result.decision,
            signalRaised = result.evaluationOutcome == EvaluationOutcome.SIGNAL_RAISED,
            reviewRequired = result.reviewRequired,
        )
    }

    private fun evaluationSnapshot(command: EvaluateRuleSetCommand): Map<String, Any?> =
        command.transactionSnapshot.ifEmpty {
            mapOf(
                "sourceSystem" to command.sourceSystem,
                "externalTransactionId" to command.transactionId.value,
                "customerId" to command.customerId.value,
                "detectionMatched" to command.detectionResult.matched,
                "matches" to command.detectionResult.matches.map { match ->
                    mapOf("term" to match.term, "category" to match.category)
                },
            )
        }

    private fun isTypedPartyId(value: String): Boolean = PARTY_ID_REGEX.matches(value)

    companion object {
        private val PARTY_ID_REGEX = Regex("^pty_[0-9A-HJKMNP-TV-Z]{26}$")
        private val INPUT_EVENT_ID_REGEX = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
    }
}
