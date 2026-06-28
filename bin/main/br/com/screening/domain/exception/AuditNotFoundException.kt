package br.com.screening.domain.exception

import br.com.shared.domain.DomainException

/**
 * Lançada quando uma auditoria não é encontrada para o par (transactionId, ruleId).
 * Mapeia para HTTP 404 nos handlers de entrada.
 */
class AuditNotFoundException(
    transactionId: String,
    ruleId: String
) : DomainException(
    code = "AUDITORIA_NAO_ENCONTRADA",
    message = "Auditoria não encontrada para transactionId=$transactionId e ruleId=$ruleId"
)
