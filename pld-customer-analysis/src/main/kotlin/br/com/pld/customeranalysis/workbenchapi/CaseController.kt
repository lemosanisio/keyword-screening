package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.casemanagement.CaseQueueView
import br.com.pld.customeranalysis.casemanagement.CaseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/cases")
class CaseController(
    private val caseService: CaseService,
) {
    @GetMapping
    fun queue(): CaseQueueView = caseService.queue()
}
