package br.com.generated.model

import java.util.Objects
import br.com.generated.model.DecisionExecutionResponse
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
 * Resultado paginado de execuções de decisão.
 * @param content Execuções na página atual
 * @param page Número da página atual (0-indexed)
 * @param propertySize Tamanho da página
 * @param totalElements Total de execuções que satisfazem o filtro
 * @param totalPages Total de páginas disponíveis
 */
data class PagedDecisionExecutionResponse(

    @field:Valid
    @get:JsonProperty("content") val content: kotlin.collections.List<DecisionExecutionResponse>? = null,

    @get:JsonProperty("page") val page: kotlin.Int? = null,

    @get:JsonProperty("size") val propertySize: kotlin.Int? = null,

    @get:JsonProperty("totalElements") val totalElements: kotlin.Long? = null,

    @get:JsonProperty("totalPages") val totalPages: kotlin.Int? = null
    ) {

}

