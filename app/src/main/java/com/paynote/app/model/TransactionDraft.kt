package com.paynote.app.model

import java.math.BigDecimal
import java.util.Locale

/** Values supplied by the user. Notification facts remain intact when an entry is corrected. */
data class TransactionDraft(
    val wallet: String = "",
    val counterparty: String = "",
    val direction: String = "SENT",
    val amount: String = "",
    val currency: String = "PKR",
    val category: String = "",
    val purpose: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
) {
    fun validationError(): String? = when {
        direction !in TransactionDirection.entries.map { it.name } -> "Choose Incoming or Outgoing."
        !amount.matches(Regex("[0-9]{1,12}(\\.[0-9]{1,2})?")) || amount.toBigDecimalOrNull()?.let { it <= BigDecimal.ZERO } != false -> "Enter an amount greater than zero, with up to two decimal places."
        !currency.trim().matches(Regex("[A-Za-z]{3}")) -> "Use a three-letter currency code, such as PKR."
        wallet.isBlank() -> "Enter the wallet or source name."
        counterparty.isBlank() -> "Enter the sender, recipient or merchant name."
        wallet.trim().length > 100 || counterparty.trim().length > 150 -> "Please use a shorter name."
        category.trim().length > 100 || purpose.trim().length > 1000 -> "Please shorten the category or purpose."
        timestampMillis <= 0 -> "Choose a valid date."
        else -> null
    }

    fun toManualEntry(): TransactionEntity {
        require(validationError() == null) { validationError().orEmpty() }
        return TransactionEntity(
            provider = wallet.trim(), sourcePackage = MANUAL_SOURCE,
            currency = currency.trim().uppercase(Locale.ROOT), amount = amount.toDouble(),
            direction = direction, counterparty = counterparty.trim(), transactionId = null,
            purpose = purpose.trim().ifEmpty { null }, isLogged = true,
            timestampMillis = timestampMillis, rawNotificationText = "Added manually",
            category = category.trim().ifEmpty { null }
        )
    }

    companion object {
        fun from(row: TransactionEntity) = TransactionDraft(
            row.walletName, row.partyName, row.flowDirection, BigDecimal.valueOf(row.amount).stripTrailingZeros().toPlainString(),
            row.currency, row.category.orEmpty(), row.purpose.orEmpty(), row.timestampMillis
        )
    }
}

val transactionCategories = listOf("Food & Dining", "Transport", "Bills", "Shopping", "Transfer", "Salary", "Health", "Other")
