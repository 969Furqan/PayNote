package com.paynote.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paynote.app.model.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MainPage(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home), ACTIVITY("Activity", Icons.Rounded.SwapVert),
    WALLETS("Wallets", Icons.Rounded.AccountBalanceWallet), SETTINGS("Settings", Icons.Rounded.Tune)
}

fun money(amount: Double): String = NumberFormat.getNumberInstance(Locale.US).apply {
    maximumFractionDigits = 2; minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
}.format(amount)

fun directionName(direction: String) = when (direction) { "RECEIVED" -> "Received"; "PAID" -> "Paid"; else -> "Sent" }
fun partyPrefix(direction: String) = when (direction) { "RECEIVED" -> "From"; "PAID" -> "At"; else -> "To" }
fun dayLabel(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    return when (date) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy")) }
}
fun timeLabel(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
fun PageHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            IconButton(onClick = back, modifier = Modifier.padding(end = 8.dp)) { Icon(Icons.Rounded.ArrowBack, "Back") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        }
        action()
    }
}

@Composable
fun PayNoteNavigation(page: MainPage, onSelect: (MainPage) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround) {
            MainPage.entries.forEach { item ->
                val selected = item == page
                val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "navigation tint")
                val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "navigation pill")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item) }).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.background(background, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 5.dp)) { Icon(item.icon, null, tint = tint, modifier = Modifier.size(23.dp)) }
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
fun ProviderMark(provider: String, size: Int = 46) {
    val background = when (provider) {
        "SadaPay" -> Color(0xFFFFDDD2); "NayaPay" -> Color(0xFFF8DFD0); "JazzCash" -> Color(0xFFF5DC86)
        "Meezan Bank" -> Color(0xFFE7DEF3); "Google Pay" -> Color(0xFFDCE7FA); else -> Color(0xFFE1E8DD)
    }
    Box(Modifier.size(size.dp).background(background, RoundedCornerShape((size * .34f).dp)), contentAlignment = Alignment.Center) {
        Text(provider.take(1).uppercase(), fontSize = (size * .39f).sp, fontWeight = FontWeight.Bold, color = Color(0xFF253329))
    }
}

@Composable
fun TransactionListRow(row: TransactionEntity, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else 1f, spring(dampingRatio = .85f, stiffness = 700f), label = "entry press")
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 12.dp)) {
                ProviderMark(row.walletName)
                Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(20.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (row.flowDirection == "RECEIVED") Icons.Rounded.SouthWest else Icons.Rounded.NorthEast, null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("${partyPrefix(row.flowDirection)} ${row.partyName}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${row.walletName} · ${timeLabel(row.timestampMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                Text(listOfNotNull(row.category, row.purpose?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { "Add a purpose" }, style = MaterialTheme.typography.bodySmall,
                    color = if (row.purpose.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (row.flowDirection == "RECEIVED") "+" else "−"}${row.currency} ${money(row.amount)}",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = if (row.flowDirection == "RECEIVED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(directionName(row.flowDirection), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (action != null) TextButton(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Rounded.ChevronRight, null, Modifier.padding(start = 5.dp).size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
