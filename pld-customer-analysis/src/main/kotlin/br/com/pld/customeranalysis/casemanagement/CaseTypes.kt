package br.com.pld.customeranalysis.casemanagement

enum class CaseOrigin {
    TRANSACTION_ALERT,
}

enum class CaseStatus {
    OPEN,
    ASSIGNED,
    IN_ANALYSIS,
    WAITING_INFORMATION,
    WAITING_TECHNICAL,
    PENDING_APPROVAL,
    DECIDED,
    CLOSED,
    CANCELLED_AS_DUPLICATE,
}

enum class CasePriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class CaseAction {
    ASSIGN,
    START_ANALYSIS,
    RETURN_TO_QUEUE,
}

enum class SuspicionDecisionValue {
    NO_SUSPICION,
    KEEP_MONITORING,
    COMMUNICATE_TO_COAF,
    INCONCLUSIVE,
}
