package br.com.pld.customeranalysis.integration

data class EventId(val value: String)

enum class IntegrationRecordStatus {
    PENDING,
    PROCESSED,
    FAILED,
}
