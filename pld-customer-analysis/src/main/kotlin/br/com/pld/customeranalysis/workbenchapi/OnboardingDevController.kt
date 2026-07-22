package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.integration.OnboardingEventConsumer
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Dev endpoint para simular eventos de sistema mestre (onboarding, data change, relationships).
 * Em produção esses eventos viriam via SQS.
 */
@RestController
@RequestMapping("/v1/dev/onboarding")
class OnboardingDevController(
    private val consumer: OnboardingEventConsumer,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/events")
    fun publishEvent(@RequestBody body: OnboardingEventRequest): ResponseEntity<Map<String, String>> {
        val payload = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(body.payload)
        consumer.processEvent(body.eventType, payload, body.correlationId ?: "dev-onboarding")
        return ResponseEntity.ok(mapOf("status" to "processed", "eventType" to body.eventType))
    }

    @PostMapping("/seed")
    fun seed(): ResponseEntity<Map<String, Any>> {
        val events = listOf(
            OnboardingEventRequest("CustomerOnboardingStarted", mapOf(
                "externalId" to "CPF-111.222.333-44",
                "officialName" to "João Silva Santos",
                "partyType" to "PERSON",
                "sourceSystem" to "ONBOARDING_MOCK",
            )),
            OnboardingEventRequest("CustomerOnboardingStarted", mapOf(
                "externalId" to "CNPJ-12.345.678/0001-90",
                "officialName" to "Empresa Suspeito Alto Risco Ltda",
                "partyType" to "ORGANIZATION",
                "sourceSystem" to "ONBOARDING_MOCK",
            )),
            OnboardingEventRequest("CustomerOnboardingStarted", mapOf(
                "externalId" to "CPF-444.555.666-77",
                "officialName" to "Maria PEP Oliveira",
                "partyType" to "PERSON",
                "sourceSystem" to "ONBOARDING_MOCK",
            )),
        )

        var processed = 0
        events.forEach { event ->
            try {
                val payload = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(event.payload)
                consumer.processEvent(event.eventType, payload, event.correlationId ?: "dev-seed-${processed}")
                processed++
            } catch (_: Exception) { /* skip duplicates */ }
        }

        return ResponseEntity.ok(mapOf("processed" to processed, "total" to events.size))
    }
}

data class OnboardingEventRequest(
    val eventType: String,
    val payload: Map<String, Any?>,
    val correlationId: String? = null,
)
