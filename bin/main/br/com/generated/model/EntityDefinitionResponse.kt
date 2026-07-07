package br.com.generated.model

import java.util.Objects
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
 * Entidade de negócio que agrupa facts. Identifica de onde vêm os dados (sourceSystem). 
 * @param id Identificador único da entidade
 * @param name Nome técnico da entidade
 * @param displayName Nome amigável para exibição
 * @param sourceSystem Sistema de origem dos dados (ex.: Cadastro, PLD, Screening)
 * @param factNames Facts que pertencem a esta entidade
 */
data class EntityDefinitionResponse(

    @get:JsonProperty("id") val id: java.util.UUID? = null,

    @get:JsonProperty("name") val name: kotlin.String? = null,

    @get:JsonProperty("displayName") val displayName: kotlin.String? = null,

    @get:JsonProperty("sourceSystem") val sourceSystem: kotlin.String? = null,

    @get:JsonProperty("factNames") val factNames: kotlin.collections.List<kotlin.String>? = null
    ) {

}

