package br.com.decision.domain.model

import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import java.time.Instant

data class RuleDefinition(
    val id: RuleId,
    val code: RuleCode,
    val name: String,
    val description: String,
    val context: RuleContext,
    val category: RuleCategory,
    val supportedFacts: List<FactName>,
    val supportedActions: List<Action>,
    val status: RuleStatus,
    val createdAt: Instant
)
