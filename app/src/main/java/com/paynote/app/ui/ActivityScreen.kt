@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.paynote.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.paynote.app.model.*

@Composable
fun ActivityScreen(rows: List<TransactionEntity>, filters: ActivityFilters, listState: LazyListState,
                   onFilters: (ActivityFilters) -> Unit, onOpen: (Long) -> Unit, onAdd: () -> Unit, onExport: (List<TransactionEntity>) -> Unit) {
    val filtered = remember(rows, filters) { filterTransactions(rows, filters) }
    LaunchedEffect(filters) { listState.scrollToItem(0) }
    var showFilters by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        PageHeader("Activity", "${filtered.size} ${if (filtered.size == 1) "entry" else "entries"}") {
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "Add entry") }
            IconButton(onClick = { onExport(filtered) }, enabled = filtered.isNotEmpty()) { Icon(Icons.Rounded.IosShare, "Export filtered activity") }
        }
        OutlinedTextField(value = filters.query, onValueChange = { onFilters(filters.copy(query = it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Search name, purpose or reference", style = MaterialTheme.typography.bodyMedium) },
            trailingIcon = { if (filters.query.isNotBlank()) IconButton(onClick = { onFilters(filters.copy(query = "")) }) { Icon(Icons.Rounded.Close, "Clear search") } },
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilledTonalButton(onClick = { keyboard?.hide(); showFilters = true }, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(17.dp)); Text(if (filters.activeCount > 0) "Filters · ${filters.activeCount}" else "Filters", Modifier.padding(start = 6.dp))
            } }
            items(FlowFilter.entries) { flow -> FilterPill(flow.label, filters.flow == flow) { onFilters(filters.copy(flow = flow)) } }
        }
        if (filters.provider != null || filters.period != PeriodFilter.ALL || filters.purpose != PurposeFilter.ALL || filters.currency != null || filters.category != null) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp).animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(filters.provider, filters.currency, filters.category, filters.period.takeIf { it != PeriodFilter.ALL }?.label,
                    filters.purpose.takeIf { it != PurposeFilter.ALL }?.label).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { onFilters(ActivityFilters(query = filters.query)) }) { Text("Clear filters") }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) item {
                EmptyState(Icons.Rounded.SearchOff, if (rows.isEmpty()) "No activity yet" else "No matching entries",
                    if (rows.isEmpty()) "Your bank notifications will appear here automatically." else "Try another name or adjust the filters.",
                    if (rows.isEmpty()) null else "Clear search and filters") { onFilters(ActivityFilters()) }
            }
            filtered.groupBy { dayLabel(it.timestampMillis) }.forEach { (day, group) ->
                item(key = "day:$day") { Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                items(group, key = { it.id }) { row -> TransactionListRow(row) { keyboard?.hide(); onOpen(row.id) } }
            }
        }
    }
    if (showFilters) ActivityFilterSheet(rows, filters, onDismiss = { showFilters = false }) { onFilters(it); showFilters = false }
}

@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
    }
}

@Composable
private fun ActivityFilterSheet(rows: List<TransactionEntity>, initial: ActivityFilters, onDismiss: () -> Unit, onApply: (ActivityFilters) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    val matches = remember(rows, draft) { filterTransactions(rows, draft).size }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filter activity", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = ActivityFilters(query = initial.query) }) { Text("Reset") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterSection("Direction") { FlowFilter.entries.forEach { flow -> FilterPill(flow.label, draft.flow == flow) { draft = draft.copy(flow = flow) } } }
                FilterSection("When") { PeriodFilter.entries.forEach { period -> FilterPill(period.label, draft.period == period) { draft = draft.copy(period = period) } } }
                FilterSection("Purpose") { PurposeFilter.entries.forEach { purpose -> FilterPill(purpose.label, draft.purpose == purpose) { draft = draft.copy(purpose = purpose) } } }
                FilterSection("Category") {
                    FilterPill("All categories", draft.category == null) { draft = draft.copy(category = null) }
                    rows.mapNotNull { it.category }.distinct().sorted().forEach { name -> FilterPill(name, draft.category == name) { draft = draft.copy(category = name) } }
                }
                Text("Wallet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Column {
                        RadioOption("All wallets", draft.provider == null) { draft = draft.copy(provider = null) }
                        rows.map { it.walletName }.distinct().sorted().forEach { provider -> RadioOption(provider, draft.provider == provider) { draft = draft.copy(provider = provider) } }
                    }
                }
                FilterSection("Currency") {
                    FilterPill("All currencies", draft.currency == null) { draft = draft.copy(currency = null) }
                    rows.map { it.currency }.distinct().sorted().forEach { currency -> FilterPill(currency, draft.currency == currency) { draft = draft.copy(currency = currency) } }
                }
                FilterSection("Order") {
                    FilterPill("Newest first", !draft.oldestFirst) { draft = draft.copy(oldestFirst = false) }
                    FilterPill("Oldest first", draft.oldestFirst) { draft = draft.copy(oldestFirst = true) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = { onApply(draft) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(54.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Show $matches ${if (matches == 1) "entry" else "entries"}")
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { content() }
    }
}

@Composable
private fun RadioOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(10.dp))
        }
    }
}
