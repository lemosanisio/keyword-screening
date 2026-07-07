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
 * Request para criar uma nova configuração de regra em estado draft.
 * @param expressions Lista de condições (semântica AND implícita). Todas devem ser satisfeitas para que a decisão seja ALERT. Mínimo 1, máximo 10. 
 * @param actions Ações a executar quando TODAS as condições forem satisfeitas. No MVP: GENERATE_ALERT (cria alerta) ou IGNORE. 
 * @param createdBy Identificação de quem está criando (email ou username)
 */
data class CreateRuleConfigurationRequest(

    @field:Valid
    @get:Size(min=1,max=10) 
    @get:JsonProperty("expressions", required = true) val expressions: kotlin.collections.List<ConditionDto>,

    @field:Valid
    @get:Size(min=1)
    @get:JsonProperty("actions", required = true) val actions: kotlin.collections.List<Action>,

    @get:Size(max=100)
    @get:JsonProperty("createdBy", required = true) val createdBy: kotlin.String
    ) {

}

