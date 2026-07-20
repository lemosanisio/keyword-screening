package br.com.decision.domain.model

import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import java.time.Instant
import java.util.UUID

/**
 * Resultado simplificado persistido no DryRunLog.
 * Representação leve para persistência e consulta — não carrega o modelo completo de ExpressionEvaluation.
 */
data class DryRunLogResult(
    val decision: Decision,
    val actions: List<Action>
)

/**
 * Registro leve de execução dry-run.
 * Usado para validar configurações antes de ativação.
 */
data class DryRunLog(
    val id: UUID,
    val configurationId: UUID,
    val version: ConfigurationVersion,
    val facts: Map<FactName, FactValue>,
    val result: DryRunLogResult,
    val executedBy: String,
    val createdAt: Instant
)
