package com.paynote.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.paynote.app.R
import com.paynote.app.data.TransactionDatabase
import com.paynote.app.model.TransactionEntity
import com.paynote.app.ui.MainActivity
import com.paynote.app.ui.QuickEntryActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class PayNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { updateAll(context, TransactionDatabase.getInstance(context).transactionDao().getRecentTransactionsSince(0)) }
            finally { pending.finish() }
        }
    }

    companion object {
        fun updateAll(context: Context, rows: List<TransactionEntity>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PayNoteWidget::class.java))
            if (ids.isEmpty()) return
            val summary = widgetSummary(rows)
            val number = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }
            val view = RemoteViews(context.packageName, R.layout.paynote_widget).apply {
                setTextViewText(R.id.widget_period, context.getString(R.string.widget_period, summary.currency))
                setTextViewText(R.id.widget_in, number.format(summary.incoming))
                setTextViewText(R.id.widget_out, number.format(summary.outgoing))
                setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(context, 400,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                setOnClickPendingIntent(R.id.widget_add_in, entryIntent(context, "RECEIVED"))
                setOnClickPendingIntent(R.id.widget_add_out, entryIntent(context, "SENT"))
            }
            manager.updateAppWidget(ids, view)
        }

        fun entryIntent(context: Context, direction: String): PendingIntent = PendingIntent.getActivity(context,
            if (direction == "RECEIVED") 401 else 402,
            Intent(context, QuickEntryActivity::class.java).putExtra(QuickEntryActivity.EXTRA_DIRECTION, direction)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun requestPin(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(context, PayNoteWidget::class.java), null, null)
            else Toast.makeText(context, "Long-press your home screen, open Widgets and choose PayNote.", Toast.LENGTH_LONG).show()
        }
    }
}
