package br.com.screening.infrastructure.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "coaf.analyzer")
data class CoafAnalyzerProperties(
    val baseUrl: String = "http://localhost:8080",
    val timeoutSeconds: Long = 30
)
