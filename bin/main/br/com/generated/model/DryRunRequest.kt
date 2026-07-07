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
 * Request para executar dry-run com facts manuais. Os facts simulam o contexto de uma transação real. Cada fact deve ter nome e valor compatível com o Fact Registry. 
 * @param facts Mapa de facts (nome → valor) fornecidos manualmente. Tipos aceitos por fact: - keywordMatched: Boolean (true/false) - customerRisk: String enum (\"BR\", \"MR\", \"AR\") 
 */
data class DryRunRequest(

    @field:Valid
    @get:Size(min=1)
    @get:JsonProperty("facts", required = true) val facts: kotlin.collections.Map<kotlin.String, kotlin.Any>
    ) {

}

