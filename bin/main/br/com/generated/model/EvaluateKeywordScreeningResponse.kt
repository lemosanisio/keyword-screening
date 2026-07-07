package br.com.generated.model

import java.util.Objects
import br.com.generated.model.MatchResultResponse
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
 * Resultado da avaliação de Keyword Screening.
 * @param ruleCode Código da regra executada (sempre KEYWORD_SCREENING)
 * @param matched Se ao menos um termo restrito foi encontrado na descrição
 * @param matches Lista de termos restritos encontrados (vazia se matched=false)
 */
data class EvaluateKeywordScreeningResponse(

    @get:JsonProperty("ruleCode") val ruleCode: kotlin.String? = null,

    @get:JsonProperty("matched") val matched: kotlin.Boolean? = null,

    @field:Valid
    @get:JsonProperty("matches") val matches: kotlin.collections.List<MatchResultResponse>? = null
    ) {

}

