package br.com.pld.customeranalysis.party

enum class PartyType {
    PERSON,
    ORGANIZATION,
}

data class PartyId(val value: String)

data class PartySnapshotId(val value: String)
