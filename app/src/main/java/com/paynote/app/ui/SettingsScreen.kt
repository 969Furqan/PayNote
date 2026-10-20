@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.paynote.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.paynote.app.widget.PayNoteWidget

@Composable
fun SettingsScreen(theme: ThemeMode, onTheme: (ThemeMode) -> Unit, listenerOn: Boolean, promptsOn: Boolean,
                   onListenerSettings: () -> Unit, onPromptSettings: () -> Unit, onExport: () -> Unit,
                   onTools: () -> Unit, onTransferHelp: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", "Appearance, logging and exports")
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            Surface(onClick = { onTheme(mode) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp),
                                color = if (mode == theme) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                                Column(Modifier.padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(when (mode) { ThemeMode.SYSTEM -> Icons.Rounded.PhoneAndroid; ThemeMode.LIGHT -> Icons.Rounded.LightMode; ThemeMode.DARK -> Icons.Rounded.DarkMode }, null,
                                        tint = if (mode == theme) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(mode.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                                    AnimatedVisibility(mode == theme) { Icon(Icons.Rounded.Check, null, Modifier.padding(top = 5.dp).size(16.dp)) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Automatic logging", style = MaterialTheme.typography.titleLarge)
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column {
                            SettingRow(Icons.Rounded.NotificationsActive, "Notification access", "Read bank and wallet app alerts", if (listenerOn) "On" else "Off", onListenerSettings)
                            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            SettingRow(Icons.Rounded.EditNotifications, "Purpose prompts", "Add a purpose from your notification", if (promptsOn) "On" else "Off", onPromptSettings)
                        }
                    }
                    Text("App notifications come first. SMS waits five seconds as a fallback and can fill in missing references.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 3.dp))
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    SettingRow(Icons.Rounded.Widgets, "Home screen widget", "Monthly totals and quick Incoming / Outgoing entry") { PayNoteWidget.requestPin(context) }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Column {
                        SettingRow(Icons.Rounded.IosShare, "Export your history", "Share a CSV file of all entries", onClick = onExport)
                        HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingRow(Icons.Rounded.SwapHoriz, "Between your own wallets", "Understand the two sides of a transfer", onClick = onTransferHelp)
                        HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingRow(Icons.Rounded.Science, "Test notifications", "Sample prompts and duplicate checks", onClick = onTools)
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Stored locally. No bank login needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 9.dp))
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(onBack: () -> Unit, running: Boolean, onSample: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Test notifications", "Try PayNote with sample entries", back = onBack)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("A safe way to try the flow", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 14.dp))
                        Text("These buttons create sample entries in your history and post real PayNote prompts. They do not make payments or verify a bank connection.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 9.dp))
                    }
                }
            }
            item { OutlinedButton(onClick = { onSample("sadapay") }, enabled = !running, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("SadaPay + SMS duplicate check") } }
            item { OutlinedButton(onClick = { onSample("meezan") }, enabled = !running, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Meezan sample transfer") } }
            item { OutlinedButton(onClick = { onSample("google") }, enabled = !running, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Google Pay sample payment") } }
            if (running) item { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp); Text("Checking notifications…", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium) } }
            item { Text("Meezan's live app check is separate. A sample does not bypass its developer-mode restriction.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun TransferHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
          Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            Text("One move. Two wallet entries.", style = MaterialTheme.typography.headlineMedium)
            Text("When you move money between your own apps, PayNote records what each wallet reports.", style = MaterialTheme.typography.bodyLarge)
            Text("EXAMPLE · MOVE PKR 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailTransferLine("Sending wallet", "−PKR 100", "Outgoing · Sent")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailTransferLine("Receiving wallet", "+PKR 100", "Incoming · Received")
                }
            }
            Text("Both entries stay separate. App + SMS duplicates within each wallet are merged. PayNote does not yet automatically link the two wallets' entries as one internal transfer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("The Home totals include both sides, so they describe movement rather than income or spending alone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("Got it") }
        }
    }
}

@Composable
private fun DetailTransferLine(title: String, amount: String, flow: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(flow, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
