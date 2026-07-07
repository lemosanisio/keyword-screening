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
 * Confirmação de que a decisão do analista foi registrada.
 * @param transactionId ID da transação
 * @param ruleId Código da regra
 * @param analystDecision Decisão registrada (APPROVE ou REJECT)
 * @param registeredAt Momento em que a decisão foi registrada
 */
data class AnalystDecisionResponse(

    @get:JsonProperty("transactionId") val transactionId: kotlin.String? = null,

    @get:JsonProperty("ruleId") val ruleId: kotlin.String? = null,

    @get:JsonProperty("analystDecision") val analystDecision: kotlin.String? = null,

    @get:JsonProperty("registeredAt") val registeredAt: java.time.OffsetDateTime? = null
    ) {

}

