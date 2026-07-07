package br.com.decision.infrastructure.input.http

import br.com.decision.domain.port.EntityDefinitionRepository
import br.com.decision.domain.port.FactDefinitionRepository
import br.com.decision.infrastructure.input.http.dto.EntityDefinitionResponse
import br.com.decision.infrastructure.input.http.dto.FactDefinitionResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/decision")
class FactRegistryController(
    private val factDefinitionRepository: FactDefinitionRepository,
    private val entityDefinitionRepository: EntityDefinitionRepository
) {

    @GetMapping("/facts")
    fun listFacts(
        @RequestParam(required = false) entity: String?,
        @RequestParam(required = false) enabled: Boolean?
    ): ResponseEntity<List<FactDefinitionResponse>> {
        val facts = when {
            enabled == true -> factDefinitionRepository.findEnabled()
            else -> factDefinitionRepository.findAll()
        }

        val filtered = facts
            .let { list ->
                if (entity != null) list.filter { it.entity.equals(entity, ignoreCase = true) }
                else list
            }
            .let { list ->
                if (enabled == false) list.filter { !it.enabled }
                else list
            }

        val response = filtered.map { it.toResponse() }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/entities")
    fun listEntities(): ResponseEntity<List<EntityDefinitionResponse>> {
        val entities = entityDefinitionRepository.findAll()
        val response = entities.map { it.toResponse() }
        return ResponseEntity.ok(response)
    }

    private fun br.com.decision.domain.model.FactDefinition.toResponse() = FactDefinitionResponse(
        id = id.toString(),
        name = name.value,
        displayName = displayName,
        entity = entity,
        type = type.name,
        context = context.name,
        source = source,
        supportedOperators = supportedOperators.map { it.name },
        enabled = enabled
    )

    private fun br.com.decision.domain.model.EntityDefinition.toResponse() = EntityDefinitionResponse(
        id = id.toString(),
        name = name,
        displayName = displayName,
        sourceSystem = sourceSystem,
        factNames = factNames.map { it.value }
    )
}
