package br.com.shared.domain

/**
 * Exceção base para violações de regras de negócio do domínio.
 * Mapeia para HTTP 422 (Unprocessable Entity) nos handlers de entrada.
 */
abstract class DomainException(
    val code: String,
    override val message: String
) : RuntimeException(message)
