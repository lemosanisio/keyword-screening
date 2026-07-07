package br.com.generated.model

import java.util.Objects
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.JsonCreator
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
* Categoria funcional de uma regra. Facilita filtros na UI e agrupamento lógico. 
* Values: KEYWORD_SCREENING,SANCTIONS,AML,FRAUD,VELOCITY
*/
enum class RuleCategory(@get:JsonValue val value: kotlin.String) {

    KEYWORD_SCREENING("KEYWORD_SCREENING"),
    SANCTIONS("SANCTIONS"),
    AML("AML"),
    FRAUD("FRAUD"),
    VELOCITY("VELOCITY");

    companion object {
        @JvmStatic
        @JsonCreator
        fun forValue(value: kotlin.String): RuleCategory {
                return values().first{it -> it.value == value}
        }
    }
}

