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
* Tipo do valor de um Fact. Determina quais operadores são válidos e como o valor é interpretado. 
* Values: BOOLEAN,ENUM,MONEY,STRING,NUMBER
*/
enum class FactType(@get:JsonValue val value: kotlin.String) {

    BOOLEAN("BOOLEAN"),
    ENUM("ENUM"),
    MONEY("MONEY"),
    STRING("STRING"),
    NUMBER("NUMBER");

    companion object {
        @JvmStatic
        @JsonCreator
        fun forValue(value: kotlin.String): FactType {
                return values().first{it -> it.value == value}
        }
    }
}

