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
 * Request para avaliar uma transação PIX na regra MF09 de Keyword Screening. O serviço normaliza a descrição e busca termos restritos cadastrados. 
 * @param transactionId Identificador único da transação PIX
 * @param customerId Identificador do cliente (máx. 64 caracteres)
 * @param description Descrição da transação PIX (máx. 140 caracteres)
 */
data class EvaluateKeywordScreeningRequest(

    @get:Size(min=1)
    @get:JsonProperty("transactionId", required = true) val transactionId: kotlin.String,

    @get:Size(min=1,max=64)
    @get:JsonProperty("customerId", required = true) val customerId: kotlin.String,

    @get:Size(min=1,max=140)
    @get:JsonProperty("description", required = true) val description: kotlin.String
    ) {

}

