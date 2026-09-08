package com.demonv.netsessiontester.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.demonv.netsessiontester.ui.apple.AppleDarkColors
import com.demonv.netsessiontester.ui.apple.AppleLightColors
import com.demonv.netsessiontester.ui.apple.DefaultAppleTypography
import com.demonv.netsessiontester.ui.apple.LocalAppleColors
import com.demonv.netsessiontester.ui.apple.LocalAppleTypography
import com.demonv.netsessiontester.ui.apple.LocalReduceTransparency

private val AppleLightMaterialColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF003F8A),
    secondary = Color(0xFF34C759),
    tertiary = Color(0xFF5856D6),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF3C3C43),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30)
)

private val AppleDarkMaterialColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3A75),
    onPrimaryContainer = Color(0xFFD6EAFF),
    secondary = Color(0xFF30D158),
    tertiary = Color(0xFF5E5CE6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFEBEBF5),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A)
)

@Composable
fun NetSessionTesterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit
) {
    val appleColors = if (darkTheme) AppleDarkColors else AppleLightColors
    val materialColors = if (darkTheme) AppleDarkMaterialColors else AppleLightMaterialColors

    CompositionLocalProvider(
        LocalAppleColors provides appleColors,
        LocalAppleTypography provides DefaultAppleTypography,
        LocalReduceTransparency provides reduceTransparency
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
