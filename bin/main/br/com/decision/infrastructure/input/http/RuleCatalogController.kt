package br.com.decision.infrastructure.input.http

import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.port.RuleDefinitionRepository
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.infrastructure.input.http.dto.RuleDefinitionResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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

    private fun br.com.decision.domain.model.RuleDefinition.toResponse() = RuleDefinitionResponse(
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
