package com.paynote.app.receiver

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.paynote.app.data.TransactionDatabase
import com.paynote.app.service.TransactionNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receives the user's inline purpose text from the notification shade's RemoteInput field
 * (just like Shizuku's wireless debugging code receiver), saves the purpose into Room,
 * and updates the notification in-place to confirm it was saved.
 */
class PurposeReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (transactionId == -1L || notificationId == -1) return

        val purposeText: String = when (intent.action) {
            ACTION_REPLY_PURPOSE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                results?.getCharSequence(TransactionNotificationService.KEY_TEXT_REPLY)
                    ?.toString()
                    ?.trim()
                    .takeUnless { it.isNullOrEmpty() }
                    ?: "General"
            }
            ACTION_QUICK_CATEGORY -> {
                intent.getStringExtra(EXTRA_QUICK_PURPOSE) ?: "General"
            }
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = TransactionDatabase.getInstance(context).transactionDao()
                dao.updatePurpose(transactionId, purposeText)
                val updatedTxn = dao.getById(transactionId)

                // Update notification in-place to stop the RemoteInput spinner and show confirmation
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val summaryLine = if (updatedTxn != null) {
                    "Saved: ${updatedTxn.currency} ${updatedTxn.amount.toInt()} • ${updatedTxn.counterparty} ($purposeText)"
                } else {
                    "Saved purpose: $purposeText"
                }

                val confirmationNotification = NotificationCompat.Builder(
                    context,
                    TransactionNotificationService.CHANNEL_ID
                )
                    .setSmallIcon(android.R.drawable.checkbox_on_background)
                    .setContentTitle("Transaction Logged ✓")
                    .setContentText(summaryLine)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .setTimeoutAfter(2500L) // Automatically dismisses after 2.5 seconds
                    .build()

                manager.notify(notificationId, confirmationNotification)
                delay(2600L)
                manager.cancel(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY_PURPOSE = "com.paynote.app.ACTION_REPLY_PURPOSE"
        const val ACTION_QUICK_CATEGORY = "com.paynote.app.ACTION_QUICK_CATEGORY"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_QUICK_PURPOSE = "extra_quick_purpose"
    }
}
