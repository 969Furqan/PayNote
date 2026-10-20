package com.paynote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

private val LightColors = lightColorScheme(
    primary = Color(0xFF204E37), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFC7), onPrimaryContainer = Color(0xFF193B26),
    secondary = Color(0xFF59704F), secondaryContainer = Color(0xFFEDF2E7), onSecondaryContainer = Color(0xFF253C21),
    background = Color(0xFFF6F7F2), onBackground = Color(0xFF18231B),
    surface = Color.White, onSurface = Color(0xFF18231B),
    surfaceVariant = Color(0xFFEBEFE7), onSurfaceVariant = Color(0xFF657064),
    outline = Color(0xFF7D877A), outlineVariant = Color(0xFFE1E6DD),
    error = Color(0xFFA63B30), errorContainer = Color(0xFFFCE8E3)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC2E89D), onPrimary = Color(0xFF143B23),
    primaryContainer = Color(0xFF293D29), onPrimaryContainer = Color(0xFFE2F2D4),
    secondary = Color(0xFFB4C8AA), secondaryContainer = Color(0xFF263227), onSecondaryContainer = Color(0xFFDBE9D1),
    background = Color(0xFF101712), onBackground = Color(0xFFE9EEE5),
    surface = Color(0xFF1A241D), onSurface = Color(0xFFE9EEE5),
    surfaceVariant = Color(0xFF28322A), onSurfaceVariant = Color(0xFFB0BCAE),
    outline = Color(0xFF899584), outlineVariant = Color(0xFF344036),
    error = Color(0xFFFFB4A5), errorContainer = Color(0xFF4B2924)
)

@Composable
fun PayNoteTheme(mode: ThemeMode, content: @Composable (Boolean) -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
        )) { content(dark) }
}
