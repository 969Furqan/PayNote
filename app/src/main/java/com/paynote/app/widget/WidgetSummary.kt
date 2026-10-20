package com.paynote.app.widget

import com.paynote.app.model.TransactionEntity
import com.paynote.app.model.flowDirection
import java.time.Instant
import java.time.ZoneId

data class WidgetSummary(val currency: String, val incoming: Double, val outgoing: Double)

fun widgetSummary(rows: List<TransactionEntity>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): WidgetSummary {
    val month = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().withDayOfMonth(1)
    val currency = if (rows.any { it.currency == "PKR" }) "PKR" else rows.maxByOrNull { it.timestampMillis }?.currency ?: "PKR"
    val current = rows.filter { row ->
        val date = Instant.ofEpochMilli(row.timestampMillis).atZone(zone).toLocalDate()
        row.currency == currency && !date.isBefore(month) && date.isBefore(month.plusMonths(1))
    }
    return WidgetSummary(currency, current.filter { it.flowDirection == "RECEIVED" }.sumOf { it.amount },
        current.filter { it.flowDirection != "RECEIVED" }.sumOf { it.amount })
}
