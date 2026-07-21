package br.com.decision.application.service

import br.com.decision.application.usecase.ExecuteDecisionCommand
import br.com.decision.application.usecase.ExecuteDecisionUseCase
import br.com.decision.domain.event.DecisionMadeEvent
import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.DecisionExplanation
import br.com.decision.domain.model.DecisionResult
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
    private val domainEventPublisher: DomainEventPublisher
) : ExecuteDecisionUseCase {

    private val logger = LoggerFactory.getLogger(DecisionService::class.java)

    companion object {
        private const val MAX_PERSISTENCE_RETRIES = 3
    }

    override fun execute(command: ExecuteDecisionCommand): DecisionResult {
        val correlationId = command.correlationId ?: PrefixedUlid.ulid()
        val traceId = TraceId(correlationId)

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
        if (existingExecution != null) {
            logger.debug(
                "Execução já existe para transactionId={}, ruleId={}. Retornando resultado existente.",
                command.transactionId.value,
                ruleDefinition.id.value
            )
            return existingExecution.result
        }

        // 3. Buscar RuleConfiguration ativa
        val activeConfig = ruleConfigurationRepository.findActiveByRuleId(ruleDefinition.id)
        if (activeConfig == null) {
            logger.debug(
                "Nenhuma RuleConfiguration ativa para ruleId={}. Retornando IGNORE com persistência.",
                ruleDefinition.id.value
            )
            val ignoreResult = buildIgnoreResult()
            val execution = buildDecisionExecution(
                transactionId = command.transactionId,
                ruleId = ruleDefinition.id,
                configurationVersion = ConfigurationVersion(0),
                facts = emptyMap(),
                result = ignoreResult,
                explanation = DecisionExplanation(traceId = traceId, steps = emptyList()),
                executionTimeMs = 0L,
                traceId = traceId
            )
            saveWithRetry(execution)
            return ignoreResult
        }

        // 4. Construir DetectionEvent a partir do command
        val detectionEvent = buildDetectionEvent(command, traceId)

        // 5. Invocar DecisionEngine
        val decisionResult = decisionEngine.evaluate(detectionEvent, activeConfig, traceId)
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

        // 7. Publicar DecisionMadeEvent
        val event = DecisionMadeEvent(
            eventId = EventId(PrefixedUlid.ulid()),
            traceId = traceId,
            timestamp = Instant.now(),
            transactionId = command.transactionId,
            customerId = command.customerId,
            ruleId = ruleDefinition.id,
            ruleCode = ruleDefinition.code,
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
        )
        domainEventPublisher.publish(event)

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
            detectionResult = command.detectionResult
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
}
