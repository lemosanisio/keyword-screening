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
* Contexto ao qual uma Rule Definition pertence. Identifica o domínio de avaliação da regra. 
* Values: SCREENING,TRANSACTION,CUSTOMER,ACCOUNT
*/
enum class RuleContext(@get:JsonValue val value: kotlin.String) {

    SCREENING("SCREENING"),
    TRANSACTION("TRANSACTION"),
    CUSTOMER("CUSTOMER"),
    ACCOUNT("ACCOUNT");

    companion object {
        @JvmStatic
        @JsonCreator
        fun forValue(value: kotlin.String): RuleContext {
                return values().first{it -> it.value == value}
        }
    }
}

