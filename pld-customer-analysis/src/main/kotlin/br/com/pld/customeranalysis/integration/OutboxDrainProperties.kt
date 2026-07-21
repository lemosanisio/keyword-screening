package br.com.pld.customeranalysis.integration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pld.integration.outbox-drain")
data class OutboxDrainProperties(
    val enabled: Boolean = false,
    val initialDelay: String = "PT5S",
    val fixedDelay: String = "PT5S",
    val limit: Int = 50,
)
