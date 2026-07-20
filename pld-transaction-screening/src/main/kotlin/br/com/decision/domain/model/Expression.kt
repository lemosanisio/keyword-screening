package br.com.decision.domain.model

import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue

sealed interface Expression

data class Condition(
    val factName: FactName,
    val operator: ComparisonOperator,
    val expectedValue: FactValue
) : Expression

data class Group(
    val logicalOperator: LogicalOperator,
    val expressions: List<Expression>
) : Expression

enum class LogicalOperator { AND, OR }
