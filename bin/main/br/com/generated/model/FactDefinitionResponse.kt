package br.com.generated.model

import java.util.Objects
import br.com.generated.model.ComparisonOperator
import br.com.generated.model.FactType
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
 * Definição de um Fact no catálogo. Descreve um dado contextual que pode ser usado em expressões de regras. 
 * @param id Identificador único do fact
 * @param name Nome técnico do fact (usado em expressions)
 * @param displayName Nome amigável para exibição na UI
 * @param entity Entidade de negócio à qual o fact pertence
 * @param type 
 * @param context 
 * @param source Bounded context de origem dos dados
 * @param supportedOperators Operadores de comparação que este fact suporta. O analista só pode usar estes operadores ao criar expressões com este fact. 
 * @param enabled Se o fact está habilitado para uso em novas configurações
 */
data class FactDefinitionResponse(

    @get:JsonProperty("id") val id: java.util.UUID? = null,

    @get:JsonProperty("name") val name: kotlin.String? = null,

    @get:JsonProperty("displayName") val displayName: kotlin.String? = null,

    @get:JsonProperty("entity") val entity: kotlin.String? = null,

    @field:Valid
    @get:JsonProperty("type") val type: FactType? = null,

    @field:Valid
    @get:JsonProperty("context") val context: RuleContext? = null,

    @get:JsonProperty("source") val source: kotlin.String? = null,

    @field:Valid
    @get:JsonProperty("supportedOperators") val supportedOperators: kotlin.collections.List<ComparisonOperator>? = null,

    @get:JsonProperty("enabled") val enabled: kotlin.Boolean? = null
    ) {

}

