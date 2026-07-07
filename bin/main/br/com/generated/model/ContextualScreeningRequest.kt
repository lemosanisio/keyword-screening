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
 * Request para análise contextual de uma transação que já teve keyword match. Envia a descrição + keyword para avaliação via LLM (coaf-analyzer). 
 * @param transactionId ID da transação que teve keyword match
 * @param description Descrição completa da transação PIX
 * @param matchedKeyword Termo que foi detectado pelo Keyword Screening
 * @param ruleId Código da regra (padrão CONTEXTUAL_SCREENING se omitido)
 */
data class ContextualScreeningRequest(

    @get:JsonProperty("transactionId", required = true) val transactionId: kotlin.String,

    @get:JsonProperty("description", required = true) val description: kotlin.String,

    @get:JsonProperty("matchedKeyword", required = true) val matchedKeyword: kotlin.String,

    @get:JsonProperty("ruleId") val ruleId: kotlin.String? = null
    ) {

}

