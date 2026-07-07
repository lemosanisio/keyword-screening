package br.com.generated.model

import java.util.Objects
import br.com.generated.model.AlertStatus
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
 * Alerta gerado automaticamente pelo Decision Engine. Criado quando decision=ALERT e action=GENERATE_ALERT. Idempotente: apenas 1 alerta por (transactionId, ruleId). 
 * @param id Identificador único do alerta
 * @param transactionId ID da transação que originou o alerta
 * @param ruleId ID da regra que gerou o alerta
 * @param customerId ID do cliente associado à transação
 * @param facts Fatos avaliados que levaram ao alerta
 * @param configurationVersion Versão da configuração que estava ativa
 * @param traceId TraceId para correlação com a DecisionExecution
 * @param actions Ações que foram executadas
 * @param explanation Dados resumidos da explicação da decisão
 * @param status 
 * @param createdAt Quando o alerta foi criado
 * @param updatedAt Última atualização de status
 */
data class AlertResponse(

    @get:JsonProperty("id") val id: java.util.UUID? = null,

    @get:JsonProperty("transactionId") val transactionId: kotlin.String? = null,

    @get:JsonProperty("ruleId") val ruleId: java.util.UUID? = null,

    @get:JsonProperty("customerId") val customerId: kotlin.String? = null,

    @field:Valid
    @get:JsonProperty("facts") val facts: kotlin.collections.Map<kotlin.String, kotlin.Any>? = null,

    @get:JsonProperty("configurationVersion") val configurationVersion: kotlin.Int? = null,

    @get:JsonProperty("traceId") val traceId: kotlin.String? = null,

    @get:JsonProperty("actions") val actions: kotlin.collections.List<kotlin.String>? = null,

    @field:Valid
    @get:JsonProperty("explanation") val explanation: kotlin.collections.Map<kotlin.String, kotlin.Any>? = null,

    @field:Valid
    @get:JsonProperty("status") val status: AlertStatus? = null,

    @get:JsonProperty("createdAt") val createdAt: java.time.OffsetDateTime? = null,

    @get:JsonProperty("updatedAt") val updatedAt: java.time.OffsetDateTime? = null
    ) {

}

