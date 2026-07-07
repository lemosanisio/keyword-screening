package br.com.generated.model

import java.util.Objects
import br.com.generated.model.Action
import br.com.generated.model.ConditionDto
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
 * Configuração de regra criada/gerenciada pelo analista. Contém as expressões de decisão e ações a serem executadas. 
 * @param id Identificador único da configuração
 * @param ruleCode Código da regra à qual esta configuração pertence
 * @param version Versão atual da configuração (incrementa a cada update)
 * @param active Se a configuração está ativa (avaliando transações em produção). Apenas uma configuração pode estar ativa por regra. 
 * @param draft Se a configuração está em rascunho (nunca foi ativada). draft=true e active=false → configuração recém-criada. 
 * @param expressions Lista de condições (AND implícito entre todas)
 * @param actions Ações a executar quando todas condições forem satisfeitas
 * @param createdBy Quem criou esta configuração
 * @param createdAt Data de criação
 * @param updatedAt Data da última atualização (update, activate ou deactivate)
 */
data class RuleConfigurationResponse(

    @get:JsonProperty("id") val id: java.util.UUID? = null,

    @get:JsonProperty("ruleCode") val ruleCode: kotlin.String? = null,

    @get:JsonProperty("version") val version: kotlin.Int? = null,

    @get:JsonProperty("active") val active: kotlin.Boolean? = null,

    @get:JsonProperty("draft") val draft: kotlin.Boolean? = null,

    @field:Valid
    @get:JsonProperty("expressions") val expressions: kotlin.collections.List<ConditionDto>? = null,

    @field:Valid
    @get:JsonProperty("actions") val actions: kotlin.collections.List<Action>? = null,

    @get:JsonProperty("createdBy") val createdBy: kotlin.String? = null,

    @get:JsonProperty("createdAt") val createdAt: java.time.OffsetDateTime? = null,

    @get:JsonProperty("updatedAt") val updatedAt: java.time.OffsetDateTime? = null
    ) {

}

