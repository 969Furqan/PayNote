package com.paynote.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paynote.app.model.*

@Composable
fun WalletsScreen(rows: List<TransactionEntity>, onWallet: (String) -> Unit) {
    val wallets = rows.groupBy { it.walletName }.entries.sortedByDescending { it.value.maxOf { row -> row.timestampMillis } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Wallets", "Your activity, organised by provider")
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (wallets.isEmpty()) item { EmptyState(Icons.Rounded.AccountBalanceWallet, "All your wallets, one place", "A wallet appears here after PayNote detects its first transaction.") }
            items(wallets, key = { it.key }) { (provider, entries) ->
                Surface(onClick = { onWallet(provider) }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProviderMark(provider, 48)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(provider, style = MaterialTheme.typography.titleLarge)
                                Text("${entries.size} ${if (entries.size == 1) "entry" else "entries"} · ${dayLabel(entries.maxOf { it.timestampMillis })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val pending = entries.count { it.purpose.isNullOrBlank() }
                        if (pending > 0) Text("$pending ${if (pending == 1) "purpose" else "purposes"} to add", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
            if (wallets.isNotEmpty()) item { Text("These are notification-based records. PayNote does not read your bank balances.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)) }
        }
    }
}

@Composable
fun WalletDetailScreen(provider: String, allRows: List<TransactionEntity>, onBack: () -> Unit, onActivity: (ActivityFilters) -> Unit, onOpen: (Long) -> Unit) {
    val rows = allRows.filter { it.walletName == provider }
    Column(Modifier.fillMaxSize()) {
        PageHeader(provider, "Wallet activity", back = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ProviderMark(provider, 64) }
            rows.groupBy { it.currency }.forEach { (currency, group) -> item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp)) {
                        Text("$currency · all recorded activity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Incoming", style = MaterialTheme.typography.bodySmall); Text(money(group.filter { it.flowDirection == "RECEIVED" }.sumOf { it.amount }), style = MaterialTheme.typography.headlineMedium) }
                            Column { Text("Outgoing", style = MaterialTheme.typography.bodySmall); Text(money(group.filter { it.flowDirection != "RECEIVED" }.sumOf { it.amount }), style = MaterialTheme.typography.headlineMedium) }
                        }
                    }
                }
            } }
            item { SectionHeading("Latest entries", "See all") { onActivity(ActivityFilters(provider = provider)) } }
            items(rows.sortedByDescending { it.timestampMillis }.take(8), key = { it.id }) { row -> TransactionListRow(row) { onOpen(row.id) } }
        }
    }
}
