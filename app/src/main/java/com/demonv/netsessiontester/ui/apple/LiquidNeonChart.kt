package com.demonv.netsessiontester.ui.apple

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 苹果 HIG 风格液态微光折线图组件 (Liquid Neon Chart)。
 * - 2.2dp 抗锯齿平滑主折线；
 * - 垂直渐变液态光晕填充 (0.22f -> 0.08f -> Transparent)；
 * - 0.5dp 隐形水平基准刻度线；
 * - 双层同心呼吸峰值光晕点 (3dp 纯白核 + 8dp 柔光环)。
 */
@Composable
fun LiquidNeonChart(
    points: List<Int>,
    modifier: Modifier = Modifier,
    strokeColor: Color = LocalAppleColors.current.systemAccentBlue,
    emptyPlaceholder: String = "开始测试后显示本次折线"
) {
    val colors = LocalAppleColors.current
    val typography = LocalAppleTypography.current

    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyPlaceholder,
                style = typography.caption,
                color = colors.labelTertiary
            )
        }
        return
    }

    val maxVal = remember(points) { (points.maxOrNull() ?: 1).coerceAtLeast(10) }
    val minVal = 0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = if (points.size > 1) width / (points.size - 1) else width

        // 1. 绘制 3 条极微弱参考水平基准线 (0.5dp, 12% alpha)，消除棋盘网格
        val gridLines = 3
        for (i in 1..gridLines) {
            val y = height * (i.toFloat() / (gridLines + 1))
            drawLine(
                color = colors.separatorHairline,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        // 2. 构建折线路径与填充路径
        val strokePath = Path()
        val fillPath = Path()

        var peakIndex = 0
        var peakValue = points[0]
        var peakOffset = Offset.Zero

        points.forEachIndexed { index, value ->
            val x = index * stepX
            val normalizedY = (value.toFloat() - minVal) / (maxVal - minVal).toFloat()
            val y = height - (normalizedY * height * 0.85f) - (height * 0.08f)

            if (value >= peakValue) {
                peakValue = value
                peakIndex = index
                peakOffset = Offset(x, y)
            }

            if (index == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // 闭合填充路径至底部
        val lastX = (points.size - 1) * stepX
        fillPath.lineTo(lastX, height)
        fillPath.close()

        // 3. 绘制垂直渐变液态微光填充 (0.22f -> 0.08f -> Transparent)
        val fillGradient = Brush.verticalGradient(
            0.0f to strokeColor.copy(alpha = 0.22f),
            0.5f to strokeColor.copy(alpha = 0.08f),
            1.0f to Color.Transparent
        )
        drawPath(path = fillPath, brush = fillGradient)

        // 4. 绘制 2.2dp 主折线
        drawPath(
            path = strokePath,
            color = strokeColor,
            style = Stroke(
                width = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 5. 绘制峰值点 (Peak Indicator)：内层纯白 + 外层呼吸光晕
        if (points.isNotEmpty()) {
            // 外层柔和光晕环
            drawCircle(
                color = strokeColor.copy(alpha = 0.25f),
                radius = 7.dp.toPx(),
                center = peakOffset
            )
            // 中层光晕边
            drawCircle(
                color = strokeColor,
                radius = 3.5.dp.toPx(),
                center = peakOffset
            )
            // 内层纯白实心核
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = peakOffset
            )
        }
    }
}
