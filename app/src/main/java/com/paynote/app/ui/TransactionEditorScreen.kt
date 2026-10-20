package com.paynote.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.paynote.app.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionEditorScreen(existing: TransactionEntity? = null, initialDirection: String = "SENT",
                            wallets: List<String>, saving: Boolean, error: String?,
                            onCancel: () -> Unit, onSave: (TransactionDraft) -> Unit) {
    val initial = remember(existing?.id) { existing?.let(TransactionDraft::from) ?: TransactionDraft(direction = initialDirection) }
    var wallet by rememberSaveable(existing?.id) { mutableStateOf(initial.wallet) }
    var party by rememberSaveable(existing?.id) { mutableStateOf(initial.counterparty) }
    var direction by rememberSaveable(existing?.id) { mutableStateOf(initial.direction) }
    var amount by rememberSaveable(existing?.id) { mutableStateOf(initial.amount) }
    var currency by rememberSaveable(existing?.id) { mutableStateOf(initial.currency) }
    var category by rememberSaveable(existing?.id) { mutableStateOf(initial.category) }
    var purpose by rememberSaveable(existing?.id) { mutableStateOf(initial.purpose) }
    var timestamp by rememberSaveable(existing?.id) { mutableStateOf(initial.timestampMillis) }
    var validation by remember { mutableStateOf<String?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val incoming = direction == "RECEIVED"
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(if (existing == null) "Add an entry" else "Edit entry", if (existing == null) "Log a transaction that was missed" else "Correct the details for this entry", back = onCancel)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FilterPill("Incoming", incoming) { direction = "RECEIVED" }
                FilterPill("Outgoing", !incoming) { if (incoming) direction = "SENT" }
            }
            if (existing == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(amount, { amount = it; validation = null }, label = { Text("Amount") }, modifier = Modifier.weight(1.7f), shape = shape,
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(currency, { currency = it.uppercase().take(3); validation = null }, label = { Text("Currency") }, modifier = Modifier.weight(1f), shape = shape,
                        singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                }
            } else {
                Text("${existing.currency} ${money(existing.amount)} · ${dayLabel(existing.timestampMillis)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(party, { party = it; validation = null }, label = { Text(if (incoming) "Sender name" else "Recipient or merchant name") },
                singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.PersonOutline, null) })
            OutlinedTextField(wallet, { wallet = it; validation = null }, label = { Text("Wallet / source name") }, placeholder = { Text("SadaPay, bank account, cash…") },
                singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, null) })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items((wallets + listOf("SadaPay", "NayaPay", "JazzCash", "Meezan Bank", "Cash")).distinct()) { name -> FilterPill(name, wallet == name) { wallet = name } }
            }
            Box {
                OutlinedTextField(category, { category = it }, label = { Text("Category (optional)") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Category, null) }, trailingIcon = { IconButton(onClick = { categoryMenu = true }) { Icon(Icons.Rounded.ExpandMore, "Choose category") } })
                DropdownMenu(categoryMenu, { categoryMenu = false }) {
                    DropdownMenuItem(text = { Text("No category") }, onClick = { category = ""; categoryMenu = false })
                    transactionCategories.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { category = name; categoryMenu = false }) }
                }
            }
            OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose / note (optional)") }, minLines = 2, maxLines = 4, shape = shape, modifier = Modifier.fillMaxWidth())
            if (!incoming) Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterPill("Transfer sent", direction == "SENT") { direction = "SENT" }
                FilterPill("Payment", direction == "PAID") { direction = "PAID" }
            }
            if (existing == null) {
                val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
                OutlinedButton(onClick = {
                    keyboard?.hide()
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            timestamp = date.withYear(year).withMonth(month + 1).withDayOfMonth(1).withDayOfMonth(day).withHour(hour).withMinute(minute).withSecond(0).toInstant().toEpochMilli()
                        }, date.hour, date.minute, false).show()
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }, modifier = Modifier.fillMaxWidth(), shape = shape) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a")), Modifier.padding(start = 9.dp))
                }
            }
            Text(if (existing == null) "Saved as a manual entry. You can change its name, category or direction later." else "Your corrections stay in place when another notification arrives. The original notification is kept for reference.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (validation != null || error != null) Text(validation ?: error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, enabled = !saving) { Text("Cancel") }
            Button(onClick = {
                val draft = TransactionDraft(wallet, party, direction, amount.trim(), currency, category, purpose, timestamp)
                validation = draft.validationError()
                if (validation == null) { keyboard?.hide(); onSave(draft) }
            }, enabled = !saving, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).height(52.dp)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (existing == null) "Save entry" else "Save changes")
            }
        }
    }
}
