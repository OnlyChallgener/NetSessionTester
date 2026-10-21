package com.demonv.netsessiontester.platform

import platform.UIKit.UIDevice
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled

actual fun getPlatformGlassCapabilities(): GlassCapabilities {
    val reduced = UIAccessibilityIsReduceTransparencyEnabled()
    val major = UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0
    return GlassCapabilities(
        platform = PlatformType.IOS,
        supportsNativeLiquidGlass = major >= 26 && !reduced,
        supportsWindowBlur = false,
        recommendedBlurRadiusDp = 24f,
        prefersReduceTransparency = reduced
    )
}
