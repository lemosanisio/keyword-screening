package br.com.evaluation.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "transaction_identity")
class TransactionIdentityEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "source_system", nullable = false)
    val sourceSystem: String = "",
    @Column(name = "external_transaction_id", nullable = false)
    val externalTransactionId: String = "",
    @Column(name = "transaction_id", nullable = false)
    val transactionId: String = "",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
