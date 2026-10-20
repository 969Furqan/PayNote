package com.paynote.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.paynote.app.data.TransactionDatabase
import com.paynote.app.dedup.DeduplicationEngine
import com.paynote.app.model.ParsedNotification
import com.paynote.app.model.NotificationSources
import com.paynote.app.model.PaymentProvider
import com.paynote.app.model.TransactionDirection
import com.paynote.app.model.TransactionEntity
import com.paynote.app.parser.TransactionParser
import com.paynote.app.receiver.PurposeReplyReceiver
import com.paynote.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.NumberFormat
import java.util.Locale

/**
 * Listens for incoming notifications from Google Pay, SadaPay, NayaPay, JazzCash, Meezan Bank, and SMS.
 * Deduplicates across App + SMS alerts and immediately posts a Shizuku-style inline RemoteInput prompt
 * so you can type the transaction purpose right inside the notification bar.
 */
class TransactionNotificationService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        // Ignore our own notifications to prevent infinite loops
        if (packageName == applicationContext.packageName) return
        if (sbn.isOngoing) return

        // Filter to target payment, banking, and SMS apps
        if (!TransactionParser.isSupportedPackage(packageName)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val parsed = TransactionParser.parse(
            packageName = packageName,
            title = title,
            text = text,
            timestampMillis = sbn.postTime
        ) ?: return

        serviceScope.launch {
            processParsedNotification(applicationContext, parsed)
        }
    }

    companion object {
        const val CHANNEL_ID = "paynote_prompt_channel"
        const val KEY_TEXT_REPLY = "key_transaction_purpose_reply"
        const val SMS_FALLBACK_DELAY_MILLIS = 5_000L
        private val processingMutex = Mutex()

        /** Reparse known fallback details from saved notifications; preserve all user purposes and amounts. */
        suspend fun repairIncompleteTransactions(context: Context) = processingMutex.withLock {
            val dao = TransactionDatabase.getInstance(context).transactionDao()
            for (row in dao.getTransactionsWithIncompleteDetails()) {
                val separator = row.rawNotificationText.indexOf(": ")
                if (separator < 0) continue
                val parsed = TransactionParser.parse(
                    row.sourcePackage, row.rawNotificationText.substring(0, separator),
                    row.rawNotificationText.substring(separator + 2), row.timestampMillis
                ) ?: continue
                val isSadaPayIncomingFallback = row.provider == "SadaPay" &&
                    row.counterparty == "Money Received" && parsed.direction == TransactionDirection.RECEIVED
                if (parsed.counterparty != "Unknown Counterparty" &&
                    parsed.provider.displayName == row.provider && parsed.currency == row.currency &&
                    parsed.amount == row.amount && (parsed.direction.name == row.direction || isSadaPayIncomingFallback)) {
                    dao.repairTransactionDetails(row.id, row.counterparty, row.direction, parsed.counterparty, parsed.direction.name)
                }
            }
        }

        /**
         * Runs deduplication against Room DB, inserts new transactions, and triggers the
         * Shizuku-style inline RemoteInput notification prompt.
         * Can also be called directly from MainActivity's built-in Test Simulator.
         */
        suspend fun processParsedNotification(
            context: Context,
            parsed: ParsedNotification
        ): DeduplicationEngine.DedupDecision {
          // Wait outside the lock so the app's richer alert can be handled immediately.
          if (NotificationSources.isSmsPackage(parsed.sourcePackage)) delay(SMS_FALLBACK_DELAY_MILLIS)
          return processingMutex.withLock {
            val dao = TransactionDatabase.getInstance(context).transactionDao()
            val windowStart = parsed.timestampMillis - DeduplicationEngine.DEDUP_WINDOW_MILLIS
            val recentTxns = dao.getRecentTransactionsSince(windowStart)

            when (val decision = DeduplicationEngine.evaluate(parsed, recentTxns)) {
                is DeduplicationEngine.DedupDecision.DuplicateOf -> {
                    val preferred = decision.preferredNotification
                    dao.enrichDuplicateMetadata(
                        id = decision.existingId,
                        newTid = decision.newlyDiscoveredTid,
                        newCounterparty = decision.betterCounterparty,
                        preferredPackage = preferred?.sourcePackage,
                        preferredText = preferred?.let { "${it.rawTitle}: ${it.rawText}".trim() },
                        preferredProvider = preferred?.provider?.displayName,
                        preferredDirection = preferred?.direction?.name
                    )
                    val updated = dao.getById(decision.existingId)
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (updated != null && !updated.isLogged && manager.activeNotifications.any { it.id == updated.id.toInt() }) {
                        showInlinePurposePromptNotification(context, updated.id, parsed.copy(
                            provider = PaymentProvider.entries.firstOrNull { it.displayName == updated.provider } ?: parsed.provider,
                            sourcePackage = updated.sourcePackage, counterparty = updated.counterparty,
                            transactionId = updated.transactionId, direction = TransactionDirection.valueOf(updated.direction)
                        ), silentUpdate = true)
                    }
                    return@withLock decision
                }

                is DeduplicationEngine.DedupDecision.NewTransaction -> {
                    val entity = TransactionEntity(
                        provider = parsed.provider.displayName,
                        sourcePackage = parsed.sourcePackage,
                        currency = parsed.currency,
                        amount = parsed.amount,
                        direction = parsed.direction.name,
                        counterparty = parsed.counterparty,
                        transactionId = parsed.transactionId,
                        purpose = null,
                        isLogged = false,
                        timestampMillis = parsed.timestampMillis,
                        rawNotificationText = "${parsed.rawTitle}: ${parsed.rawText}".trim()
                    )
                    val insertedId = dao.insert(entity)
                    showInlinePurposePromptNotification(context, insertedId, parsed)
                    return@withLock decision
                }
            }
          }
        }

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Transaction Purpose Prompts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Prompts you with an inline text box to log the purpose of detected transactions"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }

        /**
         * Posts a heads-up notification with a RemoteInput inline text field (identical to how
         * Shizuku prompts for the wireless debugging pairing code in the notification shade).
         */
        fun showInlinePurposePromptNotification(
            context: Context,
            transactionDbId: Long,
            parsed: ParsedNotification,
            silentUpdate: Boolean = false
        ) {
            ensureNotificationChannel(context)
            val notificationId = transactionDbId.toInt()

            val formattedAmount = "${parsed.currency} ${
                NumberFormat.getNumberInstance(Locale.US).apply {
                    maximumFractionDigits = 2
                    minimumFractionDigits = if (parsed.amount % 1.0 == 0.0) 0 else 2
                }.format(parsed.amount)
            }"

            val verb = when (parsed.direction) {
                TransactionDirection.SENT -> "Sent to"
                TransactionDirection.PAID -> "Paid at"
                TransactionDirection.RECEIVED -> "Received from"
            }

            val promptTitle = "$formattedAmount • $verb ${parsed.counterparty}"
            val tidSuffix = parsed.transactionId?.let { " • TID $it" }.orEmpty()
            val promptBody = "via ${parsed.provider.displayName}$tidSuffix — Tap 'Add Purpose' below to log what this was for."

            // 1. Primary Shizuku-style RemoteInput inline action ("Add Purpose")
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Enter purpose (e.g., Dinner, Fuel, Rent)...")
                .build()

            val replyIntent = Intent(context, PurposeReplyReceiver::class.java).apply {
                action = PurposeReplyReceiver.ACTION_REPLY_PURPOSE
                putExtra(PurposeReplyReceiver.EXTRA_TRANSACTION_ID, transactionDbId)
                putExtra(PurposeReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }

            val replyPendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 1,
                replyIntent,
                replyPendingIntentFlags
            )

            val inlineReplyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_edit,
                "Add Purpose",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()

            // 2. One-tap Quick Category Action ("Food & Dining")
            val quickFoodIntent = Intent(context, PurposeReplyReceiver::class.java).apply {
                action = PurposeReplyReceiver.ACTION_QUICK_CATEGORY
                putExtra(PurposeReplyReceiver.EXTRA_TRANSACTION_ID, transactionDbId)
                putExtra(PurposeReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(PurposeReplyReceiver.EXTRA_QUICK_PURPOSE, "Food & Dining")
            }
            val quickFoodPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 2,
                quickFoodIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 3. One-tap "Skip" Action (marks logged as "General")
            val skipIntent = Intent(context, PurposeReplyReceiver::class.java).apply {
                action = PurposeReplyReceiver.ACTION_QUICK_CATEGORY
                putExtra(PurposeReplyReceiver.EXTRA_TRANSACTION_ID, transactionDbId)
                putExtra(PurposeReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(PurposeReplyReceiver.EXTRA_QUICK_PURPOSE, "General / Uncategorized")
            }
            val skipPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 3,
                skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Tapping the notification body opens MainActivity focused on this transaction
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(PurposeReplyReceiver.EXTRA_TRANSACTION_ID, transactionDbId)
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                notificationId * 10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(promptTitle)
                .setContentText(promptBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(promptBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(silentUpdate)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .addAction(inlineReplyAction)
                .addAction(android.R.drawable.ic_menu_compass, "Food & Dining", quickFoodPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Skip", skipPendingIntent)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        }
    }
}
