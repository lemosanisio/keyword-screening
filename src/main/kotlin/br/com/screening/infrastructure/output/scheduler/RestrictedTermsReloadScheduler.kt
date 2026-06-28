package br.com.screening.infrastructure.output.scheduler

import br.com.screening.application.cache.RestrictedTermsCache
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RestrictedTermsReloadScheduler(
    private val restrictedTermsCache: RestrictedTermsCache
) {
    @Scheduled(fixedDelay = 300_000) // 5 minutos
    fun reload() {
        restrictedTermsCache.reload()
    }
}
