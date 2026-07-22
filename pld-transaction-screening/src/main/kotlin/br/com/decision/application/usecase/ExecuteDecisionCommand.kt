package br.com.decision.application.usecase

import br.com.decision.domain.event.DetectionResult
import br.com.decision.domain.model.vo.RuleCode
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId

data class ExecuteDecisionCommand(
    val transactionId: TransactionId,
    val customerId: CustomerId,
    val ruleCode: RuleCode,
    val detectionResult: DetectionResult,
    val correlationId: String? = null,
    val causationId: String? = null,
    val inputEventId: String? = null,
    val inputEventSchemaVersion: Int = 1,
    val transactionVersion: Int = 1,
    val purpose: String = "LIVE",
    val sourceSystem: String = "SCREENING",
    val transactionSnapshot: Map<String, Any?> = emptyMap(),
    val evaluationRequestId: String? = null,
) {
    init {
        require(transactionVersion >= 1) { "transactionVersion must be positive" }
        require(inputEventSchemaVersion >= 1) { "inputEventSchemaVersion must be positive" }
        require(purpose in PURPOSES) { "Unsupported evaluation purpose: $purpose" }
        require(purpose == "LIVE" || !evaluationRequestId.isNullOrBlank()) {
            "evaluationRequestId is required for non-LIVE evaluation"
        }
    }

    companion object {
        private val PURPOSES = setOf("LIVE", "REPLAY", "BACKTEST", "DRY_RUN", "INVESTIGATION")
    }
}
