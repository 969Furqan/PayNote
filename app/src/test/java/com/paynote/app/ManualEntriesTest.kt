package com.paynote.app

import com.paynote.app.dedup.DeduplicationEngine
import com.paynote.app.model.*
import com.paynote.app.ui.*
import com.paynote.app.widget.widgetSummary
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ManualEntriesTest {
    private val now = Instant.parse("2026-10-21T10:00:00Z").toEpochMilli()
    private fun draft(direction: String = "SENT") = TransactionDraft(" Cash ", " QA recipient ", direction, "25.50", "pkr", "Transport", "Taxi", now)

    @Test fun manualIncomingAndOutgoingHaveAnExplicitSourceAndCleanValues() {
        for (direction in listOf("SENT", "PAID", "RECEIVED")) {
            val row = draft(direction).toManualEntry()
            assertEquals(direction, row.flowDirection)
            assertEquals("Cash", row.walletName)
            assertEquals("QA recipient", row.partyName)
            assertEquals("PKR", row.currency)
            assertEquals("Manual entry", row.sourceLabel)
            assertEquals("Transport", row.category)
            assertTrue(row.isLogged && row.isManual)
        }
    }

    @Test fun invalidAmountsAndMissingRequiredFieldsCannotBeSaved() {
        for (amount in listOf("", "0", "-1", "NaN", "Infinity", "1.234", "1,2", "1e3", "9999999999999")) {
            assertNotNull(amount, draft().copy(amount = amount).validationError())
        }
        assertNotNull(draft().copy(counterparty = " ").validationError())
        assertNotNull(draft().copy(wallet = "").validationError())
        assertNotNull(draft().copy(currency = "RUPEES").validationError())
        assertNotNull(draft().copy(direction = "OTHER").validationError())
        assertNull(draft().copy(amount = "0.01").validationError())
    }

    @Test fun correctionsDriveSearchCategoriesFiltersCsvAndTotalsWhileOriginalFactsRemain() {
        val row = draft().toManualEntry().copy(sourcePackage = "com.sadapay.app", customProvider = "My savings", customCounterparty = "Correct name", customDirection = "RECEIVED")
        assertEquals("Cash", row.provider)
        assertEquals("QA recipient", row.counterparty)
        val filters = ActivityFilters(query = "correct", provider = "My savings", category = "Transport", flow = FlowFilter.INCOMING)
        assertEquals(listOf(row), filterTransactions(listOf(row), filters, now))
        assertTrue(filterTransactions(listOf(row), filters.copy(category = "Bills"), now).isEmpty())
        val csv = transactionCsv(listOf(row))
        assertTrue(csv.contains("\"My savings\",RECEIVED"))
        assertTrue(csv.contains("\"Correct name\""))
        assertTrue(csv.endsWith("\"Transport\""))
        assertEquals(25.5, widgetSummary(listOf(row), now).incoming, 0.0)
    }

    @Test fun aManualRecordDoesNotSwallowAnUnrelatedDetectedNotification() {
        val manual = draft("RECEIVED").copy(wallet = "SadaPay").toManualEntry()
        val detected = ParsedNotification(PaymentProvider.SADAPAY, "com.sadapay.app", "PKR", manual.amount,
            TransactionDirection.RECEIVED, manual.counterparty, null, now, "Receipt", "Test")
        assertTrue(DeduplicationEngine.evaluate(detected, listOf(manual)) is DeduplicationEngine.DedupDecision.NewTransaction)
    }

    @Test fun widgetUsesOneCurrencyAndTheLocalMonthAndIncludesCorrectedDirections() {
        val rows = listOf(draft("RECEIVED").toManualEntry(), draft().copy(currency = "USD", amount = "900").toManualEntry(),
            draft().copy(timestampMillis = Instant.parse("2026-09-30T18:59:59Z").toEpochMilli()).toManualEntry(),
            draft().copy(timestampMillis = Instant.parse("2026-10-31T19:00:00Z").toEpochMilli()).toManualEntry())
        val summary = widgetSummary(rows, now, ZoneId.of("Asia/Karachi"))
        assertEquals("PKR", summary.currency)
        assertEquals(25.5, summary.incoming, 0.0)
        assertEquals(0.0, summary.outgoing, 0.0)
        assertEquals("PKR", widgetSummary(emptyList(), now).currency)
    }
}
