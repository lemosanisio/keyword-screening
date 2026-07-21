package br.com.pld.customeranalysis.integration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pld.integration.sqs")
data class SqsIntegrationProperties(
    val enabled: Boolean = false,
    val region: String = "us-east-1",
    val endpoint: String = "",
    val accessKeyId: String = "test",
    val secretAccessKey: String = "test",
    val outboxQueueUrl: String = "",
)
