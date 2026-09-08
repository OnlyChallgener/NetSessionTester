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
 * iOS 端采用原生液态玻璃 (Native Liquid Glass / UIBlurEffect)，
 * macOS / Windows 具备原生窗口级磨砂透明，
 * Android 及其他环境自动优雅降级为高保真硬件毛玻璃表面。
 */
data class GlassCapabilities(
    val platform: PlatformType,
    val supportsNativeLiquidGlass: Boolean,
    val supportsWindowBlur: Boolean,
    val recommendedBlurRadiusDp: Float,
    val prefersReduceTransparency: Boolean = false
)

expect fun getPlatformGlassCapabilities(): GlassCapabilities
