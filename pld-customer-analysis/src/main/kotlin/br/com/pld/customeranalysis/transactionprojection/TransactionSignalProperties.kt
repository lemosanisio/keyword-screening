package br.com.pld.customeranalysis.transactionprojection

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

@ConfigurationProperties(prefix = "pld.transaction-signals")
data class TransactionSignalProperties(
    val caseTriggerMode: CaseTriggerMode = CaseTriggerMode.LEGACY,
    val caseCreationEnabled: Boolean = true,
    val acceptedProducer: String = "pld-transaction-screening",
    val manualReviewCutoverAt: Instant = Instant.parse("9999-12-31T23:59:59Z"),
)

enum class CaseTriggerMode { LEGACY, SHADOW, MANUAL_REVIEW_LIVE }
