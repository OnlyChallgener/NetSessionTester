package com.demonv.netsessiontester.platform

actual fun getPlatformGlassCapabilities(): GlassCapabilities = GlassCapabilities(
    platform = PlatformType.IOS,
    supportsNativeLiquidGlass = true,
    supportsWindowBlur = false,
    recommendedBlurRadiusDp = 24f,
    prefersReduceTransparency = false
)
