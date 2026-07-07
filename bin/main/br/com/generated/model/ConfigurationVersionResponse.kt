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
 * Snapshot de uma versão específica da configuração. Imutável após criação — representa o estado exato naquele ponto no tempo. 
 * @param version Número da versão (monotonicamente crescente, começa em 1)
 * @param expressions Expressões vigentes nesta versão
 * @param actions Ações configuradas nesta versão
 * @param active Se esta versão estava ativa no momento da criação
 * @param createdBy Quem criou esta versão
 * @param createdAt Quando esta versão foi criada
 */
data class ConfigurationVersionResponse(

    @get:JsonProperty("version") val version: kotlin.Int? = null,

    @field:Valid
    @get:JsonProperty("expressions") val expressions: kotlin.collections.List<ConditionDto>? = null,

    @field:Valid
    @get:JsonProperty("actions") val actions: kotlin.collections.List<Action>? = null,

    @get:JsonProperty("active") val active: kotlin.Boolean? = null,

    @get:JsonProperty("createdBy") val createdBy: kotlin.String? = null,

    @get:JsonProperty("createdAt") val createdAt: java.time.OffsetDateTime? = null
    ) {

}

