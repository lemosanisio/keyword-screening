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
* Ação a ser executada quando a decisão é ALERT. - **GENERATE_ALERT**: Cria alerta no Alert Context - **IGNORE**: Nenhuma ação (apenas registro) - **REVIEW**: (futuro) Enviar para revisão - **BLOCK**: (futuro) Bloquear 
* Values: GENERATE_ALERT,IGNORE,REVIEW,BLOCK
*/
enum class Action(@get:JsonValue val value: kotlin.String) {

    GENERATE_ALERT("GENERATE_ALERT"),
    IGNORE("IGNORE"),
    REVIEW("REVIEW"),
    BLOCK("BLOCK");

    companion object {
        @JvmStatic
        @JsonCreator
        fun forValue(value: kotlin.String): Action {
                return values().first{it -> it.value == value}
        }
    }
}

