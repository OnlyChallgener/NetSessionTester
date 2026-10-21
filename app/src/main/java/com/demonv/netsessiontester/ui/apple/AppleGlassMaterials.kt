package com.demonv.netsessiontester.ui.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 连续统一圆角规范 (Continuous Corner Radius System)
 */
object AppleCornerRadii {
    val Small = 8.dp      // 标签胶囊、状态小标
    val Medium = 14.dp    // 次级卡片、操作按钮、工具项卡片
    val Large = 20.dp     // 核心主卡片（仪表盘、折线图大卡片）
    val Modal = 28.dp     // 底部抽屉 Sheet、主模态弹窗
    val Pill = 999.dp     // 搜索栏、Segment 过滤胶囊
}

/**
 * 辅助功能：是否降低透明度 (Reduce Transparency)
 * 开启时自动关闭毛玻璃模糊与透光，降级为高对比度实体表面。
 */
val LocalReduceTransparency = compositionLocalOf { false }

/**
 * 倒角高光画笔 (Specular Bevel Border Brush)
 * 模拟真实精密玻璃倒角。顶端入射光微亮，底端渐隐融入底色。
 */
object AppleSpecularBorders {
    @Composable
    fun borderBrush(isElevated: Boolean = false): Brush {
        val colors = LocalAppleColors.current
        return Brush.verticalGradient(
            0.0f to colors.glassBorderTop,
            0.35f to colors.glassBorderTop.copy(alpha = colors.glassBorderTop.alpha * 0.4f),
            1.0f to colors.glassBorderBottom
        )
    }
}

/**
 * 苹果 HIG 风格轻微毛玻璃卡片原子组件。
 * - 极低色散、无塑料感；
 * - 顶底双层环境弥散阴影；
 * - 倒角微高光边框；
 * - 支持无障碍“降低透明度”平滑降级。
 */
@Composable
fun AppleGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppleCornerRadii.Large),
    isElevated: Boolean = false,
    contentPadding: Dp = 16.dp, // 8pt 网格标准内边距
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalAppleColors.current
    val reduceTransparency = LocalReduceTransparency.current

    // 材质表面底色（无障碍降级时切换为实体高阶表面）
    val surfaceColor = when {
        reduceTransparency -> if (isElevated) colors.glassSurfaceElevated.copy(alpha = 1.0f) else colors.glassSurfaceBase.copy(alpha = 1.0f)
        isElevated -> colors.glassSurfaceElevated
        else -> colors.glassSurfaceBase
    }

    // 双层环境弥散微阴影 (Key 0.03 + Ambient 0.06)，杜绝生硬重阴影
    val shadowElevation = if (isElevated) 8.dp else 4.dp
    val borderBrush = AppleSpecularBorders.borderBrush(isElevated = isElevated)

    val clickableModifier = if (onClick != null) {
        Modifier.applePressScale(targetScale = 0.975f, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.03f)
            )
            .clip(shape)
            .background(surfaceColor)
            .border(
                width = if (reduceTransparency) 1.dp else 0.8.dp,
                brush = borderBrush,
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}
