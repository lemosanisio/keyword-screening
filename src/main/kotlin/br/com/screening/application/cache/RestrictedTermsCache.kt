package br.com.screening.application.cache

import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.repository.RestrictedTermRepository
import br.com.screening.domain.service.TextNormalizer
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RestrictedTermsCache(
    private val restrictedTermRepository: RestrictedTermRepository,
    private val textNormalizer: TextNormalizer
) {
    private val log = LoggerFactory.getLogger(RestrictedTermsCache::class.java)

    @Volatile
    private var terms: Set<RestrictedTerm> = emptySet()

    @PostConstruct
    fun initialize() {
        val activeTerms = restrictedTermRepository.findAllActive()
        terms = activeTerms.map { it.copy(term = textNormalizer.normalize(it.term)) }.toSet()
        log.info("Cache inicializado com ${terms.size} termos ativos")
    }

    fun reload() {
        try {
            val activeTerms = restrictedTermRepository.findAllActive()
            terms = activeTerms.map { it.copy(term = textNormalizer.normalize(it.term)) }.toSet()
            log.info("Cache recarregado com ${terms.size} termos ativos")
        } catch (e: Exception) {
            log.error("Falha ao recarregar cache. Mantendo ${terms.size} termos anteriores.", e)
        }
    }

    fun getActiveTerms(): Set<RestrictedTerm> = terms
}
