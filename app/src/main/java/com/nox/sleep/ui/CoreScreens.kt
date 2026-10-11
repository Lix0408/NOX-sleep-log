package com.nox.sleep.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nox.sleep.core.Crescent
import com.nox.sleep.core.NoxBrush
import com.nox.sleep.core.NoxColor
import com.nox.sleep.core.NoxIcons
import com.nox.sleep.core.clock
import com.nox.sleep.core.dateCn
import com.nox.sleep.core.greetingFor
import com.nox.sleep.core.hmCn
import com.nox.sleep.core.hmParts
import com.nox.sleep.core.hms
import com.nox.sleep.data.NoxSettings
import com.nox.sleep.data.SleepSession
import com.nox.sleep.domain.metricsOf
import com.nox.sleep.ui.components.BigDuration
import com.nox.sleep.ui.components.CircleIconButton
import com.nox.sleep.ui.components.GhostButton
import com.nox.sleep.ui.components.Label
import com.nox.sleep.ui.components.MutedText
import com.nox.sleep.ui.components.NoxBottomBar
import com.nox.sleep.ui.components.NoxCard
import com.nox.sleep.ui.components.NoxChip
import com.nox.sleep.ui.components.NoxTab
import com.nox.sleep.ui.components.NoxToggle
import com.nox.sleep.ui.components.PrimaryButton
import com.nox.sleep.ui.components.RingProgress
import com.nox.sleep.ui.components.SettingRow
import java.time.LocalDateTime

/* ==================================================================== 首页 */

@Composable
fun HomeScreen(
    settings: NoxSettings,
    latest: SleepSession?,
    weeklyAvgMinutes: Long,
    streakDays: Int,
    onStartSleep: () -> Unit,
    onStartNap: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickTab: (NoxTab) -> Unit
) {
    val now = remember { LocalDateTime.now() }
    val ctx = LocalContext.current

    // 开始记录前确保通知权限：睡眠计时通知与醒来确认卡片都依赖它
    // （Android 13+ 需运行时授权，未授权时通知会静默失败）
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val startWithPermission: (() -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        action()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(dateCn(System.currentTimeMillis()))
                    Text(
                        greetingFor(now.hour),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NoxColor.Ink
                    )
                }
                CircleIconButton(NoxIcons.Settings, "设置", onOpenSettings)
            }

            Spacer(Modifier.height(16.dp))

            if (latest != null && latest.endAt != null) {
                val m = metricsOf(latest.startAt, latest.endAt, latest.awakeMillis, settings.goalMinutes)
                val (h, mm, _) = hmParts(m.sleepMinutes)
                if (latest.isNap) {
                    // 最新一条是小憩：换文案、不拿 8h 目标去比 —— 它同样已计入当日汇总
                    NoxCard {
                        Label("最近小憩")
                        Spacer(Modifier.height(6.dp))
                        BigDuration(h, mm)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${clock(latest.startAt)} → ${clock(latest.endAt)} · 已计入当日汇总",
                            fontSize = 11.5.sp,
                            color = NoxColor.Ink2
                        )
                    }
                } else {
                    NoxCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label("昨夜睡眠")
                                Spacer(Modifier.height(6.dp))
                                BigDuration(h, mm)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${clock(latest.startAt)} 上床 · ${clock(latest.endAt)} 醒来",
                                    fontSize = 11.5.sp,
                                    color = NoxColor.Ink2
                                )
                            }
                            RingProgress(
                                percent = m.goalPercent,
                                size = 76.dp,
                                stroke = 6.dp,
                                centerTitle = "${m.goalPercent}%",
                                centerSub = "达成"
                            )
                        }
                    }
                }
            } else {
                NoxCard {
                    Label("还没有记录")
                    Spacer(Modifier.height(8.dp))
                    MutedText("睡前点「进入睡眠」，打盹点「小憩」，醒来点「我醒了」。")
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NapButton { startWithPermission(onStartNap) }
                    StartSleepButton { startWithPermission(onStartSleep) }
                }
                Spacer(Modifier.height(10.dp))
                MutedText(
                    "小憩与睡眠同样计入当日汇总",
                    size = 10.5.sp,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell(if (weeklyAvgMinutes > 0) minor(weeklyAvgMinutes) else "—", "本周平均", Modifier.weight(1f))
                StatCell(if (streakDays > 0) "$streakDays 天" else "—", "连续记录", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
        }
        NoxBottomBar(NoxTab.Home, onPickTab)
    }
}

private fun minor(minutes: Long): String {
    val (h, m, _) = hmParts(minutes)
    return "${h}h${m}m"
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
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
private fun StartSleepButton(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p1 by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "p1"
    )
    val p2 by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(3400, easing = LinearEasing),
            RepeatMode.Restart,
            initialStartOffset = StartOffset(1700)
        ),
        label = "p2"
    )

    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        listOf(p1, p2).forEach { p ->
            Box(
                Modifier
                    .size(158.dp)
                    .scale(1f + 0.52f * p)
                    .alpha(0.7f * (1f - p))
                    .border(1.dp, NoxColor.Violet.copy(alpha = 0.45f), CircleShape)
            )
        }
        Box(
            Modifier
                .size(158.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NoxColor.Violet.copy(alpha = 0.42f),
                            NoxColor.Violet.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, NoxColor.VioletLt.copy(alpha = 0.30f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Crescent(size = 42.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "进入睡眠",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE7E3FF),
                    letterSpacing = 0.14.sp
                )
            }
        }
    }
}

/**
 * 小憩入口：更小、暖色、无脉冲 —— 与主按钮「进入睡眠」形成主次对比。
 * 数据上与小憩段完全等价（同样计入当日汇总），只是标记与文案不同。
 */
@Composable
private fun NapButton(onClick: () -> Unit) {
    Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NoxColor.Amber.copy(alpha = 0.30f),
                            NoxColor.Amber.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, NoxColor.Amber.copy(alpha = 0.34f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Crescent(
                    size = 30.dp,
                    brush = Brush.linearGradient(listOf(Color(0xFFFFD9A6), Color(0xFFE8A25C)))
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "小憩",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF5D9B0),
                    letterSpacing = 0.12.sp
                )
            }
        }
    }
}

/* ================================================================== 睡眠中 */

@Composable
fun SleepingScreen(
    settings: NoxSettings,
    startAt: Long,
    elapsedMillis: Long,
    awakeMinutes: Long,
    nap: Boolean = false,
    onWakeUp: () -> Unit,
    onDiscard: () -> Unit
) {
    val breathe = rememberInfiniteTransition(label = "breathe")
    val scale by breathe.animateFloat(
        1f, 1.07f,
        infiniteRepeatable(tween(2750, easing = LinearEasing), RepeatMode.Reverse),
        label = "bs"
    )
    val glow by breathe.animateFloat(
        0.92f, 1f,
        infiniteRepeatable(tween(2750, easing = LinearEasing), RepeatMode.Reverse),
        label = "bg"
    )

    val expectedWake = startAt + settings.goalMinutes * 60_000L
    val sleptMinutes = elapsedMillis / 60_000L

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D1334), Color(0xFF070B1C), Color(0xFF03050C)),
                    radius = 1200f
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (nap) NoxColor.Amber.copy(alpha = 0.10f)
                        else NoxColor.Violet.copy(alpha = 0.10f)
                    )
                    .border(
                        1.dp,
                        if (nap) NoxColor.Amber.copy(alpha = 0.30f)
                        else NoxColor.VioletLt.copy(alpha = 0.28f),
                        CircleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    if (nap) "小憩中 · 勿扰已开启" else "睡眠中 · 勿扰已开启",
                    fontSize = 11.sp,
                    color = Color(0xFFB9C0E4),
                    letterSpacing = 0.05.sp
                )
            }

            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(210.dp)
                        .scale(scale)
                        .alpha(glow)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    if (nap) NoxColor.Amber.copy(alpha = 0.34f)
                                    else NoxColor.Violet.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                Box(Modifier.scale(scale)) { Crescent(size = 116.dp) }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                hms(elapsedMillis),
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-0.01).sp,
                color = Color(0xFFF2F4FF)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (nap) "已小憩 ${hmCn(sleptMinutes)}\n建议 20–40 分钟"
                else "已睡眠 ${hmCn(sleptMinutes)}\n预计 ${clock(expectedWake)} 起床",
                fontSize = 12.5.sp,
                color = NoxColor.Ink2,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))

            val detectOn = settings.wakeDetection
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (detectOn) NoxColor.Violet.copy(alpha = 0.10f)
                        else NoxColor.Surface
                    )
                    .border(
                        1.dp,
                        if (detectOn) NoxColor.VioletLt.copy(alpha = 0.24f) else NoxColor.Line,
                        CircleShape
                    )
                    .padding(horizontal = 13.dp, vertical = 5.dp)
            ) {
                Text(
                    if (detectOn) "醒来检测运行中 · 累计亮屏 $awakeMinutes 分"
                    else "醒来检测已关闭 · 需要手动结束",
                    fontSize = 10.sp,
                    color = Color(0xFF9AA3C7),
                    letterSpacing = 0.04.sp
                )
            }

            Spacer(Modifier.weight(1f))

            GhostButton(
                text = "我醒了",
                modifier = Modifier.fillMaxWidth(),
                borderColor = NoxColor.VioletLt.copy(alpha = 0.42f),
                textColor = Color(0xFFE7E3FF),
                onClick = onWakeUp
            )
            Spacer(Modifier.height(13.dp))
            Text(
                "长按可放弃本次记录",
                fontSize = 10.5.sp,
                color = NoxColor.Ink3,
                letterSpacing = 0.06.sp,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onDiscard() })
                }
            )
        }
    }
}

/* ==================================================================== 结算 */

private val MOODS = listOf("精神", "一般", "疲惫", "多梦")

@Composable
fun SummaryScreen(
    session: SleepSession,
    settings: NoxSettings,
    onSave: (mood: String?, quality: Int?, note: String?) -> Unit,
    onDiscard: () -> Unit
) {
    val endAt = session.endAt ?: System.currentTimeMillis()
    val m = metricsOf(session.startAt, endAt, session.awakeMillis, settings.goalMinutes)
    val (h, mm, _) = hmParts(m.sleepMinutes)

    var quality by remember { mutableIntStateOf(session.quality ?: 4) }
    var mood by remember { mutableStateOf(session.mood ?: "精神") }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(dateCn(endAt) + if (session.isNap) " · 小憩" else "")
                    Text(
                        greetingFor(LocalDateTime.now().hour),
                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink
                    )
                }
                CircleIconButton(NoxIcons.Close, "放弃", onDiscard)
            }

            Spacer(Modifier.height(14.dp))

            Label(if (session.isNap) "刚刚小憩了" else "你昨晚睡了")
            Spacer(Modifier.height(6.dp))
            BigDuration(h, mm, bigSize = 36.sp, unitSize = 16.sp)
            Spacer(Modifier.height(14.dp))

            SleepSpanBar()
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpanPin(clock(session.startAt), if (session.isNap) "开始" else "上床")
                SpanPin(clock(endAt), "醒来", alignEnd = true)
            }

            Spacer(Modifier.height(14.dp))

            NoxCard(padding = 4.dp) {
                FourNumberRow("在床时长", hmShort(m.inBedMinutes), NoxColor.Ink)
                FourNumberRow("其中清醒（累计亮屏）", "− ${m.awakeMinutes}m", NoxColor.Amber)
                FourNumberRow("实际睡眠", hmShort(m.sleepMinutes), NoxColor.Cyan, emphasize = true)
                FourNumberRow("睡眠效率", "${m.efficiency}%", NoxColor.Ink, last = true)
            }

            Spacer(Modifier.height(14.dp))

            NoxCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("睡眠质量", Modifier.weight(1f), NoxColor.Ink3)
                    Row {
                        (1..5).forEach { i ->
                            Text(
                                "★",
                                fontSize = 16.sp,
                                color = if (i <= quality) NoxColor.Amber else Color(0xFF3B4272),
                                modifier = Modifier
                                    .clickable { quality = i }
                                    .padding(horizontal = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Label("醒来感觉")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MOODS.forEach { s ->
                    NoxChip(s, selected = s == mood, onClick = { mood = s })
                }
            }
        }

        Column(Modifier.padding(horizontal = 22.dp)) {
            PrimaryButton("保存记录", Modifier.fillMaxWidth()) {
                onSave(mood, quality, null)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "不填任何内容也可以直接保存",
                fontSize = 10.5.sp,
                color = NoxColor.Ink3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun hmShort(minutes: Long): String = "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}m"

@Composable
private fun FourNumberRow(
    label: String,
    value: String,
    color: Color,
    emphasize: Boolean = false,
    last: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 11.dp, vertical = if (last) 11.dp else 9.dp),
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
    if (!last) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0EFFFFFF)))
    }
}

@Composable
private fun SleepSpanBar() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(NoxBrush.Moon)
    )
}

@Composable
private fun SpanPin(value: String, label: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink)
        Label(label, fontSize = 10.sp)
    }
}

/* ================================================================ 醒来确认 */

@Composable
fun ConfirmScreen(
    startAt: Long,
    endAt: Long,
    awaitSeconds: Int,
    nap: Boolean = false,
    onConfirm: () -> Unit,
    onKeepSleeping: () -> Unit
) {
    val minutes = ((endAt - startAt) / 60_000L).coerceAtLeast(0L)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080D28), Color(0xFF141A44), Color(0xFF2A1E42))
                )
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    clock(System.currentTimeMillis()),
                    fontSize = 58.sp,
                    fontWeight = FontWeight.ExtraLight,
                    color = Color(0xFFF2F4FF),
                    lineHeight = 62.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    dateCn(System.currentTimeMillis()),
                    fontSize = 11.5.sp,
                    color = Color(0xFFA9B1D6),
                    letterSpacing = 0.08.sp
                )
            }

            Column {
                NoxCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFF232A5C), Color(0xFF0A0E20))
                                    )
                                )
                                .border(1.dp, NoxColor.Line, RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Crescent(size = 17.dp, brush = Brush.linearGradient(listOf(NoxColor.VioletLt, NoxColor.Violet)))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("NOX", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink)
                            Label("现在", fontSize = 10.sp)
                        }
                    }

                    Spacer(Modifier.height(13.dp))
                    Text("看起来你已经醒了", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = NoxColor.Ink)
                    Spacer(Modifier.height(5.dp))
                    MutedText(
                        if (nap) "检测到你已经用了一会儿手机。要现在结束这次小憩吗？"
                        else "检测到你已经用了一会儿手机。要现在结束昨晚的记录吗？",
                        size = 11.5.sp
                    )

                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NoxColor.Violet.copy(alpha = 0.15f))
                            .border(1.dp, NoxColor.VioletLt.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${clock(startAt)} → ${clock(endAt)} · ${hmCn(minutes)}",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE7E3FF)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "结束点会回到你点亮屏幕的那一刻",
                                fontSize = 10.5.sp,
                                color = NoxColor.Ink3
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        GhostButton("我还想再睡", Modifier.weight(1f)) { onKeepSleeping() }
                        Box(Modifier.weight(1.25f)) {
                            PrimaryButton("对，我醒了", Modifier.fillMaxWidth()) { onConfirm() }
                        }
                    }

                    Spacer(Modifier.height(11.dp))
                    Text(
                        "$awaitSeconds 秒内不作选择\n将按「继续睡眠」处理",
                        fontSize = 9.5.sp,
                        color = NoxColor.Ink3,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/* ============================================================ 设置里用的小行 */

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title = title, trailing = { NoxToggle(checked, onChange) })
}
