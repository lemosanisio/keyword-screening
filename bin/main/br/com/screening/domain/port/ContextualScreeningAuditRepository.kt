package br.com.screening.domain.port

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit

/**
 * Porta de persistência para registros de auditoria do Contextual Screening.
 */
interface ContextualScreeningAuditRepository {

    /**
     * Busca auditoria existente pelo par (transactionId, ruleId).
     * Usado para idempotência.
     */
    fun findByTransactionIdAndRuleId(transactionId: String, ruleId: String): ContextualScreeningAudit?

    /**
     * Persiste um novo registro de auditoria.
     * Race condition tratada via constraint UNIQUE — em caso de violação,
     * recupera e retorna o registro existente.
     */
    fun save(audit: ContextualScreeningAudit): ContextualScreeningAudit

    /**
     * Atualiza o campo analystDecision de um registro de auditoria existente.
     */
    fun updateAnalystDecision(transactionId: String, ruleId: String, decision: Classification)
}
