package br.com.pld.customeranalysis.identityaccess

data class Actor(
    val id: String,
    val role: ActorRole,
)

enum class ActorRole {
    ANALYST,
    APPROVER,
    RULE_ADMIN,
    AUDITOR,
    SYSTEM,
}
