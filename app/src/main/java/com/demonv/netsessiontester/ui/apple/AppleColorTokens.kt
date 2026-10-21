package com.demonv.netsessiontester.ui.apple

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Apple Human Interface Guidelines (HIG) - iOS 26 / macOS Tahoe 动态语义色彩 Token。
 * 遵循极简干净、极低色散、高对比度可读性，深浅模式自适应。
 */
@Immutable
data class AppleColorPalette(
    // 根画布背景
    val systemBackground: Color,
    // 毛玻璃材质表面
    val glassSurfaceBase: Color,
    val glassSurfaceElevated: Color,
    // 文字层级
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    // 系统语义强调色 (WCAG AA/AAA 对比度保证)
    val systemAccentBlue: Color,
    val systemSuccessGreen: Color,
    val systemWarningOrange: Color,
    val systemCriticalRed: Color,
    // 0.5dp 极隐形分割线
    val separatorHairline: Color,
    // 玻璃反光边缘
    val glassBorderTop: Color,
    val glassBorderBottom: Color
)

val AppleLightColors = AppleColorPalette(
    systemBackground = Color(0xFFF2F2F7),
    glassSurfaceBase = Color(0xFFFFFFFF).copy(alpha = 0.78f),
    glassSurfaceElevated = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    labelPrimary = Color(0xFF000000),
    labelSecondary = Color(0x993C3C43), // 60% alpha
    labelTertiary = Color(0x4D3C3C43),  // 30% alpha
    systemAccentBlue = Color(0xFF007AFF),
    systemSuccessGreen = Color(0xFF34C759),
    systemWarningOrange = Color(0xFFFF9500),
    systemCriticalRed = Color(0xFFFF3B30),
    separatorHairline = Color(0x1F3C3C43), // 12% alpha
    glassBorderTop = Color(0x99FFFFFF),    // 60% white specular highlight
    glassBorderBottom = Color(0x0A000000)  // 4% black shadow line
)

val AppleDarkColors = AppleColorPalette(
    systemBackground = Color(0xFF000000), // 极夜纯黑底
    glassSurfaceBase = Color(0xFF1C1C1E).copy(alpha = 0.72f),
    glassSurfaceElevated = Color(0xFF2C2C2E).copy(alpha = 0.82f),
    labelPrimary = Color(0xFFFFFFFF),
    labelSecondary = Color(0x99EBEBF5), // 60% alpha
    labelTertiary = Color(0x4DEBEBF5),  // 30% alpha
    systemAccentBlue = Color(0xFF0A84FF),
    systemSuccessGreen = Color(0xFF30D158),
    systemWarningOrange = Color(0xFFFF9F0A),
    systemCriticalRed = Color(0xFFFF453A),
    separatorHairline = Color(0x33545458), // 20% alpha
    glassBorderTop = Color(0x3DFFFFFF),    // 24% white specular highlight
    glassBorderBottom = Color(0x05FFFFFF)  // 2% subtle fade
)

val LocalAppleColors = staticCompositionLocalOf { AppleLightColors }
