package br.com.decision.application.service

import br.com.decision.application.usecase.ExecuteDecisionCommand
import br.com.decision.application.usecase.ExecuteDecisionUseCase
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.DecisionResult
import br.com.decision.domain.model.EvaluationStageException
import br.com.decision.domain.model.EvaluationStatus
import br.com.decision.domain.model.FailureStage
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.port.DecisionExecutionRepository
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.service.DecisionEngine
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.evaluation.domain.RiskContext
import br.com.evaluation.domain.RuleEvaluationReference
import br.com.evaluation.domain.TransactionEvaluation
import br.com.evaluation.infrastructure.SnapshotCanonicalizer
import br.com.evaluation.infrastructure.TransactionEvaluationRepository
import br.com.evaluation.infrastructure.TransactionIdentityResolver
import br.com.evaluation.infrastructure.TransactionEvaluationLock
import br.com.shared.domain.DomainEventPublisher
import br.com.shared.domain.valueobject.EventId
import br.com.shared.domain.valueobject.PrefixedUlid
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Application service que orquestra o fluxo completo de decisão.
 * Implementa ExecuteDecisionUseCase como input port.
 *
 * Responsabilidades:
 * 1. Garantir idempotência (transactionId + ruleId)
 * 2. Carregar RuleDefinition e RuleConfiguration ativa
 * 3. Invocar DecisionEngine para avaliação
 * 4. Persistir DecisionExecution (com retry para falhas de persistência)
 * 5. Publicar DecisionMadeEvent via DomainEventPublisher
 */
@Service
@Transactional
class DecisionService(
    private val decisionEngine: DecisionEngine,
    private val decisionExecutionRepository: DecisionExecutionRepository,
    private val ruleDefinitionRepository: RuleDefinitionRepository,
    private val ruleConfigurationRepository: RuleConfigurationRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val transactionIdentityResolver: TransactionIdentityResolver,
    private val snapshotCanonicalizer: SnapshotCanonicalizer,
    private val transactionEvaluationRepository: TransactionEvaluationRepository,
    private val transactionEvaluationLock: TransactionEvaluationLock,
) : ExecuteDecisionUseCase {

    private val logger = LoggerFactory.getLogger(DecisionService::class.java)

    companion object {
        private const val MAX_PERSISTENCE_RETRIES = 3
    }

    override fun execute(command: ExecuteDecisionCommand): DecisionResult {
        val correlationId = command.correlationId ?: PrefixedUlid.ulid()
        val traceId = TraceId(correlationId)
        val contractTransactionId = transactionIdentityResolver.resolve(command.sourceSystem, command.transactionId.value)
        transactionEvaluationLock.acquire("transaction", contractTransactionId, command.purpose)

        // 1. Buscar RuleDefinition pelo ruleCode
        val ruleDefinition = ruleDefinitionRepository.findByCode(command.ruleCode)
        if (ruleDefinition == null) {
            logger.debug(
                "RuleDefinition não encontrada para ruleCode={}. Retornando IGNORE.",
                command.ruleCode.value
            )
            return buildIgnoreResult()
        }

        // 2. Verificar idempotência: já existe execução para (transactionId, ruleId)?
        val existingExecution = decisionExecutionRepository.findByTransactionIdAndRuleId(
            command.transactionId,
            ruleDefinition.id
        )
        if (existingExecution != null && existingExecution.evaluationId == null) {
            logger.debug(
                "Execução já existe para transactionId={}, ruleId={}. Retornando resultado existente.",
                command.transactionId.value,
                ruleDefinition.id.value
            )
            return existingExecution.result
        }

        // 3. Buscar RuleConfiguration ativa
        val activeConfig = ruleConfigurationRepository.findActiveByRuleId(ruleDefinition.id)
        val rulesetVersion = "${ruleDefinition.code.value}:${activeConfig?.currentVersion?.value ?: 0}"
        val incomingSnapshotHash = snapshotCanonicalizer.canonicalize(evaluationSnapshot(command)).hash
        transactionEvaluationRepository.findDecisionExecutionId(
            transactionId = contractTransactionId,
            externalTransactionId = command.transactionId.value,
            sourceSystem = command.sourceSystem,
            transactionVersion = command.transactionVersion,
            rulesetVersion = rulesetVersion,
            purpose = command.purpose,
            evaluationRequestId = command.evaluationRequestId,
            inputEventId = command.inputEventId?.takeIf(INPUT_EVENT_ID_REGEX::matches),
            snapshotHash = incomingSnapshotHash,
        )?.let { executionId ->
            decisionExecutionRepository.findById(executionId)?.let { return it.result }
        }
        if (activeConfig == null) {
            logger.debug(
                "Nenhuma RuleConfiguration ativa para ruleId={}. Retornando IGNORE com persistência.",
                ruleDefinition.id.value
            )
            val ignoreResult = buildIgnoreResult()
            val evaluationId = PrefixedUlid.next("evl_")
            val execution = buildDecisionExecution(
                transactionId = command.transactionId,
                ruleId = ruleDefinition.id,
                configurationVersion = ConfigurationVersion(0),
                facts = emptyMap(),
                result = ignoreResult,
                explanation = DecisionExplanation(traceId = traceId, steps = emptyList()),
                executionTimeMs = 0L,
                traceId = traceId,
                evaluationId = evaluationId,
                partyId = command.customerId.value.takeIf(::isTypedPartyId),
                correlationId = correlationId,
                causationId = command.causationId,
            )
            val savedExecution = saveWithRetry(execution)
            val evaluation = buildTransactionEvaluation(
                command = command,
                execution = savedExecution,
                ruleCode = ruleDefinition.code.value,
                decisionResult = ignoreResult,
                contractTransactionId = contractTransactionId,
            )
            transactionEvaluationRepository.save(evaluation)
            publishDecisionMade(command, ruleDefinition.id, ruleDefinition.code, ignoreResult, savedExecution, traceId, evaluation)
            return ignoreResult
        }

        // 4. Construir DetectionEvent a partir do command
        val detectionEvent = buildDetectionEvent(command, traceId)

        // 5. Invocar DecisionEngine; falha de negócio vira avaliação FAILED persistida
        val decisionResult = try {
            decisionEngine.evaluate(detectionEvent, activeConfig, traceId)
        } catch (failure: Exception) {
            if (failure is DataAccessException) throw failure
            return handleEvaluationFailure(
                command = command,
                ruleDefinition = ruleDefinition,
                activeConfig = activeConfig,
                traceId = traceId,
                correlationId = correlationId,
                contractTransactionId = contractTransactionId,
                failure = failure,
            )
        }
        val evaluationId = PrefixedUlid.next("evl_")

        // 6. Persistir DecisionExecution (com retry 3x para DataAccessException)
        val execution = buildDecisionExecution(
            transactionId = command.transactionId,
            ruleId = ruleDefinition.id,
            configurationVersion = decisionResult.configurationVersion,
            facts = decisionResult.facts,
            result = decisionResult,
            explanation = decisionResult.explanation ?: DecisionExplanation(traceId = traceId, steps = emptyList()),
            executionTimeMs = decisionResult.executionTimeMs,
            traceId = traceId,
            evaluationId = evaluationId,
            partyId = command.customerId.value.takeIf(::isTypedPartyId),
            correlationId = correlationId,
            causationId = command.causationId,
        )
        val savedExecution = saveWithRetry(execution)
        val evaluation = buildTransactionEvaluation(
            command = command,
            execution = savedExecution,
            ruleCode = ruleDefinition.code.value,
            decisionResult = decisionResult,
            contractTransactionId = contractTransactionId,
        )
        transactionEvaluationRepository.save(evaluation)

        // 7. Publicar DecisionMadeEvent
        publishDecisionMade(command, ruleDefinition.id, ruleDefinition.code, decisionResult, savedExecution, traceId, evaluation)

        logger.info(
            "Decisão executada: transactionId={}, ruleCode={}, decision={}, traceId={}",
            command.transactionId.value,
            command.ruleCode.value,
            decisionResult.decision,
            traceId.value
        )

        return decisionResult
    }

    /**
     * Converte falha de negócio após intake/snapshot/ruleset válidos em avaliação FAILED
     * persistida e publicada. Falhas de persistência continuam propagando para retry.
     */
    private fun handleEvaluationFailure(
        command: ExecuteDecisionCommand,
        ruleDefinition: br.com.decision.domain.model.RuleDefinition,
        activeConfig: br.com.decision.domain.model.RuleConfiguration,
        traceId: TraceId,
        correlationId: String,
        contractTransactionId: String,
        failure: Exception,
    ): DecisionResult {
        val stage = (failure as? EvaluationStageException)?.stage ?: FailureStage.RULE_EVALUATION
        val failureCode = (failure.cause ?: failure)::class.simpleName ?: "UNKNOWN_FAILURE"
        logger.warn(
            "Avaliação FAILED: stage={}, code={}, transactionId={}, ruleCode={}",
            stage,
            failureCode,
            command.transactionId.value,
            command.ruleCode.value,
        )
        val evaluationId = PrefixedUlid.next("evl_")
        val failedResult = DecisionResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            executionTimeMs = 0L,
            configurationVersion = activeConfig.currentVersion,
            facts = emptyMap(),
            explanation = DecisionExplanation(traceId = traceId, steps = emptyList()),
            factResults = emptyList(),
            evaluationStatus = EvaluationStatus.FAILED,
            evaluationOutcome = br.com.decision.domain.model.EvaluationOutcome.NO_SIGNAL,
            reviewRequired = false,
            recommendedRoute = null,
        )
        val execution = buildDecisionExecution(
            transactionId = command.transactionId,
            ruleId = ruleDefinition.id,
            configurationVersion = activeConfig.currentVersion,
            facts = emptyMap(),
            result = failedResult,
            explanation = failedResult.explanation!!,
            executionTimeMs = 0L,
            traceId = traceId,
            evaluationId = evaluationId,
            partyId = command.customerId.value.takeIf(::isTypedPartyId),
            correlationId = correlationId,
            causationId = command.causationId,
        )
        val savedExecution = saveWithRetry(execution)
        val evaluation = buildTransactionEvaluation(
            command = command,
            execution = savedExecution,
            ruleCode = ruleDefinition.code.value,
            decisionResult = failedResult,
            contractTransactionId = contractTransactionId,
            failureStage = stage,
            failureCode = failureCode,
        )
        transactionEvaluationRepository.save(evaluation)
        publishDecisionMade(command, ruleDefinition.id, ruleDefinition.code, failedResult, savedExecution, traceId, evaluation)
        return failedResult
    }

    private fun publishDecisionMade(
        command: ExecuteDecisionCommand,
        ruleId: RuleId,
        ruleCode: br.com.decision.domain.model.vo.RuleCode,
        decisionResult: DecisionResult,
        savedExecution: DecisionExecution,
        traceId: TraceId,
        evaluation: TransactionEvaluation,
    ) {
        val event = DecisionMadeEvent(
            eventId = EventId(PrefixedUlid.ulid()),
            traceId = traceId,
            timestamp = evaluation.evaluatedAt,
            transactionId = command.transactionId,
            customerId = command.customerId,
            ruleId = ruleId,
            ruleCode = ruleCode,
            decision = decisionResult.decision,
            actions = decisionResult.actions,
            facts = decisionResult.facts,
            matchedExpressions = decisionResult.matchedExpressions,
            configurationVersion = decisionResult.configurationVersion,
            executionTimeMs = decisionResult.executionTimeMs,
            explanation = decisionResult.explanation ?: DecisionExplanation(traceId = traceId, steps = emptyList()),
            evaluationId = savedExecution.evaluationId,
            correlationId = savedExecution.correlationId,
            causationId = savedExecution.causationId,
            evaluation = evaluation,
        )
        domainEventPublisher.publish(event)
    }

    private fun buildTransactionEvaluation(
        command: ExecuteDecisionCommand,
        execution: DecisionExecution,
        ruleCode: String,
        decisionResult: DecisionResult,
        contractTransactionId: String,
        failureStage: FailureStage? = null,
        failureCode: String? = null,
    ): TransactionEvaluation {
        val evaluationId = requireNotNull(execution.evaluationId)
        val failed = decisionResult.evaluationStatus == EvaluationStatus.FAILED
        val snapshot = evaluationSnapshot(command)
        val canonicalSnapshot = snapshotCanonicalizer.canonicalize(snapshot)
        val riskFact = decisionResult.factResults.firstOrNull { it.name.value == "customerRisk" }
        val ruleReference = RuleEvaluationReference(
            ruleCode = ruleCode,
            ruleVersion = decisionResult.configurationVersion.value,
            explanationCode = "KEYWORD_MATCH",
        )

        return TransactionEvaluation(
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
            rulesetVersion = "$ruleCode:${decisionResult.configurationVersion.value}",
            riskContext = RiskContext(
                source = riskFact?.source ?: "LEGACY_REST",
                quality = riskFact?.quality?.name ?: "UNKNOWN",
                reasonCode = riskFact?.reasonCode ?: if (riskFact?.quality == br.com.decision.domain.model.FactQuality.PRESENT) {
                    "LEGACY_SOURCE_WITHOUT_VERSION"
                } else {
                    "RISK_NOT_EVALUATED"
                },
            ),
            facts = decisionResult.factResults,
            rulesExecuted = if (decisionResult.configurationVersion.value > 0) {
                listOf(ruleReference.copy(explanationCode = null))
            } else {
                emptyList()
            },
            rulesTriggered = if (!failed && decisionResult.evaluationOutcome == br.com.decision.domain.model.EvaluationOutcome.SIGNAL_RAISED) {
                listOf(ruleReference)
            } else {
                emptyList()
            },
            executionStatus = decisionResult.evaluationStatus,
            evaluationOutcome = decisionResult.evaluationOutcome.takeUnless { failed },
            reviewRequired = decisionResult.reviewRequired.takeUnless { failed },
            recommendedRoute = decisionResult.recommendedRoute.takeUnless { failed },
            explanation = if (failed) {
                listOf(mapOf("code" to "EVALUATION_FAILED", "detail" to (failureCode ?: "UNKNOWN_FAILURE")))
            } else {
                listOf(mapOf("code" to decisionResult.decision.name))
            },
            partyId = command.customerId.value.takeIf(::isTypedPartyId),
            correlationId = execution.correlationId ?: execution.traceId.value,
            causationId = execution.causationId,
            evaluatedAt = execution.timestamp,
            failureStage = failureStage,
            failureCode = failureCode,
            executions = listOf(br.com.evaluation.domain.ExecutionLink(execution.id, ruleCode)),
        )
    }

    private fun evaluationSnapshot(command: ExecuteDecisionCommand): Map<String, Any?> =
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

    /**
     * Persiste DecisionExecution com retry (3 tentativas) para falhas de DataAccessException.
     */
    private fun saveWithRetry(execution: DecisionExecution): DecisionExecution {
        var lastException: DataAccessException? = null

        for (attempt in 1..MAX_PERSISTENCE_RETRIES) {
            try {
                return decisionExecutionRepository.save(execution)
            } catch (e: DataAccessException) {
                lastException = e
                logger.warn(
                    "Falha ao persistir DecisionExecution (tentativa {}/{}): {}",
                    attempt,
                    MAX_PERSISTENCE_RETRIES,
                    e.message
                )
                if (attempt < MAX_PERSISTENCE_RETRIES) {
                    // Pequena pausa antes de retry
                    Thread.sleep(100L * attempt)
                }
            }
        }

        throw lastException!!
    }

    private fun buildIgnoreResult(): DecisionResult {
        return DecisionResult(
            decision = Decision.IGNORE,
            actions = emptyList(),
            matchedExpressions = emptyList(),
            failedExpressions = emptyList(),
            executionTimeMs = 0L,
            configurationVersion = ConfigurationVersion(0),
            facts = emptyMap(),
            explanation = null
        )
    }

    private fun buildDetectionEvent(command: ExecuteDecisionCommand, traceId: TraceId): DetectionEvent {
        return DetectionEvent(
            eventId = EventId(PrefixedUlid.ulid()),
            traceId = traceId,
            timestamp = Instant.now(),
            transactionId = command.transactionId,
            customerId = command.customerId,
            ruleCode = command.ruleCode,
            detectionResult = command.detectionResult,
            inputEventId = command.inputEventId,
            inputEventSchemaVersion = command.inputEventSchemaVersion,
            transactionVersion = command.transactionVersion,
            purpose = command.purpose,
            sourceSystem = command.sourceSystem,
            transactionSnapshot = command.transactionSnapshot,
            evaluationRequestId = command.evaluationRequestId,
        )
    }

    private fun buildDecisionExecution(
        transactionId: TransactionId,
        ruleId: RuleId,
        configurationVersion: ConfigurationVersion,
        facts: Map<FactName, FactValue>,
        result: DecisionResult,
        explanation: DecisionExplanation,
        executionTimeMs: Long,
        traceId: TraceId,
        evaluationId: String? = null,
        partyId: String? = null,
        correlationId: String? = null,
        causationId: String? = null,
    ): DecisionExecution {
        return DecisionExecution(
            id = UUID.randomUUID(),
            transactionId = transactionId,
            ruleId = ruleId,
            configurationVersion = configurationVersion,
            facts = facts,
            result = result,
            explanation = explanation,
            executionTimeMs = executionTimeMs,
            traceId = traceId,
            timestamp = Instant.now(),
            evaluationId = evaluationId,
            partyId = partyId,
            correlationId = correlationId,
            causationId = causationId,
        )
    }

    private fun isTypedPartyId(value: String): Boolean = PARTY_ID_REGEX.matches(value)

    private val PARTY_ID_REGEX = Regex("^pty_[0-9A-HJKMNP-TV-Z]{26}$")
    private val INPUT_EVENT_ID_REGEX = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
}
