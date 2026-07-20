package br.com.decision.domain.model.enums

/**
 * Nível de risco do cliente.
 * Ordering via ordinal: BR(0) < MR(1) < AR(2).
 */
enum class CustomerRisk {
    BR,
    MR,
    AR
}
