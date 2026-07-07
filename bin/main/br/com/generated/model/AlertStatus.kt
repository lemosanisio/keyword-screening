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
* Status do alerta com state machine. OPEN → UNDER_REVIEW → CLOSED | FALSE_POSITIVE (terminais). 
* Values: OPEN,UNDER_REVIEW,CLOSED,FALSE_POSITIVE
*/
enum class AlertStatus(@get:JsonValue val value: kotlin.String) {

    OPEN("OPEN"),
    UNDER_REVIEW("UNDER_REVIEW"),
    CLOSED("CLOSED"),
    FALSE_POSITIVE("FALSE_POSITIVE");

    companion object {
        @JvmStatic
        @JsonCreator
        fun forValue(value: kotlin.String): AlertStatus {
                return values().first{it -> it.value == value}
        }
    }
}

