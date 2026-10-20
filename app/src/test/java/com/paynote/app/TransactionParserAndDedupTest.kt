package com.paynote.app

import com.paynote.app.dedup.DeduplicationEngine
import com.paynote.app.model.PaymentProvider
import com.paynote.app.model.ParsedNotification
import com.paynote.app.model.TransactionDirection
import com.paynote.app.model.TransactionEntity
import com.paynote.app.parser.TransactionParser
import org.junit.Assert.*
import org.junit.Test

class TransactionParserAndDedupTest {

    @Test
    fun `SadaPay sent you notification is incoming and identifies the sender`() {
        val parsed = TransactionParser.parse("com.sadapay.app", "Money Received", "QA TEST SENDER sent you PKR 10")!!
        assertEquals(TransactionDirection.RECEIVED, parsed.direction)
        assertEquals("QA TEST SENDER", parsed.counterparty)
        assertEquals(10.0, parsed.amount, 0.001)
    }

    @Test
    fun `JazzCash receipt stops the sender name before masked account details`() {
        val parsed = TransactionParser.parse("com.techlogix.mobilinkcustomer", "Raast Incoming Payment",
            "Rs 2.00 received from QA TEST SENDER AC ***************123456789 in your JazzCash Mobile Account:03000000000 via Raast. TID: 123456789012")!!
        assertEquals("QA TEST SENDER", parsed.counterparty)
        assertEquals(PaymentProvider.JAZZCASH, parsed.provider)
        assertEquals(TransactionDirection.RECEIVED, parsed.direction)
        assertEquals(2.0, parsed.amount, 0.001)
        assertEquals("123456789012", parsed.transactionId)
    }

    private fun receipt(provider: PaymentProvider = PaymentProvider.MEEZAN_BANK,
                        tid: String? = "MZ12345678", currency: String = "PKR") = ParsedNotification(
        provider, "test.${provider.name}", currency, 1.0, TransactionDirection.RECEIVED,
        "QA Sender", tid, 1_700_000_000_000L, "Payment received", "PKR 1 received from QA Sender"
    )

    private fun saved(parsed: ParsedNotification) = TransactionEntity(
        id = 1L, provider = parsed.provider.displayName, sourcePackage = parsed.sourcePackage,
        currency = parsed.currency, amount = parsed.amount, direction = parsed.direction.name,
        counterparty = parsed.counterparty, transactionId = parsed.transactionId, purpose = null,
        timestampMillis = parsed.timestampMillis, rawNotificationText = parsed.rawText
    )

    @Test
    fun `shorter app name replaces longer SMS name`() {
        val app = receipt(PaymentProvider.JAZZCASH).copy(sourcePackage = "com.techlogix.mobilinkcustomer", counterparty = "QA Sender")
        val sms = app.copy(sourcePackage = "com.google.android.apps.messaging", counterparty = "QA Sender Mobile Account")
        val decision = DeduplicationEngine.evaluate(app, listOf(saved(sms))) as DeduplicationEngine.DedupDecision.DuplicateOf
        assertEquals("QA Sender", decision.betterCounterparty)
        assertEquals(app, decision.preferredNotification)
    }

    @Test
    fun `longer SMS name cannot replace the app name`() {
        val app = receipt(PaymentProvider.JAZZCASH).copy(sourcePackage = "com.techlogix.mobilinkcustomer", counterparty = "QA Sender")
        val sms = app.copy(sourcePackage = "com.google.android.apps.messaging", counterparty = "QA Sender Mobile Account")
        val decision = DeduplicationEngine.evaluate(sms, listOf(saved(app))) as DeduplicationEngine.DedupDecision.DuplicateOf
        assertEquals("QA Sender", decision.betterCounterparty)
        assertNull(decision.preferredNotification)
    }

    @Test
    fun `app can enrich one unnamed SMS without a shared reference`() {
        val app = receipt(PaymentProvider.JAZZCASH, tid = null).copy(sourcePackage = "com.techlogix.mobilinkcustomer")
        val sms = app.copy(sourcePackage = "com.google.android.apps.messaging", counterparty = "Unknown Counterparty")
        val decision = DeduplicationEngine.evaluate(app, listOf(saved(sms))) as DeduplicationEngine.DedupDecision.DuplicateOf
        assertEquals(app.counterparty, decision.betterCounterparty)
        assertEquals(app, decision.preferredNotification)
    }

    @Test
    fun `ambiguous unnamed SMS records do not swallow a new app receipt`() {
        val app = receipt(PaymentProvider.JAZZCASH, tid = null).copy(sourcePackage = "com.techlogix.mobilinkcustomer")
        val sms = saved(app.copy(sourcePackage = "com.google.android.apps.messaging", counterparty = "Unknown Counterparty"))
        assertEquals(DeduplicationEngine.DedupDecision.NewTransaction,
            DeduplicationEngine.evaluate(app, listOf(sms, sms.copy(id = 2))))
    }

    @Test
    fun `exact reference wins over a newer amount match`() {
        val app = receipt(tid = "REF12345678")
        val exact = saved(app).copy(id = 2)
        val newerUnnamed = saved(app.copy(transactionId = null))
        val decision = DeduplicationEngine.evaluate(app, listOf(newerUnnamed, exact)) as DeduplicationEngine.DedupDecision.DuplicateOf
        assertEquals(2L, decision.existingId)
    }

    @Test
    fun `incoming notifications identify the payment apps installed on the Pixel`() {
        val providers = mapOf("invo8.meezan.mb" to PaymentProvider.MEEZAN_BANK,
            "com.ofss.tx.meezan" to PaymentProvider.MEEZAN_BANK,
            "com.nayapay.app" to PaymentProvider.NAYAPAY,
            "com.techlogix.mobilinkcustomer" to PaymentProvider.JAZZCASH)
        for ((packageName, provider) in providers) {
            val parsed = TransactionParser.parse(packageName, "Payment received", "Rs. 1.00 received from QA SENDER via Raast")!!
            assertEquals(provider, parsed.provider)
            assertEquals(TransactionDirection.RECEIVED, parsed.direction)
            assertEquals(1.0, parsed.amount, 0.001)
        }
    }

    @Test
    fun `equal incoming amounts at different banks remain separate`() {
        val existing = saved(receipt())
        for (provider in listOf(PaymentProvider.NAYAPAY, PaymentProvider.JAZZCASH)) {
            assertEquals(DeduplicationEngine.DedupDecision.NewTransaction,
                DeduplicationEngine.evaluate(receipt(provider, "${provider.name}87654321"), listOf(existing)))
        }
    }

    @Test
    fun `different currencies cannot be duplicates`() {
        assertEquals(DeduplicationEngine.DedupDecision.NewTransaction,
            DeduplicationEngine.evaluate(receipt(currency = "USD"), listOf(saved(receipt()))))
    }

    @Test
    fun `different references at the same bank remain separate`() {
        assertEquals(DeduplicationEngine.DedupDecision.NewTransaction,
            DeduplicationEngine.evaluate(receipt(tid = "MZ87654321"), listOf(saved(receipt()))))
    }

    @Test
    fun `unrelated counterparties are not duplicates just because channels differ`() {
        val incoming = receipt(tid = null).copy(sourcePackage = "com.google.android.apps.messaging", counterparty = "Different Person")
        assertEquals(DeduplicationEngine.DedupDecision.NewTransaction,
            DeduplicationEngine.evaluate(incoming, listOf(saved(receipt(tid = null)))))
    }

    @Test
    fun `different full references sharing a suffix do not match`() {
        assertFalse(DeduplicationEngine.isMatchingTransactionId("111123456", "999123456"))
        assertTrue(DeduplicationEngine.isMatchingTransactionId("000084920174", "84920174"))
        assertTrue(DeduplicationEngine.isMatchingTransactionId("123456789", "456789"))
    }

    @Test
    fun `parses SadaPay outgoing notification and suppresses duplicate SMS`() {
        val t0 = 1_700_000_000_000L

        val appNotif = TransactionParser.parse(
            packageName = "com.sadapay.app",
            title = "SadaPay",
            text = "You sent Rs. 2,500 to Ali Khan. TID: 84920174",
            timestampMillis = t0
        )
        assertNotNull(appNotif)
        assertEquals(PaymentProvider.SADAPAY, appNotif!!.provider)
        assertEquals(2500.0, appNotif.amount, 0.001)
        assertEquals("Ali Khan", appNotif.counterparty)
        assertEquals("84920174", appNotif.transactionId)

        val savedEntity = TransactionEntity(
            id = 1L,
            provider = appNotif.provider.displayName,
            sourcePackage = appNotif.sourcePackage,
            currency = appNotif.currency,
            amount = appNotif.amount,
            direction = appNotif.direction.name,
            counterparty = appNotif.counterparty,
            transactionId = appNotif.transactionId,
            purpose = null,
            timestampMillis = appNotif.timestampMillis,
            rawNotificationText = appNotif.rawText
        )

        // 5 seconds later, SMS arrives with full zero-padded Reference and uppercase name
        val smsNotif = TransactionParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "SadaPay",
            text = "PKR 2,500.00 sent to MUHAMMAD ALI KHAN on 20-Oct. Ref: 000084920174",
            timestampMillis = t0 + 5_000L
        )
        assertNotNull(smsNotif)

        val decision = DeduplicationEngine.evaluate(smsNotif!!, listOf(savedEntity))
        assertTrue(decision is DeduplicationEngine.DedupDecision.DuplicateOf)
    }

    @Test
    fun `parses NayaPay, JazzCash, Meezan Bank, and Google Pay accurately`() {
        val nayapay = TransactionParser.parse(
            packageName = "com.nayapay.app",
            title = "NayaPay",
            text = "You received Rs. 12,000 from Sara Ahmed via Raast. Txn ID: NP9938271"
        )
        assertNotNull(nayapay)
        assertEquals(TransactionDirection.RECEIVED, nayapay!!.direction)
        assertEquals("Sara Ahmed", nayapay.counterparty)
        assertEquals("NP9938271", nayapay.transactionId)

        val jazzcash = TransactionParser.parse(
            packageName = "com.techlogix.mobilinkcustomer",
            title = "8558",
            text = "Rs. 1,200.00 sent to ZAIN UL ABIDEEN (03001234567) via JazzCash. TID: 094827162534. Fee Rs. 0.00."
        )
        assertNotNull(jazzcash)
        assertEquals(1200.0, jazzcash!!.amount, 0.001)
        assertEquals("ZAIN UL ABIDEEN", jazzcash.counterparty)
        assertEquals("094827162534", jazzcash.transactionId)

        val meezan = TransactionParser.parse(
            packageName = "pk.com.meezanbank.mobilebanking",
            title = "Meezan Bank",
            text = "PKR 4,800.00 debited from A/C *4910 for IBFT to HASSAN RAZA on 20-Oct-26. STAN: 592014"
        )
        assertNotNull(meezan)
        assertEquals(4800.0, meezan!!.amount, 0.001)
        assertEquals("HASSAN RAZA", meezan.counterparty)
        assertEquals("592014", meezan.transactionId)

        val gpay = TransactionParser.parse(
            packageName = "com.google.android.apps.walletnfcrel",
            title = "Google Wallet",
            text = "Paid Rs. 1,450 at Espresso Coffee with Visa ••4021"
        )
        assertNotNull(gpay)
        assertEquals(TransactionDirection.PAID, gpay!!.direction)
        assertEquals("Espresso Coffee", gpay.counterparty)
    }
}
