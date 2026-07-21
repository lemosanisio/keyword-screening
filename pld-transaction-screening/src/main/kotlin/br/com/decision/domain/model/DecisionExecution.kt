package br.com.decision.domain.model

import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import java.time.Instant
import java.util.UUID

/**
 * Aggregate Root imutável (write-once).
 * Registro operacional de TODAS as execuções do Decision Engine.
 * Nunca modificado após criação.
 */
data class DecisionExecution(
    val id: UUID,
    val transactionId: TransactionId,
    val ruleId: RuleId,
    val configurationVersion: ConfigurationVersion,
    val facts: Map<FactName, FactValue>,
    val result: DecisionResult,
    val explanation: DecisionExplanation,
    val executionTimeMs: Long,
    val traceId: TraceId,
    val timestamp: Instant,
    val evaluationId: String? = null,
    val partyId: String? = null,
    val correlationId: String? = null,
    val causationId: String? = null,
)
