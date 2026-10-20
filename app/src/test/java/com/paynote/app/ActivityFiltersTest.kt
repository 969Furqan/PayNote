package com.paynote.app

import com.paynote.app.model.TransactionEntity
import com.paynote.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ActivityFiltersTest {
    private fun row(id: Long, provider: String = "NayaPay", direction: String = "RECEIVED", purpose: String? = null,
                    currency: String = "PKR", timestamp: String = "2026-10-21T12:00:00Z") = TransactionEntity(
        id = id, provider = provider, sourcePackage = "com.nayapay.app", currency = currency, amount = 10.0,
        direction = direction, counterparty = "QA Sender", transactionId = "REF$id", purpose = purpose,
        timestampMillis = Instant.parse(timestamp).toEpochMilli(), rawNotificationText = "Unit fixture"
    )
    private val now = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()

    @Test fun `combined filters return only matching wallet flow purpose currency and text`() {
        val rows = listOf(row(1), row(2, provider = "JazzCash"), row(3, direction = "SENT"), row(4, purpose = "Rent"), row(5, currency = "USD"))
        val filters = ActivityFilters(query = "qa sender", provider = "NayaPay", currency = "PKR", flow = FlowFilter.INCOMING, purpose = PurposeFilter.PENDING)
        assertEquals(listOf(1L), filterTransactions(rows, filters, now).map { it.id })
    }

    @Test fun `outgoing includes sent and paid and search finds a saved purpose or reference`() {
        val rows = listOf(row(1), row(2, direction = "SENT", purpose = "Office Lunch"), row(3, direction = "PAID"))
        assertEquals(setOf(2L, 3L), filterTransactions(rows, ActivityFilters(flow = FlowFilter.OUTGOING), now).map { it.id }.toSet())
        assertEquals(2L, filterTransactions(rows, ActivityFilters(query = "LUNCH"), now).single().id)
        assertEquals(3L, filterTransactions(rows, ActivityFilters(query = "REF3"), now).single().id)
    }

    @Test fun `today uses local day boundaries and excludes the next day`() {
        val rows = listOf(row(1, timestamp = "2026-10-20T18:59:59Z"), row(2, timestamp = "2026-10-20T19:00:00Z"), row(3, timestamp = "2026-10-21T19:00:00Z"))
        assertEquals(listOf(2L), filterTransactions(rows, ActivityFilters(period = PeriodFilter.TODAY), now, ZoneId.of("Asia/Karachi")).map { it.id })
    }

    @Test fun `month and chronological order do not include adjacent month entries`() {
        val rows = listOf(row(1, timestamp = "2026-09-30T23:59:59Z"), row(2, timestamp = "2026-10-01T00:00:00Z"), row(3), row(4, timestamp = "2026-11-01T00:00:00Z"))
        assertEquals(listOf(2L, 3L), filterTransactions(rows, ActivityFilters(period = PeriodFilter.MONTH, oldestFirst = true), now, ZoneId.of("UTC")).map { it.id })
    }

    @Test fun `CSV preserves quotes commas and newlines in user purposes`() {
        val csv = transactionCsv(listOf(row(1, purpose = "Lunch, \"with friends\"\nand tea")))
        assertTrue(csv.contains("\"Lunch, \"\"with friends\"\"\nand tea\""))
        assertTrue(csv.startsWith("ID,Timestamp,Provider,Direction,Currency,Amount,Counterparty,Purpose,TransactionID,Source,Category\r\n"))
        assertTrue(csv.endsWith("\"REF1\",\"App notification\",\"\""))
    }
}
