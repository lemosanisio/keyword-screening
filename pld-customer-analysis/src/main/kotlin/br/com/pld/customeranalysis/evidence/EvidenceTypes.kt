package br.com.pld.customeranalysis.evidence

enum class EvidenceScenario {
    CLEAR,
    SOURCE_UNAVAILABLE,
    RISK_CONTEXT,
}

enum class SourceExecutionStatus {
    SUCCESS_WITH_DATA,
    SUCCESS_NO_RESULTS,
    PARTIAL,
    CONFLICT,
    UNAVAILABLE,
    ERROR,
    EXPIRED,
}

enum class FactQuality {
    PRESENT,
    UNKNOWN,
    STALE,
    ERROR,
}

enum class RequirementOutcome {
    PENDING,
    SATISFIED,
    NOT_SATISFIED,
    TECHNICAL_PENDING,
    WAIVED,
}

enum class EvidenceClassification {
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED,
}
