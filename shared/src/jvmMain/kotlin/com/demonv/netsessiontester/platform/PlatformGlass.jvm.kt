package com.demonv.netsessiontester.platform

actual fun getPlatformGlassCapabilities(): GlassCapabilities {
    val os = System.getProperty("os.name", "").lowercase()
    val isMac = os.contains("mac")
    return GlassCapabilities(
        platform = if (isMac) PlatformType.MACOS else PlatformType.WINDOWS,
        supportsNativeLiquidGlass = false,
        supportsWindowBlur = true,
        recommendedBlurRadiusDp = if (isMac) 28f else 20f,
        prefersReduceTransparency = false
    )
}
