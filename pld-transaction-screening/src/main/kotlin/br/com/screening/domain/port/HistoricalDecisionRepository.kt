package br.com.screening.domain.port

import br.com.screening.domain.model.HistoricalDecision

/**
 * Porta de persistência para decisões históricas dos analistas.
 */
interface HistoricalDecisionRepository {

    /**
     * Recupera decisões históricas por keyword, ordenadas por createdAt DESC.
     * Retorna lista vazia se nenhuma decisão existir.
     */
    fun findByKeyword(keyword: String): List<HistoricalDecision>

    /**
     * Persiste uma nova decisão histórica.
     */
    fun save(decision: HistoricalDecision): HistoricalDecision
}
