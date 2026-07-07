package br.com.decision.domain.model

import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue

data class ExpressionEvaluation(
    val factName: FactName,
    val operator: ComparisonOperator,
    val expectedValue: FactValue,
    val actualValue: FactValue?,
    val satisfied: Boolean,
    val justification: String
)
