package com.demonv.netsessiontester.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF16A34A),
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF6F8FC),
    surface = Color.White,
    error = Color(0xFFEF4444)
)

@Composable
fun NetSessionTesterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
