package br.com.decision.domain.service

import br.com.decision.domain.model.Expression
import br.com.decision.domain.model.ExpressionEvaluation
import br.com.decision.domain.model.ExpressionOutcome
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue

/**
 * Rule Engine — avaliador puro.
 * Recebe Facts e Expressions, retorna resultado com semântica AND implícita.
 * Zero conhecimento de infraestrutura, persistência ou origem dos dados.
 * Classe pura sem Spring annotations — registrada via @Configuration.
 */
class RuleEngine(
    private val expressionEvaluator: ExpressionEvaluator
) {

    /**
     * Avalia todas as expressões contra o conjunto de facts.
     * Semântica AND implícita: allSatisfied=true iff TODAS as expressões satisfeitas.
     */
    fun evaluate(
        facts: Map<FactName, FactValue>,
        expressions: List<Expression>
    ): RuleEvaluationResult {
        val evaluations = expressions.map { expression ->
            expressionEvaluator.evaluate(expression, facts)
        }

        val outcome = when {
            evaluations.isEmpty() -> RuleEvaluationOutcome.FALSE
            evaluations.any { it.outcome == ExpressionOutcome.FALSE } -> RuleEvaluationOutcome.FALSE
            evaluations.any { it.outcome == ExpressionOutcome.INDETERMINATE } -> RuleEvaluationOutcome.INDETERMINATE
            else -> RuleEvaluationOutcome.TRUE
        }

        return RuleEvaluationResult(
            allSatisfied = outcome == RuleEvaluationOutcome.TRUE,
            outcome = outcome,
            evaluations = evaluations
        )
    }
}

data class RuleEvaluationResult(
    val allSatisfied: Boolean,
    val evaluations: List<ExpressionEvaluation>,
    val outcome: RuleEvaluationOutcome = if (allSatisfied) RuleEvaluationOutcome.TRUE else RuleEvaluationOutcome.FALSE,
)

enum class RuleEvaluationOutcome { TRUE, FALSE, INDETERMINATE }
