package com.nox.sleep.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nox.sleep.core.NoxBrush
import com.nox.sleep.core.NoxColor
import com.nox.sleep.core.NoxIcons

/* ------------------------------------------------------------------ 基础容器 */

val CardShape = RoundedCornerShape(22.dp)

@Composable
fun NoxCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(
                Brush.verticalGradient(listOf(Color(0x12FFFFFF), Color(0x05FFFFFF)))
            )
            .border(1.dp, NoxColor.Line, CardShape)
            .padding(padding),
        content = content
    )
}

/** 字段标签：小字、宽字距、弱色 */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NoxColor.Ink3,
    fontSize: TextUnit = 11.5.sp
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        color = color,
        letterSpacing = if (fontSize > 12.sp) 0.sp else 0.06.sp,
        lineHeight = fontSize * 1.42f
    )
}

/* ------------------------------------------------------------------ 大数字 */

/**
 * "7h42m" 排版：数字用 ExtraLight，单位缩小并下沉。
 * 设计稿里所有时长都走这个组件，保证口径与观感一致。
 */
@Composable
fun BigDuration(
    hours: String,
    minutes: String,
    bigSize: TextUnit = 34.sp,
    unitSize: TextUnit = 14.sp,
    color: Color = NoxColor.Ink,
    unitColor: Color = NoxColor.Ink2,
    showUnits: Boolean = true
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            hours,
            fontSize = bigSize,
            fontWeight = FontWeight.ExtraLight,
            color = color,
            letterSpacing = (-0.01).sp,
            lineHeight = bigSize * 1.05f
        )
        if (showUnits) {
            Text(
                "h", fontSize = unitSize, color = unitColor,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
            )
        }
        Text(
            minutes,
            fontSize = bigSize,
            fontWeight = FontWeight.ExtraLight,
            color = color,
            letterSpacing = (-0.01).sp,
            lineHeight = bigSize * 1.05f
        )
        if (showUnits) {
            Text(
                "m", fontSize = unitSize, color = unitColor,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ 进度环 */

@Composable
fun RingProgress(
    percent: Int,
    size: Dp = 76.dp,
    stroke: Dp = 6.dp,
    centerTitle: String? = null,
    centerSub: String? = null
) {
    val animated by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100) / 100f,
        label = "ring"
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val r = (this.size.minDimension - sw) / 2f
            val tl = Offset((this.size.width - 2 * r) / 2f, (this.size.height - 2 * r) / 2f)
            drawArc(
                color = Color(0x1AFFFFFF),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = tl, size = Size(2 * r, 2 * r),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            if (animated > 0.001f) {
                drawArc(
                    brush = NoxBrush.Aurora,
                    startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = tl, size = Size(2 * r, 2 * r),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            centerTitle?.let {
                Text(
                    it,
                    fontSize = if (size > 64.dp) 15.sp else 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NoxColor.Ink,
                    lineHeight = if (size > 64.dp) 17.sp else 14.sp
                )
            }
            centerSub?.let {
                Text(it, fontSize = 8.5.sp, color = NoxColor.Ink3, letterSpacing = 0.04.sp)
            }
        }
    }
}

/* ------------------------------------------------------------------ 按钮 */

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) NoxBrush.Moon else Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.03.sp,
            color = if (enabled) Color(0xFF0A0E20) else NoxColor.Ink3
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    borderColor: Color = NoxColor.LineStrong,
    textColor: Color = NoxColor.Ink,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NoxColor.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = textColor)
    }
}

/* ------------------------------------------------------------------ 小控件 */

@Composable
fun NoxChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val bg by animateColorAsState(
        if (selected) NoxColor.Violet.copy(alpha = 0.18f) else NoxColor.Surface, label = "chipBg"
    )
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(
                1.dp,
                if (selected) NoxColor.VioletLt.copy(alpha = 0.55f) else NoxColor.Line,
                CircleShape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            color = if (selected) Color(0xFFDCD4FF) else NoxColor.Ink2,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun Segmented(
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(NoxColor.Surface)
            .border(1.dp, NoxColor.Line, CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, s ->
            val on = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) Color(0x1FFFFFFF) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s,
                    fontSize = 12.sp,
                    color = if (on) NoxColor.Ink else NoxColor.Ink3,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun NoxToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val bg by animateColorAsState(
        if (checked) NoxColor.Violet.copy(alpha = 0.85f) else Color(0x24FFFFFF),
        label = "tg"
    )
    Box(
        Modifier
            .size(width = 40.dp, height = 23.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable { onChange(!checked) }
    ) {
        val pad = if (checked) 19.dp else 2.5.dp
        Box(
            Modifier
                .padding(top = 2.5.dp)
                .padding(start = pad)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * 设置行。
 *
 * 注意参数顺序：`onClick` 必须放在最后。Kotlin 的尾随 lambda 只绑定最后一个参数，
 * 若把 `trailing` 放在最后，`SettingRow("x") { 动作 }` 会被静默解释为「行尾内容」，
 * 导致动作在每次组合时被无条件执行（曾因此产生「进设置页自动弹对话框且关不掉」的 bug）。
 * 回调永远放参数列表末尾。
 */
@Composable
fun SettingRow(
    title: String,
    value: String? = null,
    valueColor: Color = NoxColor.Ink3,
    showChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.5.sp, color = NoxColor.Ink, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, fontSize = 12.5.sp, color = valueColor)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                NoxIcons.Chevron, null,
                Modifier.size(16.dp),
                tint = NoxColor.Ink3
            )
        }
    }
}

@Composable
fun GroupLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = NoxColor.Ink3,
        letterSpacing = 0.12.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
    )
}

/* ------------------------------------------------------------------ 顶部栏 */

@Composable
fun NoxTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NoxColor.Surface)
                    .border(1.dp, NoxColor.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(NoxIcons.Back, "返回", Modifier.size(17.dp), tint = NoxColor.Ink2)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            subtitle?.let { Label(it) }
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink, lineHeight = 27.sp)
        }
        action?.invoke()
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(NoxColor.Surface)
            .border(1.dp, NoxColor.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size(17.dp), tint = NoxColor.Ink2)
    }
}

/* ------------------------------------------------------------------ 底部导航 */

enum class NoxTab(val label: String, val icon: ImageVector) {
    Home("首页", NoxIcons.Moon),
    Stats("统计", NoxIcons.Stats),
    History("历史", NoxIcons.History),
    Settings("设置", NoxIcons.Settings)
}

@Composable
fun NoxBottomBar(current: NoxTab?, onSelect: (NoxTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xE6070B18))
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NoxTab.entries.forEach { tab ->
            val on = tab == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(tab) }
                    .padding(horizontal = 16.dp, vertical = 3.dp)
            ) {
                Icon(
                    tab.icon, tab.label,
                    Modifier.size(21.dp),
                    tint = if (on) NoxColor.VioletLt else Color(0xFF666F9C)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    fontSize = 9.5.sp,
                    color = if (on) NoxColor.VioletLt else Color(0xFF666F9C),
                    letterSpacing = 0.02.sp
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ 分布图 */

/** 14 天分布：柱高即时长，虚线是当前阈值 */
@Composable
fun DistributionChart(
    values: List<Int>,
    threshold: Int,
    modifier: Modifier = Modifier
) {
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(threshold + 5)
    Box(modifier.fillMaxWidth().height(56.dp)) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEach { v ->
                Box(
                    Modifier
                        .weight(1f)
                        .height((56f * v / max).dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (v >= threshold) NoxColor.VioletLt.copy(alpha = 0.85f)
                            else NoxColor.VioletLt.copy(alpha = 0.28f)
                        )
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * (1f - threshold.toFloat() / max)
            drawLine(
                color = NoxColor.Cyan.copy(alpha = 0.6f),
                start = Offset(0f, y), end = Offset(size.width, y),
                strokeWidth = 1.5f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(8f, 6f), 0f
                )
            )
        }
    }
}

/* ------------------------------------------------------------------ 文本工具 */

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.5.sp, align: TextAlign = TextAlign.Start) {
    Text(text, modifier, fontSize = size, color = NoxColor.Ink2, lineHeight = size * 1.55f, textAlign = align)
}
