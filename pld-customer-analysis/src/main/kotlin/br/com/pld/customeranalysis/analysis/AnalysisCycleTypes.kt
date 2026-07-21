package br.com.pld.customeranalysis.analysis

enum class AnalysisCycleType {
    ONBOARDING,
    PERIODIC_REVIEW,
    EVENT_DRIVEN_REVIEW,
    TRANSACTION_ALERT,
    REGULATORY_REQUEST,
    MANUAL_REVIEW,
}

enum class AnalysisCycleStatus {
    CREATED,
    COLLECTING_EVIDENCE,
    TECHNICAL_PENDING,
    READY_FOR_EVALUATION,
    AUTO_CLEARED,
    DERIVED,
    RISK_DETECTED,
    UNDER_REVIEW,
    AWAITING_APPROVAL,
    DECIDED,
    REPORTED,
    CLOSED,
}

data class AnalysisCycleId(val value: String)
