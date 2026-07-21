package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.casemanagement.CaseDetailView
import br.com.pld.customeranalysis.casemanagement.CaseNotFoundException
import br.com.pld.customeranalysis.casemanagement.CaseQueueView
import br.com.pld.customeranalysis.casemanagement.CaseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/cases")
class CaseController(
    private val caseService: CaseService,
) {
    @GetMapping
    fun queue(): CaseQueueView = caseService.queue()

    @GetMapping("/{caseId}")
    fun get(@PathVariable caseId: String): CaseDetailView = caseService.get(caseId)

    @ExceptionHandler(CaseNotFoundException::class)
    fun notFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()
}
