package br.com.pld.customeranalysis.timeline

data class TimelineEntryId(val value: String)

enum class VisibilityClassification {
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED,
}
