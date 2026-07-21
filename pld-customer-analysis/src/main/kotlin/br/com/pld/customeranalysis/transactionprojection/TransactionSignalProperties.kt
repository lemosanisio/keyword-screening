package br.com.pld.customeranalysis.transactionprojection

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pld.transaction-signals")
data class TransactionSignalProperties(
    val caseCreationEnabled: Boolean = true,
    val acceptedProducer: String = "pld-transaction-screening",
)
