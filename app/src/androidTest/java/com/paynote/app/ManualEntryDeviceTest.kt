package com.paynote.app

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paynote.app.data.TransactionDatabase
import com.paynote.app.dedup.DeduplicationEngine
import com.paynote.app.model.*
import com.paynote.app.service.TransactionNotificationService
import com.paynote.app.ui.QuickEntryActivity
import com.paynote.app.widget.PayNoteWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualEntryDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao = TransactionDatabase.getInstance(context).transactionDao()

    @Test fun userCorrectionsSurviveAnotherAlertAndPurposeUpdates() = runBlocking {
        val token = "QA${System.nanoTime()}EDIT"
        val parsed = ParsedNotification(PaymentProvider.NAYAPAY, "com.nayapay.app", "PKR", 7.0,
            TransactionDirection.RECEIVED, "QA original name", token, System.currentTimeMillis(), "QA fixture", "Synthetic test only")
        var id: Long? = null
        try {
            TransactionNotificationService.processParsedNotification(context, parsed)
            id = dao.getRecentTransactionsSince(0).single { it.transactionId == token }.id
            dao.updateUserDetails(id, "QA corrected wallet", "QA corrected name", "SENT", "QA category", "Keep my note")
            val decision = TransactionNotificationService.processParsedNotification(context, parsed.copy(counterparty = "QA original name extended"))
            assertTrue(decision is DeduplicationEngine.DedupDecision.DuplicateOf)
            dao.updatePurpose(id, "Updated purpose")
            val row = dao.getById(id)!!
            assertEquals("QA corrected wallet", row.walletName)
            assertEquals("QA corrected name", row.partyName)
            assertEquals("SENT", row.flowDirection)
            assertEquals("QA category", row.category)
            assertEquals("Updated purpose", row.purpose)
            assertEquals("NayaPay", row.provider)
            assertEquals("RECEIVED", row.direction)
            assertEquals(1, dao.getRecentTransactionsSince(0).count { it.transactionId == token })
        } finally {
            id?.let { dao.deleteById(it); (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(it.toInt()) }
        }
    }

    @Test fun manualDirectionsAndCategoriesPersist() = runBlocking {
        val ids = mutableListOf<Long>()
        try {
            for (direction in listOf("SENT", "RECEIVED")) {
                val id = dao.insert(TransactionDraft("QA cash", "QA manual", direction, "1.25", category = "Transport").toManualEntry())
                ids.add(id)
                val row = dao.getById(id)!!
                assertTrue(row.isManual)
                assertEquals(direction, row.flowDirection)
                assertEquals("Transport", row.category)
                assertEquals(1.25, row.amount, 0.0)
            }
        } finally { ids.forEach { dao.deleteById(it) } }
    }

    @Test fun widgetButtonsOpenTheCompactFormWithTheCorrectDirection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (direction in listOf("RECEIVED", "SENT")) {
            val monitor = instrumentation.addMonitor(QuickEntryActivity::class.java.name, null, false)
            var activity: Activity? = null
            try {
                PayNoteWidget.entryIntent(context, direction).send()
                activity = instrumentation.waitForMonitorWithTimeout(monitor, 6_000)
                assertNotNull("The widget must open the entry form", activity)
                assertEquals(direction, activity!!.intent.getStringExtra(QuickEntryActivity.EXTRA_DIRECTION))
            } finally {
                activity?.let { open -> instrumentation.runOnMainSync { open.finish() } }
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    @Test fun migrationFromVersionOnePreservesExistingDetailsAndPurposes() = runBlocking {
        val name = "qa_migration_${System.nanoTime()}.db"
        var room: TransactionDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, provider TEXT NOT NULL, sourcePackage TEXT NOT NULL, currency TEXT NOT NULL, amount REAL NOT NULL, direction TEXT NOT NULL, counterparty TEXT NOT NULL, transactionId TEXT, purpose TEXT, isLogged INTEGER NOT NULL, timestampMillis INTEGER NOT NULL, rawNotificationText TEXT NOT NULL)")
                db.execSQL("CREATE INDEX index_transactions_timestampMillis ON transactions(timestampMillis)")
                db.execSQL("CREATE INDEX index_transactions_transactionId ON transactions(transactionId)")
                db.execSQL("INSERT INTO transactions VALUES (1, 'QA wallet', 'qa.fixture', 'PKR', 12.5, 'SENT', 'QA person', 'QA ref', 'Keep this purpose', 1, 1000, 'Original alert')")
                db.version = 1
            }
            room = Room.databaseBuilder(context, TransactionDatabase::class.java, name).addMigrations(TransactionDatabase.MIGRATION_1_2).build()
            val row = room.transactionDao().getById(1)!!
            assertEquals("Keep this purpose", row.purpose)
            assertEquals("Original alert", row.rawNotificationText)
            assertEquals("QA wallet", row.walletName)
            assertEquals("SENT", row.flowDirection)
            assertEquals(12.5, row.amount, 0.0)
            assertNull(row.category)
            assertNull(row.customCounterparty)
        } finally { room?.close(); context.deleteDatabase(name) }
    }
}
