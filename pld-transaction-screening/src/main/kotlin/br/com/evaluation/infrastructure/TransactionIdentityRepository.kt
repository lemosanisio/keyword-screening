package br.com.evaluation.infrastructure

import br.com.shared.domain.valueobject.PrefixedUlid
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

interface TransactionIdentityJpaRepository : JpaRepository<TransactionIdentityEntity, UUID> {
    fun findBySourceSystemAndExternalTransactionId(sourceSystem: String, externalTransactionId: String): TransactionIdentityEntity?

    @Modifying
    @Query(
        value = """
            INSERT INTO transaction_identity (id, source_system, external_transaction_id, transaction_id, created_at)
            VALUES (:id, :sourceSystem, :externalTransactionId, :transactionId, :createdAt)
            ON CONFLICT (source_system, external_transaction_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("sourceSystem") sourceSystem: String,
        @Param("externalTransactionId") externalTransactionId: String,
        @Param("transactionId") transactionId: String,
        @Param("createdAt") createdAt: Instant,
    ): Int
}

@Component
class TransactionIdentityResolver(private val repository: TransactionIdentityJpaRepository) {
    fun resolve(sourceSystem: String, externalTransactionId: String): String {
        if (TRANSACTION_ID_REGEX.matches(externalTransactionId)) return externalTransactionId
        repository.findBySourceSystemAndExternalTransactionId(sourceSystem, externalTransactionId)?.let {
            return it.transactionId
        }
        repository.insertIfAbsent(
            id = UUID.randomUUID(),
            sourceSystem = sourceSystem,
            externalTransactionId = externalTransactionId,
            transactionId = PrefixedUlid.next("txn_"),
            createdAt = Instant.now(),
        )
        return requireNotNull(repository.findBySourceSystemAndExternalTransactionId(sourceSystem, externalTransactionId))
            .transactionId
    }

    companion object {
        private val TRANSACTION_ID_REGEX = Regex("^txn_[0-9A-HJKMNP-TV-Z]{26}$")
    }
}
