package com.paynote.app.ui

import com.paynote.app.model.*
import java.time.Instant
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import androidx.compose.runtime.saveable.listSaver

enum class FlowFilter(val label: String) { ALL("All activity"), INCOMING("Incoming"), OUTGOING("Outgoing") }
enum class PurposeFilter(val label: String) { ALL("Any purpose"), PENDING("Needs a purpose"), SAVED("Purpose saved") }
enum class PeriodFilter(val label: String) { ALL("All time"), TODAY("Today"), WEEK("This week"), MONTH("This month") }

data class ActivityFilters(
    val query: String = "",
    val provider: String? = null,
    val currency: String? = null,
    val flow: FlowFilter = FlowFilter.ALL,
    val purpose: PurposeFilter = PurposeFilter.ALL,
    val period: PeriodFilter = PeriodFilter.ALL,
    val oldestFirst: Boolean = false,
    val category: String? = null
) {
    val activeCount: Int get() = listOf(provider != null, currency != null, flow != FlowFilter.ALL,
        purpose != PurposeFilter.ALL, period != PeriodFilter.ALL, oldestFirst, category != null).count { it }
}

val ActivityFiltersSaver = listSaver<ActivityFilters, String>(
    save = { listOf(it.query, it.provider.orEmpty(), it.currency.orEmpty(), it.flow.name, it.purpose.name, it.period.name, it.oldestFirst.toString(), it.category.orEmpty()) },
    restore = { ActivityFilters(it[0], it[1].ifEmpty { null }, it[2].ifEmpty { null }, FlowFilter.valueOf(it[3]), PurposeFilter.valueOf(it[4]), PeriodFilter.valueOf(it[5]), it[6].toBoolean(), it.getOrNull(7)?.ifEmpty { null }) }
)

fun filterTransactions(rows: List<TransactionEntity>, filters: ActivityFilters,
                       nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<TransactionEntity> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val start = when (filters.period) {
        PeriodFilter.ALL -> null
        PeriodFilter.TODAY -> today
        PeriodFilter.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        PeriodFilter.MONTH -> today.withDayOfMonth(1)
    }
    val end = when (filters.period) {
        PeriodFilter.ALL -> null
        PeriodFilter.TODAY -> today.plusDays(1)
        PeriodFilter.WEEK -> start!!.plusWeeks(1)
        PeriodFilter.MONTH -> start!!.plusMonths(1)
    }
    val query = filters.query.trim()
    val result = rows.filter { row ->
        val day = Instant.ofEpochMilli(row.timestampMillis).atZone(zone).toLocalDate()
        (filters.provider == null || row.walletName == filters.provider) &&
            (filters.category == null || row.category == filters.category) &&
            (filters.currency == null || row.currency == filters.currency) &&
            (filters.flow == FlowFilter.ALL || (row.flowDirection == "RECEIVED") == (filters.flow == FlowFilter.INCOMING)) &&
            (filters.purpose == PurposeFilter.ALL || (!row.purpose.isNullOrBlank()) == (filters.purpose == PurposeFilter.SAVED)) &&
            (start == null || (!day.isBefore(start) && day.isBefore(end!!))) &&
            (query.isBlank() || listOf(row.partyName, row.walletName, row.category.orEmpty(), row.purpose.orEmpty(), row.transactionId.orEmpty())
                .any { it.contains(query, ignoreCase = true) })
    }
    return if (filters.oldestFirst) result.sortedBy { it.timestampMillis } else result.sortedByDescending { it.timestampMillis }
}

fun transactionCsv(rows: List<TransactionEntity>): String {
    fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
    val header = "ID,Timestamp,Provider,Direction,Currency,Amount,Counterparty,Purpose,TransactionID,Source,Category"
    return header + "\r\n" + rows.joinToString("\r\n") { row ->
        listOf(row.id.toString(), row.timestampMillis.toString(), quote(row.walletName), row.flowDirection,
            row.currency, row.amount.toString(), quote(row.partyName), quote(row.purpose.orEmpty()),
            quote(row.transactionId.orEmpty()), quote(row.sourceLabel), quote(row.category.orEmpty()))
            .joinToString(",")
    }
}
