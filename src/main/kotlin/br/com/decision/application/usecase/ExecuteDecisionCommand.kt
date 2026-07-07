package br.com.decision.application.usecase

import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId

data class ExecuteDecisionCommand(
    val transactionId: TransactionId,
    val customerId: CustomerId,
    val ruleCode: RuleCode,
    val detectionResult: DetectionResult
)
