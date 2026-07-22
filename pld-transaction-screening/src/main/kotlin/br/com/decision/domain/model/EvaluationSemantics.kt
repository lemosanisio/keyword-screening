package br.com.decision.domain.model

import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue

enum class FactQuality { PRESENT, UNKNOWN, STALE, ERROR }

data class FactResult(
    val name: FactName,
    val quality: FactQuality,
    val value: FactValue? = null,
    val source: String,
    val reasonCode: String? = null,
) {
    init {
        require(quality == FactQuality.PRESENT || !reasonCode.isNullOrBlank()) {
            "reasonCode é obrigatório para fato não presente"
        }
        require(quality != FactQuality.PRESENT || value != null) {
            "value é obrigatório para fato presente"
        }
    }
}

enum class ExpressionOutcome { TRUE, FALSE, INDETERMINATE }
enum class EvaluationStatus { COMPLETED, INDETERMINATE, FAILED }
enum class EvaluationOutcome { NO_SIGNAL, SIGNAL_RAISED }
enum class RecommendedRoute { DERIVED_TO_ANALYST, MANDATORY_SECOND_APPROVAL, TECHNICAL_RETRY }
