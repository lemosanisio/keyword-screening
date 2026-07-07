package br.com.decision.application.usecase

import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import java.util.UUID

data class ExecuteDryRunCommand(
    val configurationId: UUID,
    val facts: Map<FactName, FactValue>,
    val executedBy: String
)
