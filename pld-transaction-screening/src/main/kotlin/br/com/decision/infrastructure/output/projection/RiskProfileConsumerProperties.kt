package br.com.decision.infrastructure.output.projection

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pld.integration.risk-profile")
data class RiskProfileConsumerProperties(
    val enabled: Boolean = false,
    val queueUrl: String = "",
    val pollInterval: Long = 5000,
)
