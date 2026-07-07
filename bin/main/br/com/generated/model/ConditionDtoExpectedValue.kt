package br.com.generated.model

import java.util.Objects
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
 * Valor esperado para a comparação. O tipo deve ser compatível com o `type` do Fact: - BOOLEAN → `true` ou `false` - ENUM → String com valor do enum (ex.: \"MR\", \"AR\", \"BR\") - NUMBER → Número (ex.: 1000.50) - STRING → String - MONEY → Objeto `{\"amount\": 1000.00, \"currency\": \"BRL\"}` 
 */
class ConditionDtoExpectedValue(

    ) {

}

