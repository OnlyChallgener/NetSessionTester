package com.demonv.netsessiontester.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F0FF),
    onPrimaryContainer = Color(0xFF0F2F6E),
    secondary = Color(0xFF16A34A),
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF4F8FF),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFDFEFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF0F5FC),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceTint = Color.Transparent,
    surfaceDim = Color(0xFFE8EFF8),
    surfaceBright = Color(0xFFFDFEFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainer = Color(0xFFF3F7FD),
    surfaceContainerHigh = Color(0xFFEEF4FB),
    surfaceContainerHighest = Color(0xFFE8F0F9),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    scrim = Color(0xFF0F172A),
    error = Color(0xFFEF4444)
)

@Composable
fun NetSessionTesterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
