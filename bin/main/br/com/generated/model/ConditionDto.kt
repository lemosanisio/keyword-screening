package br.com.generated.model

import java.util.Objects
import br.com.generated.model.ComparisonOperator
import br.com.generated.model.ConditionDtoExpectedValue
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
 * Expressão atômica (condição) que compara um Fact contra um valor esperado. No MVP, `type` é sempre \"CONDITION\". Groups (AND/OR compostos) serão suportados em versões futuras. 
 * @param type Tipo da expressão. MVP suporta apenas \"CONDITION\".
 * @param factName Nome do Fact a ser avaliado. Deve existir no Fact Registry e estar habilitado. Consulte `GET /v1/decision/facts` para ver fatos disponíveis. 
 * @param &#x60;operator&#x60; 
 * @param expectedValue 
 */
data class ConditionDto(

    @get:JsonProperty("type", required = true) val type: ConditionDto.Type,

    @get:JsonProperty("factName", required = true) val factName: kotlin.String,

    @field:Valid
    @get:JsonProperty("operator", required = true) val `operator`: ComparisonOperator,

    @field:Valid
    @get:JsonProperty("expectedValue", required = true) val expectedValue: ConditionDtoExpectedValue
    ) {

    /**
    * Tipo da expressão. MVP suporta apenas \"CONDITION\".
    * Values: CONDITION
    */
    enum class Type(@get:JsonValue val value: kotlin.String) {

        CONDITION("CONDITION");

        companion object {
            @JvmStatic
            @JsonCreator
            fun forValue(value: kotlin.String): Type {
                return values().first{it -> it.value == value}
            }
        }
    }

}

