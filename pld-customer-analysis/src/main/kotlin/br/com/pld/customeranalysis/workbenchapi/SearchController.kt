package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.casemanagement.CaseJpaRepository
import br.com.pld.customeranalysis.party.PartyJpaRepository
import br.com.pld.customeranalysis.party.PartySnapshotJpaRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/search")
class SearchController(
    private val partyRepository: PartyJpaRepository,
    private val snapshotRepository: PartySnapshotJpaRepository,
    private val caseRepository: CaseJpaRepository,
) {

    @GetMapping
    fun search(@RequestParam q: String): SearchResponse {
        if (q.isBlank() || q.length < 2) {
            return SearchResponse(parties = emptyList(), cases = emptyList())
        }

        val query = q.trim()

        // Search by exact partyId or caseId
        val parties = mutableListOf<PartySearchResult>()
        val cases = mutableListOf<CaseSearchResult>()

        // Exact ID match
        if (query.startsWith("pty_")) {
            partyRepository.findById(query).ifPresent { party ->
                val snapshot = snapshotRepository.findTopByPartyIdOrderBySnapshotVersionDesc(party.id)
                parties += PartySearchResult(
                    partyId = party.id,
                    partyType = party.partyType.name,
                    officialName = snapshot?.officialName ?: "—",
                    matchReason = "ID exato",
                )
            }
        }
        if (query.startsWith("cas_")) {
            caseRepository.findById(query).ifPresent { case ->
                cases += CaseSearchResult(
                    caseId = case.id,
                    partyId = case.partyId,
                    status = case.status.name,
                    priority = case.priority.name,
                    reasonCode = case.reasonCode,
                    matchReason = "ID exato",
                )
            }
        }

        // Name substring search (case-insensitive)
        if (parties.isEmpty() && !query.startsWith("cas_")) {
            val snapshots = snapshotRepository.findByOfficialNameContainingIgnoreCase(query)
            snapshots.take(10).forEach { snapshot ->
                parties += PartySearchResult(
                    partyId = snapshot.partyId,
                    partyType = partyRepository.findById(snapshot.partyId).map { it.partyType.name }.orElse("UNKNOWN"),
                    officialName = snapshot.officialName,
                    matchReason = "Nome contém \"$query\"",
                )
            }
        }

        // Cases by partyId
        if (cases.isEmpty() && query.startsWith("pty_")) {
            caseRepository.findByPartyIdOrderByCreatedAtDesc(query).take(5).forEach { case ->
                cases += CaseSearchResult(
                    caseId = case.id,
                    partyId = case.partyId,
                    status = case.status.name,
                    priority = case.priority.name,
                    reasonCode = case.reasonCode,
                    matchReason = "Party associada",
                )
            }
        }

        return SearchResponse(parties = parties, cases = cases)
    }
}

data class SearchResponse(
    val parties: List<PartySearchResult>,
    val cases: List<CaseSearchResult>,
)

data class PartySearchResult(
    val partyId: String,
    val partyType: String,
    val officialName: String,
    val matchReason: String,
)

data class CaseSearchResult(
    val caseId: String,
    val partyId: String,
    val status: String,
    val priority: String,
    val reasonCode: String,
    val matchReason: String,
)
