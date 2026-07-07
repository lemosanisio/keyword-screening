package br.com.generated.model

import java.util.Objects
import br.com.generated.model.Action
import br.com.generated.model.Decision
import br.com.generated.model.ExpressionEvaluationResponse
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
 * Resultado completo do dry-run mostrando exatamente o que aconteceria em produção com os facts fornecidos. Inclui detalhamento de cada expressão avaliada. 
 * @param decision 
 * @param actions Ações que seriam executadas (vazio se IGNORE)
 * @param matchedExpressions Expressões que foram satisfeitas (com justificativa)
 * @param failedExpressions Expressões que falharam (com motivo da falha)
 * @param configurationVersion Versão da configuração que foi testada
 */
data class DryRunResponse(

    @field:Valid
    @get:JsonProperty("decision") val decision: Decision? = null,

    @field:Valid
    @get:JsonProperty("actions") val actions: kotlin.collections.List<Action>? = null,

    @field:Valid
    @get:JsonProperty("matchedExpressions") val matchedExpressions: kotlin.collections.List<ExpressionEvaluationResponse>? = null,

    @field:Valid
    @get:JsonProperty("failedExpressions") val failedExpressions: kotlin.collections.List<ExpressionEvaluationResponse>? = null,

    @get:JsonProperty("configurationVersion") val configurationVersion: kotlin.Int? = null
    ) {

}

