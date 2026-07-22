package br.com.pld.customeranalysis.evidence

import org.springframework.stereotype.Component

/**
 * Registry que descobre adapters por sourceCode.
 * Adapters são auto-registrados via Spring (todos os beans de EvidenceSourceAdapter).
 */
@Component
class EvidenceSourceRegistry(adapters: List<EvidenceSourceAdapter>) {

    private val byCode: Map<String, EvidenceSourceAdapter> = adapters.associateBy { it.sourceCode }

    fun find(sourceCode: String): EvidenceSourceAdapter? = byCode[sourceCode]

    fun all(): Collection<EvidenceSourceAdapter> = byCode.values

    fun availableCodes(): Set<String> = byCode.keys
}
