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
    APPROVE_DECISION,
}

enum class DecisionRoute {
    AUTOMATIC,
    DERIVED_TO_ANALYST,
    MANDATORY_SECOND_APPROVAL,
    TECHNICAL_RETRY,
}

enum class DecisionApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
}

enum class SuspicionDecisionValue {
    NO_SUSPICION,
    KEEP_MONITORING,
    COMMUNICATE_TO_COAF,
    INCONCLUSIVE,
}

enum class AccountDecisionValue {
    APPROVE,
    APPROVE_WITH_CONDITIONS,
    REQUEST_INFORMATION,
    REJECT,
    MAINTAIN,
    RESTRICT,
    SUSPEND,
    TERMINATE_RELATIONSHIP,
}
