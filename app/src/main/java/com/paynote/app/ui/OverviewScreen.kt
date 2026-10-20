package com.paynote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paynote.app.model.*

@Composable
fun OverviewScreen(rows: List<TransactionEntity>, listenerOn: Boolean, promptsOn: Boolean, listState: LazyListState,
                   onSetup: () -> Unit, onActivity: (ActivityFilters) -> Unit, onOpen: (Long) -> Unit, onAdd: () -> Unit, onTransferHelp: () -> Unit) {
    val currencies = rows.map { it.currency }.distinct().sorted().ifEmpty { listOf("PKR") }
    var currency by remember { mutableStateOf(if ("PKR" in currencies) "PKR" else currencies.first()) }
    var currencyMenu by remember { mutableStateOf(false) }
    val month = filterTransactions(rows, ActivityFilters(currency = currency, period = PeriodFilter.MONTH))
    val incoming = month.filter { it.flowDirection == "RECEIVED" }.sumOf { it.amount }
    val outgoing = month.filter { it.flowDirection != "RECEIVED" }.sumOf { it.amount }
    val pending = rows.count { it.purpose.isNullOrBlank() }

    Column(Modifier.fillMaxSize()) {
        PageHeader("PayNote", "One history for your wallets") {
            FilledTonalIconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "Add entry") }
            FilledTonalIconButton(onClick = onSetup) { Icon(if (listenerOn && promptsOn) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone, "Notification settings") }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("This month", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                            Box {
                                TextButton(onClick = { currencyMenu = true }) { Text(currency, color = MaterialTheme.colorScheme.onPrimaryContainer); Icon(Icons.Rounded.ExpandMore, "Choose summary currency", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                                DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                                    currencies.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { currency = value; currencyMenu = false }) }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            MovementTotal("Money in", incoming, true, Modifier.weight(1f)) { onActivity(ActivityFilters(currency = currency, period = PeriodFilter.MONTH, flow = FlowFilter.INCOMING)) }
                            MovementTotal("Money out", outgoing, false, Modifier.weight(1f)) { onActivity(ActivityFilters(currency = currency, period = PeriodFilter.MONTH, flow = FlowFilter.OUTGOING)) }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Recorded activity · not your bank balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = onTransferHelp).padding(horizontal = 3.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Between your wallets? Both sides are recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Icon(Icons.Rounded.Info, "How wallet transfers work", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!listenerOn || !promptsOn) item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(18.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Set up automatic logging", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                        Text("Enable notification access and purpose prompts. Bank app alerts are always preferred.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                        FilledTonalButton(onClick = onSetup, modifier = Modifier.padding(top = 12.dp)) { Text("Finish setup"); Icon(Icons.Rounded.ArrowForward, null, Modifier.padding(start = 8.dp).size(18.dp)) }
                    }
                }
            }
            item {
                Surface(onClick = { onActivity(ActivityFilters(purpose = PurposeFilter.PENDING)) }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (pending > 0) Icons.Rounded.EditNote else Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text(if (pending > 0) "$pending ${if (pending == 1) "entry needs" else "entries need"} a purpose" else "You're all caught up", style = MaterialTheme.typography.titleMedium)
                            Text(if (pending > 0) "A small note makes it easier to remember." else "Every entry has a purpose saved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionHeading("Recent activity", "See all") { onActivity(ActivityFilters()) } }
            if (rows.isEmpty()) item { EmptyState(Icons.Rounded.ReceiptLong, "Your first entry starts here", "Your bank app notifications become a clear, searchable history.") }
            items(rows.sortedByDescending { it.timestampMillis }.take(6), key = { it.id }) { row -> TransactionListRow(row) { onOpen(row.id) } }
        }
    }
}

@Composable
private fun MovementTotal(title: String, amount: Double, incoming: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (incoming) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(start = 6.dp))
        }
        Text(money(amount), fontWeight = FontWeight.SemiBold, fontSize = if (money(amount).length > 9) 23.sp else 30.sp,
            letterSpacing = (-.7).sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 9.dp), maxLines = 1)
    }
}
