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
 * Request para atualizar uma configuração existente. Cada update cria uma nova versão no histórico. 
 * @param expressions Nova lista de condições (substitui as anteriores)
 * @param actions Nova lista de ações
 * @param updatedBy Identificação de quem está atualizando
 */
data class UpdateRuleConfigurationRequest(

    @field:Valid
    @get:Size(min=1,max=10) 
    @get:JsonProperty("expressions", required = true) val expressions: kotlin.collections.List<ConditionDto>,

    @field:Valid
    @get:Size(min=1)
    @get:JsonProperty("actions", required = true) val actions: kotlin.collections.List<Action>,

    @get:Size(max=100)
    @get:JsonProperty("updatedBy", required = true) val updatedBy: kotlin.String
    ) {

}

