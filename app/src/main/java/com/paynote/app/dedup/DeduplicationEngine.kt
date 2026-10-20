package com.paynote.app.dedup

import com.paynote.app.model.ParsedNotification
import com.paynote.app.model.PaymentProvider
import com.paynote.app.model.NotificationSources
import com.paynote.app.model.TransactionEntity
import com.paynote.app.model.isManual
import java.util.Locale
import kotlin.math.abs

/**
 * Prevents duplicate prompts when a single real-world transaction triggers multiple notifications:
 * - Scenario A: Banking/Wallet App push notification + Carrier SMS alert arrive within seconds/minutes.
 * - Scenario B: App updates/reposts its own notification in the shade.
 * - Scenario C: One notification has a full TID ("049281746281") while the other has a truncated TID ("746281") or no TID.
 */
object DeduplicationEngine {

    /**
     * Sliding time window (3 minutes) within which two notifications of the exact same amount
     * and direction are evaluated as potential cross-channel (App + SMS) duplicates.
     */
    const val DEDUP_WINDOW_MILLIS = 3 * 60 * 1000L // 180,000 ms (3 minutes)
    private const val UNKNOWN_PARTY_MATCH_WINDOW_MILLIS = 30_000L

    sealed class DedupDecision {
        /** Unique transaction: save to DB and trigger the inline RemoteInput prompt. */
        data object NewTransaction : DedupDecision()

        /**
         * Duplicate of an existing recent transaction [existingId].
         * Do NOT show a second prompt; optionally enrich the existing record if the second
         * notification carries a TID or a more complete counterparty name.
         */
        data class DuplicateOf(
            val existingId: Long,
            val reason: String,
            val newlyDiscoveredTid: String?,
            val betterCounterparty: String,
            val preferredNotification: ParsedNotification? = null
        ) : DedupDecision()
    }

    /**
     * Evaluates [incoming] against [recentTransactions] (fetched from Room within [DEDUP_WINDOW_MILLIS]).
     */
    fun evaluate(
        incoming: ParsedNotification,
        recentTransactions: List<TransactionEntity>
    ): DedupDecision {
        val genericProviders = setOf(PaymentProvider.SMS_ALERT.displayName, PaymentProvider.UNKNOWN.displayName)
        val candidates = recentTransactions.filter { existing ->
            val timeDelta = abs(incoming.timestampMillis - existing.timestampMillis)
            val sameProvider = incoming.provider.displayName == existing.provider
            !existing.isManual && timeDelta <= DEDUP_WINDOW_MILLIS &&
                incoming.currency.equals(existing.currency, ignoreCase = true) &&
                (sameProvider || incoming.provider.displayName in genericProviders || existing.provider in genericProviders) &&
                abs(incoming.amount - existing.amount) < 0.01 &&
                isCompatibleDirection(incoming.direction.name, existing.direction)
        }

        // Search all references before considering weaker name/amount evidence.
        candidates.firstOrNull { isMatchingTransactionId(incoming.transactionId, it.transactionId) }?.let {
            return duplicateDecision(incoming, it, "Matched transaction reference")
        }

        val fallbackMatches = candidates.filter { existing ->
            val sameProvider = incoming.provider.displayName == existing.provider
            val conflictingReferences = !incoming.transactionId.isNullOrBlank() && !existing.transactionId.isNullOrBlank()
            val incomingSms = NotificationSources.isSmsPackage(incoming.sourcePackage)
            val existingSms = NotificationSources.isSmsPackage(existing.sourcePackage)
            val missingName = !hasCounterpartyName(incoming.counterparty) || !hasCounterpartyName(existing.counterparty)
            val closeCrossChannel = incomingSms != existingSms && missingName &&
                abs(incoming.timestampMillis - existing.timestampMillis) <= UNKNOWN_PARTY_MATCH_WINDOW_MILLIS
            sameProvider && !conflictingReferences &&
                (areCounterpartiesCompatible(incoming.counterparty, existing.counterparty) || closeCrossChannel)
        }
        // An unnamed SMS may be upgraded only when there is one possible transaction.
        if (fallbackMatches.size == 1) {
            return duplicateDecision(incoming, fallbackMatches.single(), "Matched bank, value, direction and notification window")
        }
        return DedupDecision.NewTransaction
    }

    private fun duplicateDecision(incoming: ParsedNotification, existing: TransactionEntity, reason: String): DedupDecision.DuplicateOf {
        val incomingSms = NotificationSources.isSmsPackage(incoming.sourcePackage)
        val existingSms = NotificationSources.isSmsPackage(existing.sourcePackage)
        val incomingNamed = hasCounterpartyName(incoming.counterparty)
        val existingNamed = hasCounterpartyName(existing.counterparty)
        val selectedName = when {
            !incomingNamed -> existing.counterparty
            !existingNamed -> incoming.counterparty
            incomingSms != existingSms -> if (incomingSms) existing.counterparty else incoming.counterparty
            incoming.counterparty.length > existing.counterparty.length -> incoming.counterparty
            else -> existing.counterparty
        }
        val preferIncoming = (!incomingSms && existingSms) ||
            (incomingSms == existingSms && selectedName == incoming.counterparty && selectedName != existing.counterparty)
        val reference = listOfNotNull(existing.transactionId, incoming.transactionId).maxByOrNull { it.length }
        return DedupDecision.DuplicateOf(existing.id, reason, reference, selectedName, incoming.takeIf { preferIncoming })
    }

    private fun hasCounterpartyName(name: String): Boolean = name.isNotBlank() && name.lowercase(Locale.ROOT) !in setOf(
        "unknown counterparty", "money received", "payment received", "raast incoming payment", "sadapay", "nayapay", "jazzcash", "meezan bank"
    )

    /**
     * Checks if two TIDs match, accounting for banks that omit leading zeros or show only the last 6 digits in SMS/Push.
     */
    fun isMatchingTransactionId(tidA: String?, tidB: String?): Boolean {
        val cleanA = tidA?.filter { it.isLetterOrDigit() }?.uppercase(Locale.ROOT)?.trimStart('0').orEmpty()
        val cleanB = tidB?.filter { it.isLetterOrDigit() }?.uppercase(Locale.ROOT)?.trimStart('0').orEmpty()
        if (cleanA.length < 5 || cleanB.length < 5) return false
        if (cleanA == cleanB) return true
        // One entire shorter reference must match; sharing six trailing digits alone
        // does not make two different full reference numbers the same transaction.
        if (cleanA.length >= 6 && cleanB.length >= 6) {
            return cleanA.endsWith(cleanB) || cleanB.endsWith(cleanA)
        }
        return false
    }

    private fun isCompatibleDirection(dirA: String, dirB: String): Boolean {
        if (dirA == dirB) return true
        // App notification might say "Paid Rs. 1500 to X" (PAID) while SMS says "Rs. 1500 debited from A/C" (SENT)
        val outgoing = setOf("SENT", "PAID")
        return dirA in outgoing && dirB in outgoing
    }

    /**
     * Fuzzy counterparty check: handles "Ali Khan" vs "MUHAMMAD ALI KHAN" or masked account numbers.
     */
    fun areCounterpartiesCompatible(nameA: String, nameB: String): Boolean {
        val normA = normalizeName(nameA)
        val normB = normalizeName(nameB)
        if (normA.isEmpty() || normB.isEmpty()) return true
        if (normA == normB) return true
        if (normA.contains(normB) || normB.contains(normA)) return true

        val tokensA = normA.split(" ").filter { it.length >= 3 }.toSet()
        val tokensB = normB.split(" ").filter { it.length >= 3 }.toSet()
        if (tokensA.isNotEmpty() && tokensB.isNotEmpty()) {
            val overlap = tokensA.intersect(tokensB)
            if (overlap.isNotEmpty()) return true
        }
        return false
    }

    private fun normalizeName(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

}
