package br.com.screening.infrastructure.output.scheduler

import br.com.screening.application.cache.RestrictedTermsCache
import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import io.mockk.verify

class RestrictedTermsReloadSchedulerTest : StringSpec({

    "reload delegates to restrictedTermsCache.reload()" {
        val cache = mockk<RestrictedTermsCache>(relaxed = true)
        val scheduler = RestrictedTermsReloadScheduler(cache)

        scheduler.reload()

        verify(exactly = 1) { cache.reload() }
    }
})
