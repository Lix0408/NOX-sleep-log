package com.nox.sleep.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nox.sleep.core.NoxBrush
import com.nox.sleep.core.NoxColor
import com.nox.sleep.core.NoxIcons
import com.nox.sleep.core.SleepGuideline
import com.nox.sleep.core.clock
import com.nox.sleep.core.dateCn
import com.nox.sleep.core.hmParts
import com.nox.sleep.core.weekdayCn
import com.nox.sleep.data.DayAggregate
import com.nox.sleep.data.NoxSettings
import com.nox.sleep.data.SleepSession
import com.nox.sleep.domain.inBedMinutesOf
import com.nox.sleep.domain.metricsOf
import com.nox.sleep.domain.sleptMinutesOf
import com.nox.sleep.ui.components.BigDuration
import com.nox.sleep.ui.components.CircleIconButton
import com.nox.sleep.ui.components.Label
import com.nox.sleep.ui.components.MutedText
import com.nox.sleep.ui.components.NoxBottomBar
import com.nox.sleep.ui.components.NoxCard
import com.nox.sleep.ui.components.NoxChip
import com.nox.sleep.ui.components.NoxTab
import com.nox.sleep.ui.components.NoxTopBar
import com.nox.sleep.ui.components.RingProgress
import com.nox.sleep.ui.components.Segmented
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun dayOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private fun startOfDay(d: LocalDate): Long =
    d.atStartOfDay(zone).toInstant().toEpochMilli()

private fun spanMinutes(s: SleepSession): Long {
    val e = s.endAt ?: return 0L
    return inBedMinutesOf(s.startAt, e)
}

private fun sleepMinutes(s: SleepSession): Long {
    val e = s.endAt ?: return 0L
    return sleptMinutesOf(s.startAt, e, s.awakeMillis)
}

/** 归属日 key 的展示文案：今天 / 昨天 / 周几 */
private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun dayLabelOf(dayKey: String): String {
    val today = LocalDate.now()
    return when (dayKey) {
        today.toString() -> "今天"
        today.minusDays(1).toString() -> "昨天"
        else -> WEEKDAYS[LocalDate.parse(dayKey).dayOfWeek.value - 1]
    }
}

private val CIRCLED = listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩")

/* ================================================================== 聚合 */

private data class Bucket(val label: String, val sleep: Long, val goalHit: Boolean, val isCurrent: Boolean)

private enum class RangeMode(val label: String) { Week("周"), Month("月"), Year("年") }

private fun bucketsFor(
    sessions: List<SleepSession>,
    mode: RangeMode,
    goalMinutes: Int
): List<Bucket> {
    val today = LocalDate.now()
    return when (mode) {
        RangeMode.Week -> (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val mins = sessions
                .filter { s -> s.endAt != null && dayOf(s.endAt!!) == d }
                .sumOf { sleepMinutes(it) }
            Bucket(
                label = arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1],
                sleep = mins,
                goalHit = mins >= goalMinutes,
                isCurrent = back == 0
            )
        }

        RangeMode.Month -> (3 downTo 0).map { back ->
            val end = today.minusDays((back * 7).toLong())
            val start = end.minusDays(6)
            val list = sessions.filter { s ->
                val e = s.endAt ?: return@filter false
                val dd = dayOf(e)
                !dd.isBefore(start) && !dd.isAfter(end)
            }
            val mins = list.sumOf { sleepMinutes(it) }
            val avg = if (list.isEmpty()) 0L else mins / list.size
            Bucket(
                label = if (back == 0) "本周" else "${back + 1}周前",
                sleep = avg,
                goalHit = avg >= goalMinutes,
                isCurrent = back == 0
            )
        }

        RangeMode.Year -> (11 downTo 0).map { back ->
            val m = today.minusMonths(back.toLong())
            val list = sessions.filter { s ->
                val e = s.endAt ?: return@filter false
                val dd = dayOf(e)
                dd.year == m.year && dd.monthValue == m.monthValue
            }
            val mins = list.sumOf { sleepMinutes(it) }
            val avg = if (list.isEmpty()) 0L else mins / list.size
            Bucket(
                label = "${m.monthValue}",
                sleep = avg,
                goalHit = avg >= goalMinutes,
                isCurrent = back == 0
            )
        }
    }
}

/* ==================================================================== 统计 */

@Composable
fun StatsScreen(
    sessions: List<SleepSession>,
    dayStats: List<DayAggregate>,
    settings: NoxSettings,
    onOpenSettings: () -> Unit,
    onOpenGuideline: () -> Unit,
    onPickTab: (NoxTab) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            NoxTopBar(
                title = "统计",
                subtitle = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月")),
                action = { CircleIconButton(NoxIcons.Trend, "趋势", onOpenSettings) }
            )

            Spacer(Modifier.height(12.dp))
            Segmented(listOf("日", "周", "月", "年"), mode) { mode = it }

            Spacer(Modifier.height(14.dp))
            if (mode == 0) {
                DayTotalsView(dayStats, sessions, settings)
            } else {
                RangeStatsView(sessions, settings, RangeMode.entries[mode - 1], onOpenGuideline)
            }
            Spacer(Modifier.height(18.dp))
        }
        NoxBottomBar(NoxTab.Stats, onPickTab)
    }
}

@Composable
private fun RangeStatsView(
    sessions: List<SleepSession>,
    settings: NoxSettings,
    rangeMode: RangeMode,
    onOpenGuideline: () -> Unit
) {
    val buckets = remember(sessions, rangeMode, settings.goalMinutes) {
        bucketsFor(sessions, rangeMode, settings.goalMinutes)
    }

    val valid = buckets.filter { it.sleep > 0 }
    val avg = if (valid.isEmpty()) 0L else valid.sumOf { it.sleep } / valid.size
    val best = valid.maxByOrNull { it.sleep }?.sleep ?: 0L
    val worst = valid.minByOrNull { it.sleep }?.sleep ?: 0L
    val hitCount = buckets.count { it.goalHit && it.sleep > 0 }
    val hitTotal = buckets.count { it.sleep > 0 }
    val avgPercent = if (settings.goalMinutes <= 0) 0
    else ((avg * 100.0) / settings.goalMinutes).roundToInt().coerceIn(0, 100)

    val (ah, am, _) = hmParts(avg)

    NoxCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(
                    when (rangeMode) {
                        RangeMode.Week -> "本周平均"
                        RangeMode.Month -> "近四周平均"
                        RangeMode.Year -> "近一年平均"
                    }
                )
                Spacer(Modifier.height(4.dp))
                BigDuration(ah, am, bigSize = 27.sp, unitSize = 13.sp)
            }
            RingProgress(
                percent = avgPercent,
                size = 60.dp,
                stroke = 7.dp,
                centerTitle = "$avgPercent%",
                centerSub = "达成"
            )
        }

        Spacer(Modifier.height(18.dp))
        BucketChart(buckets, settings.goalMinutes, settings.healthReference)
    }

    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat(hm(avg), "平均时长", Modifier.weight(1f))
        MiniStat(hm(best), "最长", Modifier.weight(1f))
        MiniStat(if (worst > 0) hm(worst) else "—", "最短", Modifier.weight(1f))
    }

    if (settings.healthReference) {
        Spacer(Modifier.height(13.dp))
        GuidelineCompareCard(avgMinutes = avg, onOpen = onOpenGuideline)
    }

    Spacer(Modifier.height(13.dp))
    NoxCard(padding = 15.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MutedText("$hitCount / $hitTotal 段达成目标", Modifier.weight(1f))
            Text(
                if (avgPercent >= 100) "已达成" else "还差 ${(settings.goalMinutes - avg).coerceAtLeast(0)} 分",
                fontSize = 12.5.sp,
                color = NoxColor.Cyan,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/* -------------------------------------------------------- 按天汇总（日视图） */

/**
 * 日视图：同一天内的多段睡眠合并为「当日累计」。
 *
 * 归属规则 = 结束时刻所在本地日期（起床日）：夜里跨零点的主睡眠记到醒来那天，
 * 白天的小睡与它同归一天 —— 「今天睡了多久」就是当天所有段相加。
 */
@Composable
private fun DayTotalsView(
    dayStats: List<DayAggregate>,
    sessions: List<SleepSession>,
    settings: NoxSettings
) {
    val todayKey = LocalDate.now().toString()
    val today = dayStats.firstOrNull { it.dayKey == todayKey }

    NoxCard(padding = 16.dp) {
        Label("今天 · ${dateCn(System.currentTimeMillis())}")
        if (today == null) {
            Spacer(Modifier.height(10.dp))
            MutedText("今天还没有完成的睡眠记录。完成一段记录（进入睡眠或小憩）后，会并入当日累计。")
        } else {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val (th, tm, _) = hmParts(today.sleptMinutes)
                    BigDuration(th, tm, bigSize = 27.sp, unitSize = 13.sp)
                }
                val percent = if (settings.goalMinutes <= 0) 0
                else ((today.sleptMinutes * 100.0) / settings.goalMinutes).roundToInt().coerceIn(0, 100)
                RingProgress(
                    percent = percent,
                    size = 60.dp,
                    stroke = 7.dp,
                    centerTitle = "$percent%",
                    centerSub = "达成"
                )
            }
            Spacer(Modifier.height(8.dp))
            MutedText(
                "共 ${today.segmentCount} 段 · 在床 ${hm(today.inBedMinutes)} · " +
                    "清醒 ${hm(today.inBedMinutes - today.sleptMinutes)}",
                size = 11.5.sp
            )
            Spacer(Modifier.height(6.dp))
            DaySegments(sessions.filter { it.dayKey == todayKey })
        }
    }

    if (dayStats.isEmpty()) return

    Spacer(Modifier.height(13.dp))
    MutedText("当日累计 = 当天各段实际睡眠相加（每段 = 在床 − 清醒）。点任意一天展开分段明细。", size = 10.5.sp)

    Spacer(Modifier.height(13.dp))
    Label("按天汇总 · 最近 30 天")
    Spacer(Modifier.height(6.dp))

    var expanded by remember { mutableStateOf<String?>(null) }
    val best = dayStats.maxOfOrNull { it.sleptMinutes } ?: 1L

    NoxCard(padding = 4.dp) {
        dayStats.forEachIndexed { i, d ->
            DayRow(
                d = d,
                sessions = sessions,
                maxMinutes = best,
                goalMinutes = settings.goalMinutes,
                expanded = expanded == d.dayKey,
                onToggle = { expanded = if (expanded == d.dayKey) null else d.dayKey }
            )
            if (i != dayStats.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0EFFFFFF)))
            }
        }
    }
}

@Composable
private fun DayRow(
    d: DayAggregate,
    sessions: List<SleepSession>,
    maxMinutes: Long,
    goalMinutes: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val date = LocalDate.parse(d.dayKey)
    val frac = if (maxMinutes <= 0L) 0f
    else (d.sleptMinutes.toFloat() / maxMinutes).coerceIn(0.05f, 1f)
    val hit = d.sleptMinutes >= goalMinutes

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.size(width = 46.dp, height = 34.dp)) {
                Text(
                    date.dayOfMonth.toString().padStart(2, '0'),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink
                )
                Label(dayLabelOf(d.dayKey), fontSize = 10.sp)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color(0x14FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(NoxBrush.Moon)
                )
            }
            Spacer(Modifier.size(12.dp))
            // 只限宽不限高：两行文字（时长 + N 段）自然排布，固定高度会把第二行裁掉
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(62.dp)
            ) {
                Text(
                    hm(d.sleptMinutes),
                    fontSize = 13.sp,
                    color = if (hit) NoxColor.Ink else NoxColor.Ink2
                )
                if (d.segmentCount > 1) {
                    Text("${d.segmentCount} 段", fontSize = 9.5.sp, color = NoxColor.VioletLt)
                }
            }
        }
        if (expanded) {
            Column(
                Modifier
                    .padding(start = 57.dp, end = 11.dp)
                    .padding(bottom = 10.dp)
            ) {
                DaySegments(sessions.filter { it.dayKey == d.dayKey })
            }
        }
    }
}

/** 一天内的分段明细：每行 = 序号 · 起止时刻 ·（清醒扣减）· 该段睡眠时长 */
@Composable
private fun DaySegments(segments: List<SleepSession>) {
    segments.sortedBy { it.startAt }.forEachIndexed { i, s ->
        val e = s.endAt ?: return@forEachIndexed
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${CIRCLED.getOrElse(i) { "${i + 1}." }} ${clock(s.startAt)} → ${clock(e)}",
                fontSize = 11.5.sp,
                color = NoxColor.Ink2,
                modifier = Modifier.weight(1f)
            )
            if (s.isNap) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NoxColor.Amber.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text("小憩", fontSize = 9.5.sp, color = NoxColor.Amber)
                }
                Spacer(Modifier.size(7.dp))
            }
            val awakeMin = s.awakeMillis / 60_000L
            if (awakeMin > 0) {
                Text("清醒 ${awakeMin}m", fontSize = 10.sp, color = NoxColor.Amber)
                Spacer(Modifier.size(8.dp))
            }
            Text(
                hm(sleptMinutesOf(s.startAt, e, s.awakeMillis)),
                fontSize = 12.sp,
                color = NoxColor.Ink
            )
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NoxColor.Surface)
            .border(1.dp, NoxColor.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink)
        Spacer(Modifier.height(1.dp))
        Label(label, fontSize = 10.5.sp)
    }
}

@Composable
private fun BucketChart(buckets: List<Bucket>, goalMinutes: Int, showGuideline: Boolean) {
    val max = maxOf(
        buckets.maxOfOrNull { it.sleep } ?: 1L,
        (goalMinutes * 1.15).toLong(),
        (SleepGuideline.ADULT_MAX_MINUTES * 1.08).toLong(),
        60L
    ).toFloat()

    Column {
        Box(Modifier.fillMaxWidth().height(118.dp)) {
            // 柱（底层）
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                buckets.forEach { b ->
                    val h = (118f * b.sleep / max).coerceIn(0f, 112f)
                    val barBrush: Brush = when {
                        b.sleep <= 0L -> SolidColor(Color(0x14FFFFFF))
                        b.goalHit && b.isCurrent -> NoxBrush.BarPeak
                        b.goalHit -> NoxBrush.BarOk
                        else -> SolidColor(Color(0x22FFFFFF))
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(h.dp.coerceAtLeast(if (b.sleep > 0) 4.dp else 2.dp))
                            .clip(RoundedCornerShape(7.dp))
                            .background(barBrush)
                    )
                }
            }
            // 基准带（WHO 7–9h，中层）+ 目标虚线（顶层）
            Canvas(Modifier.fillMaxSize()) {
                if (showGuideline) {
                    val bandTop = size.height * (1f - SleepGuideline.ADULT_MAX_MINUTES.toFloat() / max)
                    val bandBottom = size.height * (1f - SleepGuideline.ADULT_MIN_MINUTES.toFloat() / max)
                    val bandH = (bandBottom - bandTop).coerceAtLeast(0f)
                    if (bandH > 0f) {
                        drawRoundRect(
                            color = NoxColor.Mist.copy(alpha = 0.09f),
                            topLeft = Offset(0f, bandTop),
                            size = Size(size.width, bandH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                    val dot = PathEffect.dashPathEffect(floatArrayOf(2f, 6f), 0f)
                    drawLine(
                        color = NoxColor.Mist.copy(alpha = 0.38f),
                        start = Offset(0f, bandTop), end = Offset(size.width, bandTop),
                        strokeWidth = 1f, pathEffect = dot
                    )
                    drawLine(
                        color = NoxColor.Mist.copy(alpha = 0.38f),
                        start = Offset(0f, bandBottom), end = Offset(size.width, bandBottom),
                        strokeWidth = 1f, pathEffect = dot
                    )
                }
                val y = size.height * (1f - goalMinutes / max)
                drawLine(
                    color = NoxColor.Cyan.copy(alpha = 0.55f),
                    start = Offset(0f, y), end = Offset(size.width, y),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f)
                )
            }
            Text(
                "8h 目标",
                fontSize = 9.sp,
                color = NoxColor.Cyan,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            if (showGuideline) {
                Text(
                    "WHO 7–9h",
                    fontSize = 9.sp,
                    color = NoxColor.MistInk,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            buckets.forEach { b ->
                Text(
                    b.label,
                    fontSize = 10.sp,
                    color = if (b.isCurrent) NoxColor.VioletLt else NoxColor.Ink2,
                    fontWeight = if (b.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 对照卡：把「周均时长」放到 WHO 推荐 7–9 小时的区间带上，给出一个不含评判的位置。
 * 只陈述"落在 / 低于 / 高于"，不用红绿标记、不催人改变。
 */
@Composable
private fun GuidelineCompareCard(avgMinutes: Long, onOpen: () -> Unit) {
    val (h, m, _) = hmParts(avgMinutes)
    val inRange = SleepGuideline.inAdultRange(avgMinutes)
    val status = when {
        inRange -> "落在 7–9 小时推荐区间内"
        avgMinutes < SleepGuideline.ADULT_MIN_MINUTES -> "低于推荐区间的下限 7 小时"
        else -> "高于推荐区间的上限 9 小时"
    }

    NoxCard(padding = 15.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("对照 WHO 推荐", Modifier.weight(1f))
            NoxChip("成人 7–9 小时")
        }

        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(26.dp)) {
            val bandStart = SleepGuideline.bandStartFraction
            val bandEnd = SleepGuideline.bandEndFraction
            val pos = SleepGuideline.positionFraction(avgMinutes)
            Canvas(Modifier.fillMaxSize()) {
                val bw = size.width
                val top = 7.dp.toPx()
                val bottom = size.height - 7.dp.toPx()
                drawRoundRect(
                    color = NoxColor.Mist.copy(alpha = 0.14f),
                    topLeft = Offset(bw * bandStart, top),
                    size = Size(bw * (bandEnd - bandStart), bottom - top),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                val px = bw * pos
                drawCircle(color = Color.White, radius = 3.5f, center = Offset(px, top))
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(px, top), end = Offset(px, bottom),
                    strokeWidth = 1.5f
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("5h", fontSize = 9.sp, color = NoxColor.Ink3)
            Text("7h", fontSize = 9.sp, color = NoxColor.Ink3)
            Text("9h", fontSize = 9.sp, color = NoxColor.Ink3)
            Text("11h", fontSize = 9.sp, color = NoxColor.Ink3)
        }

        Spacer(Modifier.height(11.dp))
        MutedText("周均 ${h}h${m}m · $status", size = 11.5.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            "区间是健康参考，不是考核线 —— 区间内没有「更优」。",
            fontSize = 10.sp,
            color = NoxColor.Ink3,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(SleepGuideline.SOURCE_LABEL, fontSize = 9.5.sp, color = NoxColor.Ink3)
            Text("了解这条基准 ›", fontSize = 11.sp, color = NoxColor.MistInk)
        }
    }
}

private fun hm(minutes: Long): String = "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}m"

/* ==================================================================== 历史 */

@Composable
fun HistoryScreen(
    sessions: List<SleepSession>,
    settings: NoxSettings,
    onOpen: (SleepSession) -> Unit,
    onPickTab: (NoxTab) -> Unit
) {
    val ordered = sessions.sortedByDescending { it.endAt ?: 0L }
    val best = ordered.maxOfOrNull { sleepMinutes(it) } ?: 1L

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            NoxTopBar(
                title = "历史",
                subtitle = "共 ${ordered.size} 条记录",
                action = { CircleIconButton(NoxIcons.Calendar, "日历") {} }
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("${ordered.size}", "已记录", Modifier.weight(1f))
                MiniStat(
                    if (ordered.isEmpty()) "—" else hm(ordered.sumOf { sleepMinutes(it) } / ordered.size),
                    "平均时长",
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))
            if (ordered.isEmpty()) {
                NoxCard {
                    Label("还没有记录")
                    Spacer(Modifier.height(8.dp))
                    MutedText("完成一次记录（进入睡眠或小憩）之后，这里会出现第一晚。")
                }
            } else {
                ordered.forEach { s ->
                    HistoryRow(s, best) { onOpen(s) }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        NoxBottomBar(NoxTab.History, onPickTab)
    }
}

@Composable
private fun HistoryRow(s: SleepSession, best: Long, onClick: () -> Unit) {
    val e = s.endAt ?: return
    val sleep = sleepMinutes(s)
    val frac = if (best <= 0) 0f else (sleep.toFloat() / best).coerceIn(0.05f, 1f)
    val hit = sleep >= 390

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.size(width = 46.dp, height = 34.dp)) {
            Text(
                dayOf(e).dayOfMonth.toString().padStart(2, '0'),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink
            )
            Label(weekdayCn(e), fontSize = 10.sp)
        }
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(NoxBrush.Moon)
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            "${sleep / 60}h${(sleep % 60).toString().padStart(2, '0')}m",
            fontSize = 13.sp,
            color = if (hit) NoxColor.Ink else NoxColor.Ink2,
            modifier = Modifier.size(width = 66.dp, height = 20.dp)
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0EFFFFFF)))
}

/* ================================================================== 单夜详情 */

@Composable
fun DetailScreen(
    session: SleepSession?,
    settings: NoxSettings,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSaveNote: (String) -> Unit,
    onSaveFeel: (String?, Int?) -> Unit
) {
    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MutedText("记录不存在")
        }
        return
    }
    val endAt = session.endAt ?: return
    val m = metricsOf(session.startAt, endAt, session.awakeMillis, settings.goalMinutes)
    val (h, mm, _) = hmParts(m.sleepMinutes)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        NoxTopBar(title = "睡眠详情", onBack = onBack, action = {
            CircleIconButton(NoxIcons.Trash, "删除", onDelete)
        })

        Spacer(Modifier.height(18.dp))
        Label(dateCn(endAt) + if (session.isNap) " · 小憩" else "")
        Spacer(Modifier.height(6.dp))
        BigDuration(h, mm, bigSize = 36.sp, unitSize = 16.sp)
        Spacer(Modifier.height(18.dp))

        TimelineBar(session)

        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("22:00", "00:00", "02:00", "04:00", "06:00", "08:00").forEach {
                Text(it, fontSize = 9.5.sp, color = NoxColor.Ink3)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(clock(session.startAt), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink)
                Label("上床", fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(clock(endAt), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink)
                Label("醒来", fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        NoxCard(padding = 4.dp) {
            DetailRow("在床时长", hm(m.inBedMinutes))
            DetailRow("其中清醒", "− ${m.awakeMinutes}m", NoxColor.Amber)
            DetailRow("实际睡眠", hm(m.sleepMinutes), NoxColor.Cyan, emphasize = true)
            DetailRow("睡眠效率", "${m.efficiency}%")
            DetailRow("夜间醒来", "${session.wakeCount} 次")
            DetailRow("醒来感觉", session.mood ?: "未填写")
            DetailRow("目标 ${settings.goalMinutes / 60}h · 达成", "${m.goalPercent}%", NoxColor.Cyan, last = true)
        }

        Spacer(Modifier.height(16.dp))
        FeelEditor(session = session, onSaveFeel = onSaveFeel)

        Spacer(Modifier.height(16.dp))
        NoteEditor(session = session, onSaveNote = onSaveNote)

        if (!session.isComplete) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NoxColor.Amber.copy(alpha = 0.10f))
                    .border(1.dp, NoxColor.Amber.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                    .padding(13.dp)
            ) {
                MutedText("这条记录不完整：记录期间 App 被系统回收过，那一段的亮屏时长无法补记。", size = 11.5.sp)
            }
        }

        session.note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(14.dp))
            Label("备注")
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NoxColor.Surface)
                    .border(1.dp, NoxColor.Line, RoundedCornerShape(14.dp))
                    .padding(13.dp)
            ) { MutedText(it) }
        }

        Spacer(Modifier.height(26.dp))
    }
}

/* ---------------------------------------------------------------- 备注编辑 */

private val DETAIL_MOODS = listOf("精神", "一般", "疲惫", "多梦")

/**
 * 醒来感觉与睡眠质量：点一下直接改，改完立即落库。
 * 这两项在结算页选过，但常常是迷糊状态下随手点的，详情页要能补救。
 */
@Composable
private fun FeelEditor(session: SleepSession, onSaveFeel: (String?, Int?) -> Unit) {
    var mood by remember(session.id, session.mood) { mutableStateOf(session.mood) }
    var quality by remember(session.id, session.quality) { mutableIntStateOf(session.quality ?: 0) }

    Label("醒来感受")
    Spacer(Modifier.height(9.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        DETAIL_MOODS.forEach { s ->
            NoxChip(s, selected = s == mood) {
                mood = if (mood == s) null else s
                onSaveFeel(mood, quality.takeIf { it > 0 })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("睡眠质量", Modifier.weight(1f), NoxColor.Ink3)
        Row {
            (1..5).forEach { i ->
                Text(
                    "★",
                    fontSize = 18.sp,
                    color = if (i <= quality) NoxColor.Amber else Color(0xFF3B4272),
                    modifier = Modifier
                        .clickable {
                            quality = if (quality == i) 0 else i
                            onSaveFeel(mood, quality.takeIf { it > 0 })
                        }
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}

/**
 * 备注编辑器。
 *
 * 平时是一行可点的卡片（长文本自动展开），点进去变编辑态，
 * 右上角出现「完成」。失焦不自动保存，避免误触把草稿吞掉。
 */
@Composable
private fun NoteEditor(session: SleepSession, onSaveNote: (String) -> Unit) {
    var editing by remember(session.id) { mutableStateOf(false) }
    var draft by remember(session.id) { mutableStateOf(session.note.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val shape = RoundedCornerShape(14.dp)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Label("备注", Modifier.weight(1f))
        if (editing) {
            Text(
                "完成",
                fontSize = 12.5.sp,
                color = NoxColor.Cyan,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable {
                        onSaveNote(draft)
                        editing = false
                        keyboard?.hide()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        } else {
            Text(
                if (session.note.isNullOrBlank()) "添加" else "编辑",
                fontSize = 12.5.sp,
                color = NoxColor.VioletLt,
                modifier = Modifier
                    .clickable {
                        draft = session.note.orEmpty()
                        editing = true
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (editing) NoxColor.Violet.copy(alpha = 0.10f) else NoxColor.Surface)
            .border(
                1.dp,
                if (editing) NoxColor.VioletLt.copy(alpha = 0.45f) else NoxColor.Line,
                shape
            )
            .then(
                if (editing) Modifier
                else Modifier.clickable {
                    draft = session.note.orEmpty()
                    editing = true
                }
            )
            .padding(13.dp)
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { if (it.length <= 200) draft = it },
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = NoxColor.Ink,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(NoxColor.VioletLt),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            if (draft.isEmpty()) {
                MutedText("睡前喝了咖啡、换了枕头、半夜被吵醒……", size = 13.sp)
            }
        } else {
            val text = session.note
            if (text.isNullOrBlank()) {
                MutedText("点这里记一笔…", size = 13.sp)
            } else {
                MutedText(text, size = 13.sp)
            }
        }
    }

    if (editing) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MutedText("${draft.length} / 200", size = 10.sp, modifier = Modifier.weight(1f))
            if (draft.isNotEmpty()) {
                Text(
                    "清空",
                    fontSize = 11.sp,
                    color = NoxColor.Ink3,
                    modifier = Modifier
                        .clickable { draft = "" }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    color: Color = NoxColor.Ink,
    emphasize: Boolean = false,
    last: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            color = if (emphasize) NoxColor.Ink else NoxColor.Ink2,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(value, fontSize = 12.5.sp, color = color, fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal)
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0EFFFFFF)))
}

/**
 * 24 小时时间轴。
 *
 * 清醒缺口用固定 5dp 宽绘制，不按时长等比 —— 12 分钟放到 10 小时轴上不足 1px，
 * 等比反而看不见。位置精确、宽度固定，精确时长交给右侧的数字。
 */
@Composable
private fun TimelineBar(session: SleepSession) {
    val endAt = session.endAt ?: return
    val endDay = dayOf(endAt)
    val winStart = startOfDay(endDay.minusDays(1)) + 22 * 3_600_000L
    val winEnd = startOfDay(endDay) + 8 * 3_600_000L
    val span = (winEnd - winStart).toFloat().coerceAtLeast(1f)

    val segments = remember(session.awakeSegments) { session.segmentRanges() }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val h = size.height
        val r = 12.dp.toPx()
        drawRoundRect(
            color = Color(0x0EFFFFFF),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            size = Size(size.width, h)
        )
        val left = ((session.startAt - winStart) / span).coerceIn(0f, 1f) * size.width
        val right = ((endAt - winStart) / span).coerceIn(0f, 1f) * size.width
        if (right > left) {
            drawRect(
                brush = NoxBrush.Aurora,
                topLeft = Offset(left, 0f),
                size = Size(right - left, h)
            )
        }
        val notchW = 5.dp.toPx()
        segments.forEach { seg ->
            val cx = ((seg.first - winStart) / span).coerceIn(0f, 1f) * size.width
            if (cx in left..right) {
                drawRoundRect(
                    color = NoxColor.Amber,
                    topLeft = Offset(cx - notchW / 2f, 0f),
                    size = Size(notchW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
        }
        drawRoundRect(
            color = Color(0x33FFFFFF),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            size = Size(size.width, h),
            style = Stroke(1.dp.toPx())
        )
    }
}
