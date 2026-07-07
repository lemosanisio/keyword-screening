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
* Resultado da avaliação do Decision Engine. - **ALERT**: Todas as condições satisfeitas → ação executada - **IGNORE**: Pelo menos uma condição falhou → sem ação - **REVIEW**: (futuro) Enviar para revisão manual - **BLOCK**: (futuro) Bloquear transação 
* Values: ALERT,IGNORE,REVIEW,BLOCK
*/
enum class Decision(@get:JsonValue val value: kotlin.String) {

    ALERT("ALERT"),
    IGNORE("IGNORE"),
    REVIEW("REVIEW"),
    BLOCK("BLOCK");

    companion object {
        @JvmStatic
        @JsonCreator
        fun forValue(value: kotlin.String): Decision {
                return values().first{it -> it.value == value}
        }
    }
}

