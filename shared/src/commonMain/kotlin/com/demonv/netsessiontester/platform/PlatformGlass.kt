package com.demonv.netsessiontester.platform

/**
 * 目标操作系统枚举（严格限定四端：Android、iOS、macOS、Windows，排除 Linux）
 */
enum class PlatformType {
    ANDROID,
    IOS,
    MACOS,
    WINDOWS
}

/**
 * 平台毛玻璃与液态玻璃渲染能力特征。
 * 能力描述不等于控件已经应用该效果。iOS 26 系统导航采用 Liquid Glass；
 * 传统 UIBlurEffect 与 Liquid Glass 不等价。各 UI 按平台能力选择外观。
 */
data class GlassCapabilities(
    val platform: PlatformType,
    val supportsNativeLiquidGlass: Boolean,
    val supportsWindowBlur: Boolean,
    val recommendedBlurRadiusDp: Float,
    val prefersReduceTransparency: Boolean = false
)

expect fun getPlatformGlassCapabilities(): GlassCapabilities
