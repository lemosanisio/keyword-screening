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
 * Resultado da análise contextual via LLM. Indica se o contexto confirma a suspeita ou é falso positivo. 
 * @param classification Classificação da LLM: - SUSPICIOUS — Contexto confirma suspeita - NOT_SUSPICIOUS — Falso positivo - INCONCLUSIVE — Requer revisão humana 
 * @param confidence Grau de confiança da classificação (0.0 a 1.0)
 * @param reason Justificativa em linguagem natural da classificação
 * @param requiresAnalystReview Se a transação precisa de revisão manual pelo analista
 */
data class ContextualScreeningResponse(

    @get:JsonProperty("classification") val classification: ContextualScreeningResponse.Classification? = null,

    @get:DecimalMin("0.0")
    @get:DecimalMax("1.0")
    @get:JsonProperty("confidence") val confidence: kotlin.Double? = null,

    @get:JsonProperty("reason") val reason: kotlin.String? = null,

    @get:JsonProperty("requiresAnalystReview") val requiresAnalystReview: kotlin.Boolean? = null
    ) {

    /**
    * Classificação da LLM: - SUSPICIOUS — Contexto confirma suspeita - NOT_SUSPICIOUS — Falso positivo - INCONCLUSIVE — Requer revisão humana 
    * Values: SUSPICIOUS,NOT_SUSPICIOUS,INCONCLUSIVE
    */
    enum class Classification(@get:JsonValue val value: kotlin.String) {

        SUSPICIOUS("SUSPICIOUS"),
        NOT_SUSPICIOUS("NOT_SUSPICIOUS"),
        INCONCLUSIVE("INCONCLUSIVE");

        companion object {
            @JvmStatic
            @JsonCreator
            fun forValue(value: kotlin.String): Classification {
                return values().first{it -> it.value == value}
            }
        }
    }

}

