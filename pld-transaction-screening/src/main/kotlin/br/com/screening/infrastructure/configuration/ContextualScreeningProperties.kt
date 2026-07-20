package br.com.screening.infrastructure.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "contextual-screening")
data class ContextualScreeningProperties(
    val autoCloseThreshold: Double = 0.95
)
