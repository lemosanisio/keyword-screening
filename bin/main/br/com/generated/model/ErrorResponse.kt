package br.com.generated.model

import java.util.Objects
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jakarta.validation.Valid

/**
 * Formato padronizado de erro para todos os endpoints. Inclui timestamp, status HTTP, código de erro e mensagem descritiva. 
 * @param timestamp Momento do erro (ISO-8601)
 * @param status Código HTTP do erro
 * @param error Descrição curta do tipo de erro HTTP
 * @param message Mensagem detalhada do erro (legível)
 * @param details Detalhes adicionais (ex.: campos com erro de validação)
 */
data class ErrorResponse(

    @get:JsonProperty("timestamp") val timestamp: java.time.OffsetDateTime? = null,

    @get:JsonProperty("status") val status: kotlin.Int? = null,

    @get:JsonProperty("error") val error: kotlin.String? = null,

    @get:JsonProperty("message") val message: kotlin.String? = null,

    @get:JsonProperty("details") val details: kotlin.String? = null
    ) {

}

