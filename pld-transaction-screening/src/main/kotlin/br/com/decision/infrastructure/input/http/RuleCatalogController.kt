package br.com.decision.infrastructure.input.http

import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.infrastructure.input.http.dto.RuleDefinitionResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/v1/decision/rules")
class RuleCatalogController(
    private val ruleDefinitionRepository: RuleDefinitionRepository
) {

    @GetMapping
    fun listRules(
        @RequestParam(required = false) context: RuleContext?,
        @RequestParam(required = false) category: RuleCategory?
    ): ResponseEntity<List<RuleDefinitionResponse>> {
        val rules = ruleDefinitionRepository.findByContextAndCategory(context, category)
        val response = rules.map { it.toResponse() }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{code}")
    fun getRuleByCode(@PathVariable code: String): ResponseEntity<RuleDefinitionResponse> {
        val rule = ruleDefinitionRepository.findByCode(RuleCode(code))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(rule.toResponse())
    }

    @PostMapping
    fun createRule(@RequestBody request: CreateRuleDefinitionRequest): ResponseEntity<RuleDefinitionResponse> {
        val existing = ruleDefinitionRepository.findByCode(RuleCode(request.code))
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

        val definition = RuleDefinition(
            id = RuleId(UUID.randomUUID()),
            code = RuleCode(request.code),
            name = request.name,
            description = request.description,
            context = RuleContext.valueOf(request.context),
            category = RuleCategory.valueOf(request.category),
            supportedFacts = request.supportedFacts.map { FactName(it) },
            supportedActions = request.supportedActions.map { Action.valueOf(it) },
            status = RuleStatus.ACTIVE,
            createdAt = Instant.now(),
        )

        val saved = ruleDefinitionRepository.save(definition)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    private fun RuleDefinition.toResponse() = RuleDefinitionResponse(
        id = id.value.toString(),
        code = code.value,
        name = name,
        description = description,
        context = context.name,
        category = category.name,
        supportedFacts = supportedFacts.map { it.value },
        supportedActions = supportedActions.map { it.name },
        status = status.name,
        createdAt = createdAt
    )
}

data class CreateRuleDefinitionRequest(
    val code: String,
    val name: String,
    val description: String = "",
    val context: String = "SCREENING",
    val category: String = "KEYWORD_SCREENING",
    val supportedFacts: List<String> = emptyList(),
    val supportedActions: List<String> = listOf("GENERATE_ALERT", "IGNORE"),
)
