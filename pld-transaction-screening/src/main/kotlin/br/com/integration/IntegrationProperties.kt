package br.com.integration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pld.integration.transaction-signals")
data class TransactionSignalProperties(
    val enabled: Boolean = false,
    val queueUrl: String = "",
)

@ConfigurationProperties(prefix = "pld.integration.sqs")
data class SqsProperties(
    val enabled: Boolean = false,
    val region: String = "us-east-1",
    val endpoint: String = "",
    val accessKeyId: String = "test",
    val secretAccessKey: String = "test",
)

@ConfigurationProperties(prefix = "pld.integration.outbox-drain")
data class OutboxDrainProperties(
    val enabled: Boolean = false,
    val initialDelay: String = "PT5S",
    val fixedDelay: String = "PT5S",
    val limit: Int = 50,
)
