package br.com.screening.domain.repository

import br.com.screening.domain.model.RuleExecution
import br.com.shared.domain.valueobject.TransactionId

interface RuleExecutionRepository {
    fun findByTransactionIdAndRuleCode(transactionId: TransactionId, ruleCode: String): RuleExecution?
    fun save(ruleExecution: RuleExecution): RuleExecution
}
