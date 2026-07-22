package br.com.evaluation.infrastructure

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TransactionEvaluationRepositoryTest {
    private val jpaRepository = mockk<TransactionEvaluationJpaRepository>()
    private val repository = TransactionEvaluationRepository(
        jpaRepository,
        mockk<org.springframework.jdbc.core.JdbcTemplate>(relaxed = true),
    )

    @Test
    fun `same input event rejects a different ruleset`() {
        every { jpaRepository.findByInputEventIdAndPurpose("01J6ZK7Q3W8K0M2N4P6R8T0V6A", "LIVE") } returns
            TransactionEvaluationEntity(
                transactionId = "txn_01J6ZK7Q3W8K0M2N4P6R8T0V2D",
                sourceSystem = "payments",
                externalTransactionId = "payment-1",
                transactionVersion = 1,
                purpose = "LIVE",
                inputEventId = "01J6ZK7Q3W8K0M2N4P6R8T0V6A",
                snapshotHash = "a".repeat(64),
                rulesetVersion = "keyword:1",
            )

        assertThatThrownBy {
            repository.findDecisionExecutionId(
                transactionId = "txn_01J6ZK7Q3W8K0M2N4P6R8T0V2D",
                externalTransactionId = "payment-1",
                sourceSystem = "payments",
                transactionVersion = 1,
                rulesetVersion = "keyword:2",
                purpose = "LIVE",
                evaluationRequestId = null,
                inputEventId = "01J6ZK7Q3W8K0M2N4P6R8T0V6A",
                snapshotHash = "a".repeat(64),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("conflicts with ruleset")
    }
}
