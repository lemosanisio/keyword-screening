package br.com.screening.domain.repository

import br.com.screening.domain.model.RuleExecution

interface RuleExecutionRepository {
    fun findByTransactionIdAndRuleCode(transactionId: String, ruleCode: String): RuleExecution?
    fun save(ruleExecution: RuleExecution): RuleExecution
}
