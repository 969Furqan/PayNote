package com.paynote.app

import android.app.Application
import com.paynote.app.data.TransactionDatabase
import com.paynote.app.widget.PayNoteWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PayNoteApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            TransactionDatabase.getInstance(this@PayNoteApplication).transactionDao().observeAllTransactions().collectLatest {
                PayNoteWidget.updateAll(this@PayNoteApplication, it)
            }
        }
    }
}
