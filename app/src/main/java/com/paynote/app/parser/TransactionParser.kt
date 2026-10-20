package com.paynote.app.parser

import com.paynote.app.model.ParsedNotification
import com.paynote.app.model.PaymentProvider
import com.paynote.app.model.TransactionDirection
import java.util.Locale

/**
 * Multi-provider transaction parser supporting:
 * 1. SadaPay (App notifications & SMS)
 * 2. NayaPay (App notifications & SMS)
 * 3. JazzCash (App notifications & 8558 SMS)
 * 4. Meezan Bank (App notifications & 8118 / 9900 SMS)
 * 5. Google Pay / Google Wallet (App notifications)
 * 6. Standard Pakistani & International Bank SMS Alerts
 */
object TransactionParser {

    // Known Android package names for target apps
    private val PACKAGE_MAP = mapOf(
        "com.sadapay.app" to PaymentProvider.SADAPAY,
        "com.nayapay.app" to PaymentProvider.NAYAPAY,
        "com.techlogix.mobilinkcustomer" to PaymentProvider.JAZZCASH,
        "pk.com.meezanbank.mobilebanking" to PaymentProvider.MEEZAN_BANK,
        "com.meezanbank.app" to PaymentProvider.MEEZAN_BANK,
        "invo8.meezan.mb" to PaymentProvider.MEEZAN_BANK,
        "com.ofss.tx.meezan" to PaymentProvider.MEEZAN_BANK,
        "com.google.android.apps.walletnfcrel" to PaymentProvider.GOOGLE_PAY,
        "com.google.android.apps.nbu.paisa.user" to PaymentProvider.GOOGLE_PAY,
        // Common SMS / Messaging packages on Android (Google Messages, Samsung Messages, AOSP, Xiaomi, etc.)
        "com.google.android.apps.messaging" to PaymentProvider.SMS_ALERT,
        "com.samsung.android.messaging" to PaymentProvider.SMS_ALERT,
        "com.android.mms" to PaymentProvider.SMS_ALERT
    )

    // Non-transactional keywords to ignore (OTPs, login alerts, promos, failed txns)
    private val IGNORE_PATTERNS = listOf(
        Regex("""\b(otp|one[- ]time password|verification code|pin code|login attempt|failed|declined|insufficient|promo|cashback offer|discount)\b""", RegexOption.IGNORE_CASE)
    )

    // Extracts TID / RRN / Ref / STAN numbers (e.g., "TID: 049281746281", "Ref# 839201", "STAN 482910")
    private val TID_REGEX = Regex(
        """(?:TID|Txn\s*ID|Transaction\s*ID|Ref(?:erence)?(?:\s*No|\s*ID)?|RRN|STAN)\s*[:#\-]?\s*([A-Z0-9\-]{5,24})""",
        RegexOption.IGNORE_CASE
    )

    // SadaPay incoming push: "A Sender sent you PKR 10".
    private val SENT_TO_YOU_REGEX = Regex(
        """^([A-Za-z][A-Za-z0-9\s.&'\-()]{1,60}?)\s+(?:sent|transferred)\s+you\s+(?:PKR|Rs\.?|USD|EUR|GBP|AED|\$)\s*[0-9]""",
        RegexOption.IGNORE_CASE
    )

    // Extracts Currency + Amount: e.g., "Rs. 2,500.00", "PKR 1,250", "$14.99", "USD 45.00", "Rs 500"
    private val AMOUNT_REGEX = Regex(
        """(?:(PKR|Rs\.?|USD|EUR|GBP|AED)|([\$\u20AC\u00A3]))\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Ordered rules for extracting direction + counterparty across SadaPay, NayaPay, JazzCash, Meezan, Google Pay, and SMS
    private data class ExtractionRule(
        val direction: TransactionDirection,
        val regex: Regex,
        val counterpartyGroupIndex: Int
    )

    private val EXTRACTION_RULES = listOf(
        // 1. SadaPay / NayaPay / JazzCash Outgoing Transfer:
        // e.g. "You sent Rs. 2,500 to Ali Khan", "Rs. 1,500.00 sent to MUHAMMAD USMAN (SadaPay/Meezan) TID: 123456"
        // e.g. "transferred PKR 5,000 to Ahmed Raza"
        ExtractionRule(
            direction = TransactionDirection.SENT,
            regex = Regex(
                """(?:sent|transferred|paid)(?:\s+(?:PKR|Rs\.?|USD|\$)\s*[0-9,]+(?:\.[0-9]{1,2})?)?\s+to\s+([A-Za-z0-9][A-Za-z0-9\s.&'\-()/*]{1,45}?)(?=\s*(?:via\b|from\b|on\b|at\b|TID\b|Txn\b|Ref\b|RRN\b|STAN\b|A/C\b|Account\b|Fee\b|Balance\b|New\s+Balance|\.|,|;|$))""",
                RegexOption.IGNORE_CASE
            ),
            counterpartyGroupIndex = 1
        ),

        // 2. Incoming Transfer (SadaPay / NayaPay / JazzCash / Meezan):
        // e.g. "You received Rs. 3,000 from Sara Ahmed", "credited with PKR 10,000.00 from ZAIN ALI"
        ExtractionRule(
            direction = TransactionDirection.RECEIVED,
            regex = Regex(
                """(?:received|credited(?:\s+with)?)(?:\s+(?:PKR|Rs\.?|USD|\$)\s*[0-9,]+(?:\.[0-9]{1,2})?)?\s+from\s+([A-Za-z0-9][A-Za-z0-9\s.&'\-()/*]{1,45}?)(?=\s*(?:via\b|in\b|on\b|at\b|AC\b|A/C\b|Account\b|TID\b|Txn\b|Ref\b|RRN\b|STAN\b|Balance\b|New\s+Balance|\.|,|;|$))""",
                RegexOption.IGNORE_CASE
            ),
            counterpartyGroupIndex = 1
        ),

        // 3. Google Pay / Card / POS / QR Payment:
        // e.g. "Paid $12.50 at Starbucks", "You spent Rs. 1,850 at Foodpanda", "charged PKR 4,200 at Khaadi"
        ExtractionRule(
            direction = TransactionDirection.PAID,
            regex = Regex(
                """(?:paid|spent|charged|purchase(?:\s+of)?)(?:\s+(?:PKR|Rs\.?|USD|\$)\s*[0-9,]+(?:\.[0-9]{1,2})?)?\s+(?:at|to)\s+([A-Za-z0-9][A-Za-z0-9\s.&'\-*/]{1,45}?)(?=\s*(?:with\b|using\b|via\b|from\b|on\b|TID\b|Txn\b|Ref\b|RRN\b|STAN\b|Card\b|Balance\b|\.|,|;|$))""",
                RegexOption.IGNORE_CASE
            ),
            counterpartyGroupIndex = 1
        ),

        // 4. Meezan Bank / Raast / IBFT SMS Alert format:
        // e.g. "PKR 5,000.00 debited from A/C *1234 for IBFT to ALI KHAN on 20-Oct-26. STAN: 938271"
        // e.g. "debited from A/C ... to HASSAN RAZA"
        ExtractionRule(
            direction = TransactionDirection.SENT,
            regex = Regex(
                """debited\s+from\s+(?:your\s+)?(?:A/C|Account)[^.]{0,60}?\b(?:to|at)\s+([A-Za-z0-9][A-Za-z0-9\s.&'\-*/]{1,40}?)(?=\s*(?:on\b|dated\b|at\s+[0-9]|TID\b|Txn\b|Ref\b|RRN\b|STAN\b|Avail\b|Bal\b|\.|,|;|$))""",
                RegexOption.IGNORE_CASE
            ),
            counterpartyGroupIndex = 1
        ),

        // 5. Google Wallet Title-based format:
        // Often Google Wallet puts the merchant name in the notification Title ("McDonald's") and "$8.40 with Visa ••1234" in text
        ExtractionRule(
            direction = TransactionDirection.PAID,
            regex = Regex(
                """(?:PKR|Rs\.?|USD|\$)\s*[0-9,]+(?:\.[0-9]{1,2})?\s+with\s+(?:Visa|Mastercard|PayPak|Card)""",
                RegexOption.IGNORE_CASE
            ),
            counterpartyGroupIndex = 0 // Signals fallback to notification title
        )
    )

    fun isSupportedPackage(packageName: String): Boolean {
        return PACKAGE_MAP.containsKey(packageName) ||
            packageName.contains("messaging", ignoreCase = true) ||
            packageName.contains("mms", ignoreCase = true) ||
            packageName.contains("sadapay", ignoreCase = true) ||
            packageName.contains("nayapay", ignoreCase = true) ||
            packageName.contains("jazzcash", ignoreCase = true) ||
            packageName.contains("mobilink", ignoreCase = true) ||
            packageName.contains("meezan", ignoreCase = true) ||
            packageName.contains("wallet", ignoreCase = true)
    }

    fun resolveProvider(packageName: String, title: String, text: String): PaymentProvider {
        val mapped = PACKAGE_MAP[packageName]
        val combined = "$title $text"

        // Even if delivered via SMS, detect if the SMS body/sender belongs to a known bank/wallet
        if (mapped == null || mapped == PaymentProvider.SMS_ALERT) {
            return when {
                combined.contains("SadaPay", ignoreCase = true) -> PaymentProvider.SADAPAY
                combined.contains("NayaPay", ignoreCase = true) -> PaymentProvider.NAYAPAY
                combined.contains("JazzCash", ignoreCase = true) || title.contains("8558") -> PaymentProvider.JAZZCASH
                combined.contains("Meezan", ignoreCase = true) || title.contains("8118") || title.contains("9900") -> PaymentProvider.MEEZAN_BANK
                combined.contains("Google Pay", ignoreCase = true) || combined.contains("GPay", ignoreCase = true) -> PaymentProvider.GOOGLE_PAY
                else -> mapped ?: PaymentProvider.UNKNOWN
            }
        }
        return mapped
    }

    /**
     * Parses a notification's title and text into a structured [ParsedNotification], or returns null
     * if the notification is not a financial transaction (e.g., OTP, promo, login alert).
     */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        timestampMillis: Long = System.currentTimeMillis()
    ): ParsedNotification? {
        val safeTitle = title?.trim().orEmpty()
        val safeText = text?.trim().orEmpty()
        if (safeTitle.isEmpty() && safeText.isEmpty()) return null

        val combined = "$safeTitle. $safeText"

        // Ignore OTPs, promos, failed alerts
        if (IGNORE_PATTERNS.any { it.containsMatchIn(combined) }) {
            return null
        }

        // 1. Extract Currency & Amount
        val amountMatch = AMOUNT_REGEX.find(combined) ?: return null
        val rawCurrencyCode = amountMatch.groups[1]?.value ?: amountMatch.groups[2]?.value ?: "PKR"
        val normalizedCurrency = normalizeCurrency(rawCurrencyCode)
        val rawAmountStr = amountMatch.groups[3]?.value?.replace(",", "") ?: return null
        val amount = rawAmountStr.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        // 2. Extract Transaction ID (TID / RRN / STAN / Ref) if available
        val tidMatch = TID_REGEX.find(combined)
        val transactionId = tidMatch?.groups?.get(1)?.value?.trim()

        // 3. Extract Direction & Counterparty
        val sentToYou = SENT_TO_YOU_REGEX.find(safeText)
        var direction: TransactionDirection? = if (sentToYou != null) TransactionDirection.RECEIVED else null
        var counterparty: String? = sentToYou?.groups?.get(1)?.value

        for (rule in EXTRACTION_RULES) {
            if (direction != null) break
            val match = rule.regex.find(combined)
            if (match != null) {
                direction = rule.direction
                counterparty = if (rule.counterpartyGroupIndex == 0) {
                    safeTitle
                } else {
                    match.groups[rule.counterpartyGroupIndex]?.value
                }
                break
            }
        }

        // Fallback heuristics for direction if a bank SMS uses terse wording (e.g., "Debit Alert" / "Credit Alert")
        if (direction == null) {
            val lower = combined.lowercase(Locale.ROOT)
            direction = when {
                lower.contains("debited") || lower.contains("sent") || lower.contains("transferred") -> TransactionDirection.SENT
                lower.contains("paid") || lower.contains("spent") || lower.contains("purchase") || lower.contains("pos") -> TransactionDirection.PAID
                lower.contains("credited") || lower.contains("received") -> TransactionDirection.RECEIVED
                else -> return null // Not confidently a transaction
            }
        }

        val cleanedCounterparty = cleanCounterpartyName(counterparty ?: safeTitle)
        val provider = resolveProvider(packageName, safeTitle, safeText)

        return ParsedNotification(
            provider = provider,
            sourcePackage = packageName,
            currency = normalizedCurrency,
            amount = amount,
            direction = direction,
            counterparty = cleanedCounterparty,
            transactionId = transactionId,
            timestampMillis = timestampMillis,
            rawTitle = safeTitle,
            rawText = safeText
        )
    }

    private fun normalizeCurrency(raw: String): String {
        val upper = raw.uppercase(Locale.ROOT).replace(".", "").trim()
        return when (upper) {
            "RS", "PKR" -> "PKR"
            "$", "USD" -> "USD"
            "€", "EUR" -> "EUR"
            "£", "GBP" -> "GBP"
            else -> upper
        }
    }

    fun cleanCounterpartyName(raw: String): String {
        val stripped = raw
            .replace(Regex("""\s*\([^)]*\)\s*"""), " ") // remove trailing bank/account parens if noisy
            .replace(Regex("""\b(?:A/C|Account|IBFT|Raast)\b.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', ',', '-', ':')
        return if (stripped.isBlank()) "Unknown Counterparty" else stripped
    }
}
