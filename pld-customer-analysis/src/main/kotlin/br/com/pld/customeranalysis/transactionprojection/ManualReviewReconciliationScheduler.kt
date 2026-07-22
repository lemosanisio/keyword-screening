package br.com.pld.customeranalysis.transactionprojection

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Component
@ConditionalOnProperty(
    prefix = "pld.transaction-signals",
    name = ["reconciliation-enabled"],
    havingValue = "true",
)
class ManualReviewReconciliationScheduler(private val consumer: ManualReviewConsumer) {
    @Scheduled(fixedDelayString = "\${pld.transaction-signals.reconciliation-fixed-delay:PT30S}")
    fun reconcile() {
        consumer.reconcilePending()
    }
}
