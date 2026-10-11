package com.nox.sleep.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 设计系统 · 色彩
 * 对应设计稿 02 节。夜间优先：底色低饱和靛蓝，强调色只服务单一焦点。
 */
object NoxColor {
    val Void = Color(0xFF05070F)
    val Midnight = Color(0xFF0A0E20)
    val Indigo = Color(0xFF131A3E)

    val Surface = Color(0x0CFFFFFF)
    val SurfaceUp = Color(0x13FFFFFF)
    val Line = Color(0x14FFFFFF)
    val LineStrong = Color(0x24FFFFFF)

    val Ink = Color(0xFFEEF1FF)
    val Ink2 = Color(0xFFA3ABCF)
    val Ink3 = Color(0xFF6C74A0)

    val Violet = Color(0xFF8B7CF6)
    val VioletLt = Color(0xFFB0A4FF)
    val Cyan = Color(0xFF5EEAD4)
    val Amber = Color(0xFFFFC08A)
    val Rose = Color(0xFFF2A0C4)
    val Good = Color(0xFF6EE7B7)

    val Awake = Amber

    // 参照层语义色（阶段四「健康基准」）：只用于描述性/群体共识内容，
    // 永不用紫/青/琥珀，防止"通用基准"被误读成"系统判定"。
    val Mist = Color(0xFF8CA3B8)
    val MistInk = Color(0xFFA9BACB)
}

object NoxBrush {
    val Moon = Brush.linearGradient(listOf(Color.White, Color(0xFFDCD4FF), NoxColor.Violet))
    val Aurora = Brush.linearGradient(listOf(NoxColor.Cyan, NoxColor.Violet, NoxColor.Rose))
    val Dawn = Brush.linearGradient(listOf(NoxColor.Amber, NoxColor.Rose))
    val Sky = Brush.verticalGradient(
        listOf(Color(0xFF070B1C), Color(0xFF0C1230), Color(0xFF131A3E))
    )
    val BarOk = Brush.verticalGradient(listOf(NoxColor.VioletLt.copy(alpha = 0.95f), NoxColor.Violet.copy(alpha = 0.32f)))
    val BarPeak = Brush.verticalGradient(listOf(NoxColor.VioletLt, NoxColor.Cyan))
}

/**
 * 设计系统 · 字体层级
 * Display 用 ExtraLight，让大数字在夜间显得安静而不是刺眼。
 */
val NoxTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 44.sp, fontWeight = FontWeight.ExtraLight,
        letterSpacing = (-0.02).sp, lineHeight = 50.sp
    ),
    displayMedium = TextStyle(
        fontSize = 34.sp, fontWeight = FontWeight.ExtraLight,
        letterSpacing = (-0.01).sp, lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontSize = 11.5.sp, fontWeight = FontWeight.Normal,
        letterSpacing = 0.06.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.12.sp, lineHeight = 14.sp,
        textAlign = TextAlign.Center
    )
)

@Composable
fun NoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NoxColor.Violet,
            onPrimary = Color(0xFF0A0E20),
            secondary = NoxColor.Cyan,
            background = NoxColor.Void,
            onBackground = NoxColor.Ink,
            surface = NoxColor.Midnight,
            onSurface = NoxColor.Ink,
            surfaceVariant = NoxColor.Indigo,
            onSurfaceVariant = NoxColor.Ink2,
            outline = NoxColor.LineStrong
        ),
        typography = NoxTypography,
        content = content
    )
}
