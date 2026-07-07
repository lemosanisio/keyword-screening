package br.com.generated.model

import java.util.Objects
import br.com.generated.model.Action
import br.com.generated.model.RuleCategory
import br.com.generated.model.RuleContext
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
 * Definição técnica de uma regra. Imutável para analistas. Criada e mantida pela engenharia. 
 * @param id Identificador único da regra
 * @param code Código legível da regra (ex.: KEYWORD_SCREENING). Único no sistema.
 * @param name Nome descritivo da regra
 * @param description Descrição detalhada do propósito da regra
 * @param context 
 * @param category 
 * @param supportedFacts Facts que podem ser usados em configurações desta regra. O analista só pode usar estes facts ao criar expressões. 
 * @param supportedActions Ações que esta regra suporta como resultado
 * @param status Status da regra: - ACTIVE: disponível para configuração - INACTIVE: temporariamente desabilitada - DEPRECATED: não deve ser usada em novas configs 
 * @param createdAt Data de criação da definição
 */
data class RuleDefinitionResponse(

    @get:JsonProperty("id") val id: java.util.UUID? = null,

    @get:JsonProperty("code") val code: kotlin.String? = null,

    @get:JsonProperty("name") val name: kotlin.String? = null,

    @get:JsonProperty("description") val description: kotlin.String? = null,

    @field:Valid
    @get:JsonProperty("context") val context: RuleContext? = null,

    @field:Valid
    @get:JsonProperty("category") val category: RuleCategory? = null,

    @get:JsonProperty("supportedFacts") val supportedFacts: kotlin.collections.List<kotlin.String>? = null,

    @field:Valid
    @get:JsonProperty("supportedActions") val supportedActions: kotlin.collections.List<Action>? = null,

    @get:JsonProperty("status") val status: RuleDefinitionResponse.Status? = null,

    @get:JsonProperty("createdAt") val createdAt: java.time.OffsetDateTime? = null
    ) {

    /**
    * Status da regra: - ACTIVE: disponível para configuração - INACTIVE: temporariamente desabilitada - DEPRECATED: não deve ser usada em novas configs 
    * Values: ACTIVE,INACTIVE,DEPRECATED
    */
    enum class Status(@get:JsonValue val value: kotlin.String) {

        ACTIVE("ACTIVE"),
        INACTIVE("INACTIVE"),
        DEPRECATED("DEPRECATED");

        companion object {
            @JvmStatic
            @JsonCreator
            fun forValue(value: kotlin.String): Status {
                return values().first{it -> it.value == value}
            }
        }
    }

}

