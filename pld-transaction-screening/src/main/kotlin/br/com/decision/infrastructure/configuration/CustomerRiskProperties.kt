package br.com.decision.infrastructure.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "decision.customer-risk")
data class CustomerRiskProperties(
    val url: String = "http://localhost:8081",
    val timeoutMs: Long = 5000
)
