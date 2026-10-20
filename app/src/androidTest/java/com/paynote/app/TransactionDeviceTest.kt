package com.paynote.app

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paynote.app.data.TransactionDatabase
import com.paynote.app.dedup.DeduplicationEngine
import com.paynote.app.model.ParsedNotification
import com.paynote.app.model.PaymentProvider
import com.paynote.app.model.TransactionDirection
import com.paynote.app.model.TransactionEntity
import com.paynote.app.service.TransactionNotificationService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses synthetic records and removes only its own records and notifications. */
@RunWith(AndroidJUnit4::class)
class TransactionDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao = TransactionDatabase.getInstance(context).transactionDao()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun fixture(provider: PaymentProvider, token: String, amount: Double = 1.0) = ParsedNotification(
        provider = provider,
        sourcePackage = when (provider) {
            PaymentProvider.NAYAPAY -> "com.nayapay.app"
            PaymentProvider.JAZZCASH -> "com.techlogix.mobilinkcustomer"
            PaymentProvider.SADAPAY -> "com.sadapay.app"
            else -> "invo8.meezan.mb"
        },
        currency = "PKR", amount = amount, direction = TransactionDirection.RECEIVED,
        counterparty = "QA DEVICE ONLY", transactionId = "QA$token${provider.name}",
        timestampMillis = System.currentTimeMillis(), rawTitle = "QA device receipt",
        rawText = "Synthetic physical-device regression fixture"
    )

    private suspend fun withFixtures(token: String, body: suspend () -> Unit) {
        try {
            body()
        } finally {
            dao.getRecentTransactionsSince(0).filter { it.transactionId?.startsWith("QA$token") == true }.forEach {
                manager.cancel(it.id.toInt())
                dao.deleteById(it.id)
            }
        }
    }

    @Test
    fun lateAppReplacesSmsDetailsWithoutResettingPurpose() = runBlocking {
        val token = System.nanoTime().toString()
        withFixtures(token) {
            val app = fixture(PaymentProvider.JAZZCASH, token)
            val sms = app.copy(sourcePackage = "com.google.android.apps.messaging", counterparty = "QA DEVICE ONLY Mobile Account",
                rawTitle = "SMS alert", rawText = "Synthetic SMS fallback")
            TransactionNotificationService.processParsedNotification(context, sms)
            val row = dao.getRecentTransactionsSince(0).single { it.transactionId == app.transactionId }
            dao.updatePurpose(row.id, "Keep this saved purpose")
            manager.cancel(row.id.toInt())
            val result = TransactionNotificationService.processParsedNotification(context, app)
            assertTrue(result is DeduplicationEngine.DedupDecision.DuplicateOf)
            val updated = dao.getById(row.id)!!
            assertEquals(app.sourcePackage, updated.sourcePackage)
            assertEquals(app.counterparty, updated.counterparty)
            assertEquals("${app.rawTitle}: ${app.rawText}", updated.rawNotificationText)
            assertEquals("Keep this saved purpose", updated.purpose)
            assertTrue(updated.isLogged)
            assertFalse(manager.activeNotifications.any { it.id == row.id.toInt() })
        }
    }

    @Test
    fun appFirstRetainsNameWhenSmsArrivesLater() = runBlocking {
        val token = System.nanoTime().toString()
        withFixtures(token) {
            val app = fixture(PaymentProvider.JAZZCASH, token)
            TransactionNotificationService.processParsedNotification(context, app)
            val sms = app.copy(sourcePackage = "com.google.android.apps.messaging", counterparty = "QA DEVICE ONLY Mobile Account",
                rawTitle = "SMS alert", rawText = "Synthetic SMS fallback")
            val result = TransactionNotificationService.processParsedNotification(context, sms)
            assertTrue(result is DeduplicationEngine.DedupDecision.DuplicateOf)
            val rows = dao.getRecentTransactionsSince(0).filter { it.transactionId == app.transactionId }
            assertEquals(1, rows.size)
            assertEquals(app.sourcePackage, rows.single().sourcePackage)
            assertEquals(app.counterparty, rows.single().counterparty)
            assertEquals("${app.rawTitle}: ${app.rawText}", rows.single().rawNotificationText)
            assertEquals(1, manager.activeNotifications.count { it.id == rows.single().id.toInt() })
        }
    }

    @Test
    fun incompleteSavedReceiptsAreRepairedWithoutChangingPurpose() = runBlocking {
        val token = System.nanoTime().toString()
        withFixtures(token) {
            val parsed = fixture(PaymentProvider.JAZZCASH, token, 2.0)
            val id = dao.insert(TransactionEntity(
                provider = parsed.provider.displayName, sourcePackage = parsed.sourcePackage,
                currency = parsed.currency, amount = parsed.amount, direction = parsed.direction.name,
                counterparty = "Unknown Counterparty", transactionId = parsed.transactionId,
                purpose = "Keep this purpose", isLogged = true, timestampMillis = parsed.timestampMillis,
                rawNotificationText = "Raast Incoming Payment: Rs 2.00 received from QA TEST SENDER AC ***************123456789 in your JazzCash Mobile Account:03000000000 via Raast. TID: ${parsed.transactionId}"
            ))
            val sadaPay = fixture(PaymentProvider.SADAPAY, token, 10.0)
            val sadaPayId = dao.insert(TransactionEntity(
                provider = sadaPay.provider.displayName, sourcePackage = sadaPay.sourcePackage,
                currency = sadaPay.currency, amount = sadaPay.amount, direction = "SENT",
                counterparty = "Money Received", transactionId = sadaPay.transactionId,
                purpose = "Keep SadaPay purpose", isLogged = true, timestampMillis = sadaPay.timestampMillis,
                rawNotificationText = "Money Received: QA TEST SENDER sent you PKR 10"
            ))
            TransactionNotificationService.repairIncompleteTransactions(context)
            val repaired = dao.getById(id)!!
            assertEquals("QA TEST SENDER", repaired.counterparty)
            assertEquals("Keep this purpose", repaired.purpose)
            assertTrue(repaired.isLogged)
            val sadaPayRepaired = dao.getById(sadaPayId)!!
            assertEquals("QA TEST SENDER", sadaPayRepaired.counterparty)
            assertEquals("RECEIVED", sadaPayRepaired.direction)
            assertEquals("Keep SadaPay purpose", sadaPayRepaired.purpose)
            assertTrue(sadaPayRepaired.isLogged)
        }
    }

    @Test
    fun separateBanksKeepThreeEqualReceipts() = runBlocking {
        val token = System.nanoTime().toString()
        withFixtures(token) {
            val providers = listOf(PaymentProvider.MEEZAN_BANK, PaymentProvider.NAYAPAY, PaymentProvider.JAZZCASH)
            val decisions = coroutineScope {
                providers.map { async { TransactionNotificationService.processParsedNotification(context, fixture(it, token)) } }.awaitAll()
            }
            assertTrue(decisions.all { it is DeduplicationEngine.DedupDecision.NewTransaction })
            val rows = dao.getRecentTransactionsSince(0).filter { it.transactionId?.startsWith("QA$token") == true }
            assertEquals(3, rows.size)
            assertEquals(providers.map { it.displayName }.toSet(), rows.map { it.provider }.toSet())
            assertTrue(rows.all { row -> manager.activeNotifications.any { it.id == row.id.toInt() } })
        }
    }

    @Test
    fun concurrentAppAndSmsSaveOneRecord() = runBlocking {
        val token = System.nanoTime().toString()
        withFixtures(token) {
            val app = fixture(PaymentProvider.NAYAPAY, token)
            val sms = app.copy(sourcePackage = "com.google.android.apps.messaging", timestampMillis = app.timestampMillis + 1000)
            val decisions = coroutineScope {
                listOf(app, sms).map { async { TransactionNotificationService.processParsedNotification(context, it) } }.awaitAll()
            }
            assertEquals(1, decisions.count { it is DeduplicationEngine.DedupDecision.NewTransaction })
            assertEquals(1, decisions.count { it is DeduplicationEngine.DedupDecision.DuplicateOf })
            assertEquals(1, dao.getRecentTransactionsSince(0).count { it.transactionId?.startsWith("QA$token") == true })
        }
    }

    @Test
    fun inlineReplyAndQuickActionsPersistPurposes() = runBlocking {
        val token = System.nanoTime().toString()
        withFixtures(token) {
            val expected = listOf("QA inline purpose", "Food & Dining", "General / Uncategorized")
            for (index in expected.indices) {
                val parsed = fixture(PaymentProvider.NAYAPAY, "${token}_$index", 8001.0 + index)
                TransactionNotificationService.processParsedNotification(context, parsed)
                val row = dao.getRecentTransactionsSince(0).single { it.transactionId == parsed.transactionId }
                val notification = withTimeout(5000) {
                    var active = manager.activeNotifications.firstOrNull { it.id == row.id.toInt() }
                    while (active == null) {
                        delay(50)
                        active = manager.activeNotifications.firstOrNull { it.id == row.id.toInt() }
                    }
                    active.notification
                }
                val action = notification.actions[index]
                val fillIn = Intent()
                if (index == 0) {
                    val results = Bundle().apply { putCharSequence(TransactionNotificationService.KEY_TEXT_REPLY, expected[index]) }
                    RemoteInput.addResultsToIntent(action.remoteInputs, fillIn, results)
                }
                action.actionIntent.send(context, 0, fillIn)
                withTimeout(5000) {
                    while (dao.getById(row.id)?.purpose != expected[index]) delay(50)
                }
                assertTrue(dao.getById(row.id)!!.isLogged)
            }
        }
    }
}
