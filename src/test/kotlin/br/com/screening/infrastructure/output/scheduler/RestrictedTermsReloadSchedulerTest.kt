package br.com.screening.infrastructure.output.scheduler

import br.com.screening.application.cache.RestrictedTermsCache
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RestrictedTermsReloadSchedulerTest {

    @Test
    @DisplayName("reload delegates to restrictedTermsCache.reload()")
    fun reloadDelegatesToCache() {
        val cache = mockk<RestrictedTermsCache>(relaxed = true)
        val scheduler = RestrictedTermsReloadScheduler(cache)

        scheduler.reload()

        verify(exactly = 1) { cache.reload() }
    }
}
