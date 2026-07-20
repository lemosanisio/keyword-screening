package br.com.decision.domain.model.vo

import java.math.BigDecimal

sealed class FactValue {
    data class BooleanValue(val value: Boolean) : FactValue()
    data class EnumValue(val value: String) : FactValue()
    data class NumberValue(val value: BigDecimal) : FactValue()
    data class StringValue(val value: String) : FactValue()
    data class MoneyValue(val amount: BigDecimal, val currency: String) : FactValue()
}
