package com.paynote.app.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.paynote.app.data.TransactionDatabase
import com.paynote.app.model.walletName
import com.paynote.app.widget.PayNoteWidget
import kotlinx.coroutines.launch

/** A separate compact task: widget entry never opens the main app or its navigation. */
class QuickEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember { getSharedPreferences("paynote_preferences", Context.MODE_PRIVATE) }
            val theme = ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: ThemeMode.SYSTEM
            PayNoteTheme(theme) {
                val dao = remember { TransactionDatabase.getInstance(this).transactionDao() }
                val rows by dao.observeAllTransactions().collectAsState(initial = emptyList())
                var saving by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val height = (LocalConfiguration.current.screenHeightDp * .83f).dp
                Surface(modifier = Modifier.fillMaxWidth().height(height), color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(28.dp)) {
                    TransactionEditorScreen(initialDirection = intent.getStringExtra(EXTRA_DIRECTION).takeIf { it == "RECEIVED" } ?: "SENT",
                        wallets = rows.map { it.walletName }.distinct(), saving = saving, error = error, onCancel = { if (!saving) finish() }, onSave = { draft ->
                            if (!saving) {
                                saving = true
                                scope.launch {
                                    try {
                                        dao.insert(draft.toManualEntry())
                                        PayNoteWidget.updateAll(this@QuickEntryActivity, dao.getRecentTransactionsSince(0))
                                        Toast.makeText(this@QuickEntryActivity, "Entry saved", Toast.LENGTH_SHORT).show()
                                        finish()
                                    } catch (_: Exception) { error = "Could not save this entry. Please try again."; saving = false }
                                }
                            }
                        })
                }
            }
        }
    }

    companion object { const val EXTRA_DIRECTION = "quick_entry_direction" }
}
