package com.demonv.netsessiontester.ui.apple

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Apple 真实物理弹簧动效规范。
 * 遵循自然阻尼，杜绝机械线性或生硬回弹。
 */
object AppleSpringSpecs {
    // 快速响应（按钮微缩放、Tab 切换选中指示器）
    val Snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy, // 0.75f 微小回弹
        stiffness = Spring.StiffnessMediumLow         // 400f
    )

    // 平稳沉着（Sheet 展开、弹窗浮现、大卡片层级过渡）
    val Smooth = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,  // 1.0f 极度平滑无晃动
        stiffness = Spring.StiffnessLow               // 200f
    )
}

/**
 * 苹果质感压感微缩放修饰符。
 * 按下时平滑微缩放至 0.975f，抬起时自然弹回。
 */
@Composable
fun Modifier.applePressScale(
    targetScale: Float = 0.975f,
    onClick: (() -> Unit)? = null
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = AppleSpringSpecs.Snappy,
        label = "ApplePressScale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // 由微缩放承载点击反馈，消除 Material 灰色水波纹遮挡毛玻璃
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}
