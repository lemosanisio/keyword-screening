package br.com.alert.infrastructure.input.http

import br.com.alert.application.usecase.QueryAlertUseCase
import br.com.alert.application.usecase.UpdateAlertStatusUseCase
import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.model.vo.AlertId
import br.com.alert.infrastructure.input.http.dto.AlertResponse
import br.com.alert.infrastructure.input.http.dto.UpdateAlertStatusRequest
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TransactionId
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/alerts")
class AlertController(
    private val queryAlertUseCase: QueryAlertUseCase,
    private val updateAlertStatusUseCase: UpdateAlertStatusUseCase
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) transactionId: String?,
        @RequestParam(required = false) ruleId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        return when {
            transactionId != null -> {
                val alerts = queryAlertUseCase.findByTransactionId(TransactionId(transactionId))
                ResponseEntity.ok(alerts.map { it.toResponse() })
            }
            ruleId != null -> {
                val pageable = PageRequest.of(page, size)
                val result = queryAlertUseCase.findByRuleId(RuleId(ruleId), pageable)
                ResponseEntity.ok(result.toPageResponse())
            }
            else -> ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{alertId}")
    fun findById(@PathVariable alertId: UUID): ResponseEntity<AlertResponse> {
        val alert = queryAlertUseCase.findById(AlertId(alertId))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(alert.toResponse())
    }

    @PatchMapping("/{alertId}/status")
    fun updateStatus(
        @PathVariable alertId: UUID,
        @Valid @RequestBody request: UpdateAlertStatusRequest
    ): ResponseEntity<AlertResponse> {
        val newStatus = AlertStatus.valueOf(request.status)
        val updated = updateAlertStatusUseCase.updateStatus(AlertId(alertId), newStatus)
        return ResponseEntity.ok(updated.toResponse())
    }

    private fun Alert.toResponse(): AlertResponse =
        AlertResponse(
            id = id.value,
            transactionId = transactionId.value,
            ruleId = ruleId.value,
            customerId = customerId.value,
            facts = facts,
            configurationVersion = configurationVersion,
            traceId = traceId?.value,
            actions = actions,
            explanation = explanation,
            status = status.name,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun Page<Alert>.toPageResponse(): Map<String, Any> = mapOf(
        "content" to content.map { it.toResponse() },
        "page" to number,
        "size" to size,
        "totalElements" to totalElements,
        "totalPages" to totalPages
    )
}
