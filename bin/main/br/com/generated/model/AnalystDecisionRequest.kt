package br.com.generated.model

import java.util.Objects
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
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
 * Request para registrar a decisão final do analista sobre uma transação.
 * @param transactionId ID da transação sendo decidida
 * @param analystDecision Decisão do analista: - APPROVE — Confirma suspeita (comunicar COAF) - REJECT — Falso positivo (não comunicar) 
 * @param ruleId Código da regra (padrão CONTEXTUAL_SCREENING se omitido)
 */
data class AnalystDecisionRequest(

    @get:JsonProperty("transactionId", required = true) val transactionId: kotlin.String,

    @get:JsonProperty("analystDecision", required = true) val analystDecision: AnalystDecisionRequest.AnalystDecision,

    @get:JsonProperty("ruleId") val ruleId: kotlin.String? = null
    ) {

    /**
    * Decisão do analista: - APPROVE — Confirma suspeita (comunicar COAF) - REJECT — Falso positivo (não comunicar) 
    * Values: APPROVE,REJECT
    */
    enum class AnalystDecision(@get:JsonValue val value: kotlin.String) {

        APPROVE("APPROVE"),
        REJECT("REJECT");

        companion object {
            @JvmStatic
            @JsonCreator
            fun forValue(value: kotlin.String): AnalystDecision {
                return values().first{it -> it.value == value}
            }
        }
    }

}

