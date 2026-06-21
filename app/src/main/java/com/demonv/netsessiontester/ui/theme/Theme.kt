package com.demonv.netsessiontester.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FE4),
    secondary = Color(0xFF18A867),
    tertiary = Color(0xFF8652F6),
    background = Color(0xFFF2F5FB),
    surface = Color(0xFFFEFEFF),
    surfaceVariant = Color(0xFFF7F9FD),
    error = Color(0xFFEF4B55)
)

@Composable
fun NetSessionTesterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
