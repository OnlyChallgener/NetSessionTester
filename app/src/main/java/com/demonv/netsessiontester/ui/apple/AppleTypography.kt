package com.demonv.netsessiontester.ui.apple

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Apple SF Pro & SF Mono 风格排版体系。
 * 针对网络精密测量与数据看板调优字号、行高与字符间距。
 */
@Immutable
data class AppleTypographyTokens(
    // 仪表盘核心并发数字 (如: 3000 CPS)
    val metricMega: TextStyle,
    // 页面主标题 / 大卡片标题
    val titleLarge: TextStyle,
    // 分组标题 / 卡片主标题
    val headline: TextStyle,
    // 正文说明 / 状态描述
    val bodyRegular: TextStyle,
    // 等宽数据 (IP 地址 / 端口 / 字节 / RTT 毫秒)
    val labelMono: TextStyle,
    // 辅助说明 / 时间戳 / 细微提示
    val caption: TextStyle
)

val DefaultAppleTypography = AppleTypographyTokens(
    metricMega = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyRegular = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)

val LocalAppleTypography = staticCompositionLocalOf { DefaultAppleTypography }
