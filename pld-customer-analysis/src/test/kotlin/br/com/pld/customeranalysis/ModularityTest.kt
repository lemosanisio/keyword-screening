package br.com.pld.customeranalysis

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {

    @Test
    fun `application modules are valid`() {
        ApplicationModules.of(CustomerAnalysisApplication::class.java).verify()
    }
}
