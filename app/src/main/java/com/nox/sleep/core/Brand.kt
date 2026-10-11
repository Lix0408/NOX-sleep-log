package com.nox.sleep.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 品牌母题：月牙
 *
 * 由两个圆的差集得到，与设计稿中的应用图标、主按钮、启动动画使用完全相同的几何。
 * 坐标已平移到 100×100 盒内居中（原始圆心 x 各 +9.41）。
 *
 *   外弧：圆 (59.41, 50) r=36，从 (76.59, 18.37) 逆时针经最左点 (23.41, 50) 到 (76.59, 81.63)
 *   内弧：圆 (71.73, 50) r=32，从 (76.59, 81.63) 顺时针经最左点 (39.73, 50) 回到起点
 *
 * 2026-10-11 起：整体逆时针倾斜 [CRESCENT_TILT_DEG]，让月牙更有夜空垂挂感。
 * 该角度是全项目统一值（应用图标 / 主按钮 / 启动动画 / 通知图标共用），
 * 渲染时由 [Crescent] / [GlowingCrescent] 统一施加；res/drawable 下的
 * 4 个矢量文件用 group rotation 实现同一角度，调整时须同步。
 */
const val CRESCENT_TILT_DEG = -12f

fun crescentPath(): Path = Path().apply {
    moveTo(76.59f, 18.37f)
    arcTo(
        rect = Rect(23.41f, 14f, 95.41f, 86f),
        startAngleDegrees = -61.5f,
        sweepAngleDegrees = -237f,
        forceMoveTo = false
    )
    arcTo(
        rect = Rect(39.73f, 18f, 103.73f, 82f),
        startAngleDegrees = 81.3f,
        sweepAngleDegrees = 197.4f,
        forceMoveTo = false
    )
    close()
}

/** 渐变月牙。brush 传 NoxBrush.Moon 即与主按钮同源。 */
@Composable
fun Crescent(
    size: Dp,
    brush: Brush = NoxBrush.Moon,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 100f
        rotate(CRESCENT_TILT_DEG, pivot = this.center) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(crescentPath(), brush = brush)
            }
        }
    }
}

/** 带柔光的月牙，用于启动动画与睡眠中页。 */
@Composable
fun GlowingCrescent(
    size: Dp,
    glowRadius: Dp,
    glowAlpha: Float = 0.55f,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val r = glowRadius.toPx() + this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NoxColor.Violet.copy(alpha = glowAlpha),
                        NoxColor.Violet.copy(alpha = 0f)
                    ),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
            val s = this.size.minDimension / 100f
            rotate(CRESCENT_TILT_DEG, pivot = this.center) {
                scale(s, s, pivot = Offset.Zero) {
                    drawPath(crescentPath(), brush = NoxBrush.Moon)
                }
            }
        }
    }
}

/**
 * 线性图标集。路径数据直接沿用设计稿 02 节定义的 24px 网格 / 1.75px 描边规范。
 */
object NoxIcons {

    private fun lineIcon(name: String, vararg ds: String): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        ds.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    private fun barIcon(name: String, vararg ds: String): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        ds.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    val Moon: ImageVector = lineIcon("nox_moon", "M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5Z")
    val Stats: ImageVector = barIcon("nox_stats", "M5 14.5v4.5", "M12 8v11", "M19 11v8")
    val History: ImageVector = lineIcon(
        "nox_history",
        "M8 6h13M8 12h13M8 18h13M3.6 6h.01M3.6 12h.01M3.6 18h.01"
    )
    val Settings: ImageVector = lineIcon(
        "nox_settings",
        "M4 6h9M19 6h1M4 12h3M13 12h7M4 18h11M21 18h-1",
        "M15.5 3.8A2.2 2.2 0 1 0 15.5 8.2A2.2 2.2 0 1 0 15.5 3.8Z",
        "M9.5 9.8A2.2 2.2 0 1 0 9.5 14.2A2.2 2.2 0 1 0 9.5 9.8Z",
        "M17.5 15.8A2.2 2.2 0 1 0 17.5 20.2A2.2 2.2 0 1 0 17.5 15.8Z"
    )
    val Back: ImageVector = lineIcon("nox_back", "M14.5 5.5L8 12l6.5 6.5")
    val Chevron: ImageVector = lineIcon("nox_chevron", "M9.5 5.5L16 12l-6.5 6.5")
    val Close: ImageVector = lineIcon("nox_close", "M6.5 6.5l11 11M17.5 6.5l-11 11")
    val Edit: ImageVector = lineIcon("nox_edit", "M15.5 4.5l4 4L8 20H4v-4z")
    val Check: ImageVector = lineIcon("nox_check", "M4.8 12.6l4.8 4.8L19.2 6.6")
    val Trash: ImageVector = lineIcon(
        "nox_trash",
        "M4 7h16M9.5 7V4.8h5V7M6.5 7l1 13h9l1-13M10.5 11v5M13.5 11v5"
    )
    val Trend: ImageVector = lineIcon("nox_trend", "M4 16.5l5-5 4 3.2 6.5-7.2", "M15.5 7.5h4v4")
    val Calendar: ImageVector = lineIcon(
        "nox_calendar",
        "M3.5 8.5A3 3 0 0 1 6.5 5.5h11a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3z",
        "M8 3.5v3.5M16 3.5v3.5M3.5 10.5h17"
    )
    val Alert: ImageVector = lineIcon(
        "nox_alert",
        "M12 3.6L21.2 19.4H2.8z",
        "M12 9.5v4.5M12 17h.01"
    )
    val Info: ImageVector = lineIcon(
        "nox_info",
        "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 0 0 0-17Z",
        "M12 11.2v5M12 8h.01"
    )
    val Play: ImageVector = lineIcon("nox_play", "M8.5 5.6l10 6.4-10 6.4z")
    val Layer: ImageVector = lineIcon(
        "nox_layer",
        "M12 3.5l9 4.8-9 4.8-9-4.8z",
        "M3 12.6l9 4.8 9-4.8"
    )
}
