package br.com.generated.model

import java.util.Objects
import br.com.generated.model.AlertStatus
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
 * Request para transicionar o status de um alerta.
 * @param status 
 */
data class UpdateAlertStatusRequest(

    @field:Valid
    @get:JsonProperty("status", required = true) val status: AlertStatus
    ) {

}

