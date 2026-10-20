package com.paynote.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paynote.app.model.NotificationSources
import com.paynote.app.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionDetailScreen(row: TransactionEntity, onBack: () -> Unit, onSavePurpose: (String) -> Unit,
                            onDelete: () -> Unit, onExport: () -> Unit, onEdit: () -> Unit) {
    var editPurpose by remember(row.id) { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showOriginal by remember(row.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val incoming = row.flowDirection == "RECEIVED"
    Column(Modifier.fillMaxSize()) {
        PageHeader("Transaction", back = onBack) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, "Transaction options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit entry") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Export entry") }, leadingIcon = { Icon(Icons.Rounded.IosShare, null) }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text("Delete entry", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteConfirm = true })
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProviderMark(row.walletName, 68)
                    Text(if (incoming) "Incoming · Received" else "Outgoing · ${directionName(row.flowDirection)}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp))
                    val amount = "${if (incoming) "+" else "−"}${row.currency} ${money(row.amount)}"
                    Text(amount, fontWeight = FontWeight.SemiBold, fontSize = if (amount.length > 15) 30.sp else 38.sp, letterSpacing = (-1).sp,
                        color = if (incoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 9.dp))
                    Text("${partyPrefix(row.flowDirection)} ${row.partyName}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
            }
            item {
                Surface(onClick = { editPurpose = true }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EditNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text("Purpose", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
                            Text(row.purpose?.takeIf { it.isNotBlank() } ?: "What was this for?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp))
                        }
                        Icon(Icons.Rounded.Edit, "Edit purpose", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        DetailLine("Wallet", row.walletName)
                        DetailLine("Category", row.category ?: "Uncategorised")
                        DetailLine("Date", Instant.ofEpochMilli(row.timestampMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a")))
                        DetailLine("Source", row.sourceLabel)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Reference", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            SelectionContainer { Text(row.transactionId ?: "Not provided", style = MaterialTheme.typography.bodyMedium) }
                            if (row.transactionId != null) IconButton(onClick = { clipboard.setText(AnnotatedString(row.transactionId)) }, modifier = Modifier.size(32.dp).padding(start = 6.dp)) { Icon(Icons.Rounded.ContentCopy, "Copy reference", Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Column {
                        TextButton(onClick = { showOriginal = !showOriginal }, modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp)) {
                            Icon(Icons.Rounded.NotificationsNone, null, Modifier.size(19.dp))
                            Text(if (row.isManual) "Entry origin" else "Original notification", Modifier.weight(1f).padding(start = 10.dp), textAlign = TextAlign.Start)
                            Icon(if (showOriginal) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                        }
                        AnimatedVisibility(showOriginal) {
                            SelectionContainer { Text(row.rawNotificationText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 19.dp, end = 19.dp, bottom = 19.dp)) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
        Button(onClick = onEdit, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).height(54.dp)) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(21.dp)); Text("Edit entry", Modifier.padding(start = 9.dp))
        }
    }
    if (editPurpose) PurposeDialog(row, onDismiss = { editPurpose = false }) { text -> editPurpose = false; onSavePurpose(text) }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("Delete this entry?") }, text = { Text("This removes the entry from your PayNote history. You can undo it immediately after deleting.") },
        confirmButton = { TextButton(onClick = { deleteConfirm = false; onDelete() }) { Text("Delete entry", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Keep entry") } })
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(.36f))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(.64f))
    }
}

@Composable
private fun PurposeDialog(row: TransactionEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(row.id) { mutableStateOf(row.purpose.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (row.purpose.isNullOrBlank()) "Add a purpose" else "Edit purpose") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("${directionName(row.flowDirection)} ${row.currency} ${money(row.amount)} · ${row.partyName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("What was this for?") }, placeholder = { Text("Lunch, rent, a transfer…") }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { items(listOf("Food & Dining", "Transport", "Bills", "Shopping", "Transfer", "Other")) { label -> FilterPill(label, text == label) { text = label } } }
            }
        }, confirmButton = { Button(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Save purpose") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
