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
* Operador de comparação para expressões. **MVP suporta:** EQUALS, NOT_EQUALS, GREATER_THAN_OR_EQUAL. Outros operadores estão modelados para evolução futura. 
* Values: EQUALS,NOT_EQUALS,GREATER_THAN,GREATER_THAN_OR_EQUAL,LESS_THAN,LESS_THAN_OR_EQUAL,IN,NOT_IN,CONTAINS
*/
enum class ComparisonOperator(@get:JsonValue val value: kotlin.String) {

    EQUALS("EQUALS"),
    NOT_EQUALS("NOT_EQUALS"),
    GREATER_THAN("GREATER_THAN"),
    GREATER_THAN_OR_EQUAL("GREATER_THAN_OR_EQUAL"),
    LESS_THAN("LESS_THAN"),
    LESS_THAN_OR_EQUAL("LESS_THAN_OR_EQUAL"),
    IN("IN"),
    NOT_IN("NOT_IN"),
    CONTAINS("CONTAINS");

    companion object {
        @JvmStatic
        @JsonCreator
        fun forValue(value: kotlin.String): ComparisonOperator {
                return values().first{it -> it.value == value}
        }
    }
}

