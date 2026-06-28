package br.com.screening.domain.model

import java.time.Instant

data class RestrictedTerm(
    val id: Long,
    val term: String,
    val category: Category,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
