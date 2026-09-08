package com.demonv.netsessiontester.platform

actual fun getPlatformGlassCapabilities(): GlassCapabilities = GlassCapabilities(
    platform = PlatformType.ANDROID,
    supportsNativeLiquidGlass = false,
    supportsWindowBlur = false,
    recommendedBlurRadiusDp = 20f,
    prefersReduceTransparency = false
)
