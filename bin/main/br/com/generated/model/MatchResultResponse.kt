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
 * Um termo restrito encontrado na descrição da transação.
 * @param term Termo restrito detectado (normalizado)
 * @param category Categoria do termo (TERRORISM, AML, FRAUD, FINANCIAL_CRIME, SANCTIONS)
 */
data class MatchResultResponse(

    @get:JsonProperty("term") val term: kotlin.String? = null,

    @get:JsonProperty("category") val category: MatchResultResponse.Category? = null
    ) {

    /**
    * Categoria do termo (TERRORISM, AML, FRAUD, FINANCIAL_CRIME, SANCTIONS)
    * Values: TERRORISM,AML,FRAUD,FINANCIAL_CRIME,SANCTIONS
    */
    enum class Category(@get:JsonValue val value: kotlin.String) {

        TERRORISM("TERRORISM"),
        AML("AML"),
        FRAUD("FRAUD"),
        FINANCIAL_CRIME("FINANCIAL_CRIME"),
        SANCTIONS("SANCTIONS");

        companion object {
            @JvmStatic
            @JsonCreator
            fun forValue(value: kotlin.String): Category {
                return values().first{it -> it.value == value}
            }
        }
    }

}

