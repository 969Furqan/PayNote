package com.paynote.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionDirection {
    SENT,       // Money sent / transferred out
    PAID,       // POS / QR / Online merchant payment
    RECEIVED    // Money received / credited
}

enum class PaymentProvider(val displayName: String, val isSmsChannel: Boolean = false) {
    SADAPAY("SadaPay"),
    NAYAPAY("NayaPay"),
    JAZZCASH("JazzCash"),
    MEEZAN_BANK("Meezan Bank"),
    GOOGLE_PAY("Google Pay"),
    SMS_ALERT("Bank/Wallet SMS", isSmsChannel = true),
    UNKNOWN("Other")
}

/**
 * Represents the structured fields extracted from an incoming notification before deduplication.
 */
data class ParsedNotification(
    val provider: PaymentProvider,
    val sourcePackage: String,
    val currency: String,          // e.g., "PKR", "USD"
    val amount: Double,            // Normalized numeric amount, e.g., 2500.0
    val direction: TransactionDirection,
    val counterparty: String,      // Receiver, sender, or merchant name
    val transactionId: String?,    // TID / RRN / STAN / Reference number if present
    val timestampMillis: Long,
    val rawTitle: String,
    val rawText: String
)

/**
 * Room Entity persisted in local SQLite database.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["transactionId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String,
    val sourcePackage: String,
    val currency: String,
    val amount: Double,
    val direction: String,         // SENT, PAID, RECEIVED
    val counterparty: String,
    val transactionId: String?,    // Nullable TID / Reference
    val purpose: String?,          // Null until the user replies via the inline notification prompt
    val isLogged: Boolean = false, // True once user enters a purpose or taps Skip
    val timestampMillis: Long,
    val rawNotificationText: String,
    // Keep user corrections separate so detection and duplicate matching retain the original facts.
    val customProvider: String? = null,
    val customCounterparty: String? = null,
    val customDirection: String? = null,
    val category: String? = null
)

const val MANUAL_SOURCE = "com.paynote.manual"
val TransactionEntity.walletName: String get() = customProvider ?: provider
val TransactionEntity.partyName: String get() = customCounterparty ?: counterparty
val TransactionEntity.flowDirection: String get() = customDirection ?: direction
val TransactionEntity.isManual: Boolean get() = sourcePackage == MANUAL_SOURCE
val TransactionEntity.sourceLabel: String get() = when {
    isManual -> "Manual entry"
    NotificationSources.isSmsPackage(sourcePackage) -> "SMS fallback"
    else -> "App notification"
}
