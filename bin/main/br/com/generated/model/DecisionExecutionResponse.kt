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
 * Registro imutável de uma execução do Decision Engine. Inclui todos os dados necessários para auditoria e compliance (COAF/BACEN). Nunca alterado ou deletado após criação. 
 * @param id Identificador único da execução
 * @param transactionId ID da transação PIX que originou a avaliação
 * @param ruleId ID da regra que foi avaliada
 * @param decision 
 * @param actions Ações executadas como resultado (vazio se IGNORE)
 * @param facts Fatos avaliados nesta execução (contexto completo da decisão). Mapa de factName → valor tipado. 
 * @param matchedExpressions Expressões que foram satisfeitas
 * @param failedExpressions Expressões que falharam
 * @param configurationVersion Versão da configuração que foi usada na avaliação
 * @param executionTimeMs Tempo total de execução em milissegundos
 * @param traceId Identificador de rastreamento para correlação com logs. Permite buscar esta execução pelo traceId em sistemas de observabilidade. 
 * @param timestamp Momento em que a decisão foi tomada (ISO-8601)
 */
data class DecisionExecutionResponse(

    @get:JsonProperty("id") val id: java.util.UUID? = null,

    @get:JsonProperty("transactionId") val transactionId: kotlin.String? = null,

    @get:JsonProperty("ruleId") val ruleId: java.util.UUID? = null,

    @field:Valid
    @get:JsonProperty("decision") val decision: Decision? = null,

    @field:Valid
    @get:JsonProperty("actions") val actions: kotlin.collections.List<Action>? = null,

    @field:Valid
    @get:JsonProperty("facts") val facts: kotlin.collections.Map<kotlin.String, kotlin.Any>? = null,

    @field:Valid
    @get:JsonProperty("matchedExpressions") val matchedExpressions: kotlin.collections.List<ExpressionEvaluationResponse>? = null,

    @field:Valid
    @get:JsonProperty("failedExpressions") val failedExpressions: kotlin.collections.List<ExpressionEvaluationResponse>? = null,

    @get:JsonProperty("configurationVersion") val configurationVersion: kotlin.Int? = null,

    @get:JsonProperty("executionTimeMs") val executionTimeMs: kotlin.Long? = null,

    @get:JsonProperty("traceId") val traceId: kotlin.String? = null,

    @get:JsonProperty("timestamp") val timestamp: java.time.OffsetDateTime? = null
    ) {

}

