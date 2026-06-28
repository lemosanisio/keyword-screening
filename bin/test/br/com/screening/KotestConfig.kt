package br.com.screening

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.property.PropertyTesting

object KotestConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        PropertyTesting.defaultIterationCount = 1000
    }
}
