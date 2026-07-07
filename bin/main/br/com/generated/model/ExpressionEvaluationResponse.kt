package br.com.generated.model

import java.util.Objects
import br.com.generated.model.ComparisonOperator
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
 * Resultado da avaliação de uma expressão individual. Mostra o valor esperado vs. real e a justificativa em linguagem humana. 
 * @param factName Nome do fact avaliado
 * @param &#x60;operator&#x60; 
 * @param expectedValue Valor esperado pela expressão (conforme configurado)
 * @param actualValue Valor real encontrado no contexto (null se fact ausente)
 * @param satisfied Se a condição foi satisfeita (true) ou não (false)
 * @param justification Explicação em linguagem humana do resultado da avaliação. Ex.: \"Fact 'customerRisk' (AR) é >= MR (ordinal 2 >= 1)\" 
 */
data class ExpressionEvaluationResponse(

    @get:JsonProperty("factName") val factName: kotlin.String? = null,

    @field:Valid
    @get:JsonProperty("operator") val `operator`: ComparisonOperator? = null,

    @field:Valid
    @get:JsonProperty("expectedValue") val expectedValue: kotlin.Any? = null,

    @field:Valid
    @get:JsonProperty("actualValue") val actualValue: kotlin.Any? = null,

    @get:JsonProperty("satisfied") val satisfied: kotlin.Boolean? = null,

    @get:JsonProperty("justification") val justification: kotlin.String? = null
    ) {

}

