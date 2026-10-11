package com.nox.sleep.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nox.sleep.core.NoxColor
import com.nox.sleep.core.dateCn
import com.nox.sleep.data.NoxSettings
import com.nox.sleep.data.SleepSession
import com.nox.sleep.domain.metricsOf
import com.nox.sleep.ui.components.BigDuration
import com.nox.sleep.ui.components.BuiltInBackgrounds
import com.nox.sleep.ui.components.CircleIconButton
import com.nox.sleep.ui.components.DistributionChart
import com.nox.sleep.ui.components.GroupLabel
import com.nox.sleep.ui.components.Label
import com.nox.sleep.ui.components.MutedText
import com.nox.sleep.ui.components.NoxBottomBar
import com.nox.sleep.ui.components.NoxCard
import com.nox.sleep.ui.components.NoxChip
import com.nox.sleep.ui.components.NoxTab
import com.nox.sleep.ui.components.NoxToggle
import com.nox.sleep.ui.components.NoxTopBar
import com.nox.sleep.ui.components.PrimaryButton
import com.nox.sleep.ui.components.SettingRow
import com.nox.sleep.ui.components.backgroundById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/* ==================================================================== 设置 */

@Composable
fun SettingsScreen(
    settings: NoxSettings,
    sessions: List<SleepSession>,
    onUpdate: ((NoxSettings) -> NoxSettings) -> Unit,
    onOpenThreshold: () -> Unit,
    onOpenBackground: () -> Unit,
    onClearAll: () -> Unit,
    onPickTab: (NoxTab) -> Unit
) {
    var showGoalDialog by remember { mutableStateOf(false) }
    var showBedtimeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var baseline by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // 就寝提醒依赖通知权限；未授权时提醒会静默消失，所以在打开开关时主动请求一次。
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 授予与否都不阻断开关本身：未授权时通知不会送达，用户可在系统设置里补授 */ }
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            NoxTopBar(title = "设置", subtitle = "版本 0.1.0")

            GroupLabel("记录")
            SettingRow(
                "睡眠目标",
                value = hmOf(settings.goalMinutes),
                showChevron = true,
                onClick = { showGoalDialog = true }
            )
            SettingRow("就寝提醒", trailing = {
                NoxToggle(settings.bedtimeReminder) { v ->
                    if (v) ensureNotifPermission()
                    onUpdate { it.copy(bedtimeReminder = v) }
                }
            })
            if (settings.bedtimeReminder) {
                SettingRow(
                    "提醒时间",
                    value = "%02d:%02d".format(settings.bedtimeHour, settings.bedtimeMinute),
                    showChevron = true,
                    onClick = { showBedtimeDialog = true }
                )
            }
            SettingRow("自动结束睡眠", trailing = {
                NoxToggle(settings.wakeDetection) { v -> onUpdate { it.copy(wakeDetection = v) } }
            })
            SettingRow(
                "亮屏阈值",
                value = "${settings.thresholdMinutes} 分钟",
                showChevron = true,
                onClick = onOpenThreshold
            )
            SettingRow("结束前询问我", trailing = {
                NoxToggle(settings.askBeforeEnd) { v -> onUpdate { it.copy(askBeforeEnd = v) } }
            })

            GroupLabel("显示")
            SettingRow(
                "背景",
                value = backgroundById(settings.backgroundId).name.let {
                    if (settings.customBackgroundUri != null) "自定义" else it
                },
                showChevron = true,
                onClick = onOpenBackground
            )
            SettingRow("自动调暗屏幕", trailing = {
                NoxToggle(settings.autoDim) { v -> onUpdate { it.copy(autoDim = v) } }
            })
            SettingRow("播放启动动画", trailing = {
                NoxToggle(settings.playSplash) { v -> onUpdate { it.copy(playSplash = v) } }
            })

            GroupLabel("数据")
            SettingRow(
                "导出记录（JSON）",
                showChevron = true,
                onClick = { scope.launch { exportJson(ctx, sessions) } }
            )
            SettingRow(
                "本地备份",
                value = "${sessions.size} 条"
            )
            SettingRow(
                "清除全部数据",
                showChevron = true,
                onClick = { showClearDialog = true }
            )

            // 仅 debug 包可见。醒来检测的全链路（累计亮屏 → 触发 → 确认卡片）
            // 在真实参数下要等一整晚才能验，这里把阈值压到 1 分钟做快速自测。
            // release 包里这段代码不会出现。
            if (com.nox.sleep.BuildConfig.DEBUG) {
                GroupLabel("开发者（仅调试包）")
                SettingRow(
                    "快速触发测试",
                    value = "阈值 1 分 · 最短间隔 1 分",
                    showChevron = true,
                    onClick = {
                        onUpdate {
                            it.copy(
                                wakeDetection = true,
                                thresholdMinutes = 1,
                                minIntervalMinutes = 1,
                                askBeforeEnd = true,
                                maxIntervalHours = 16
                            )
                        }
                    }
                )
                SettingRow(
                    "恢复正常参数",
                    value = "15 分 / 60 分",
                    showChevron = true,
                    onClick = { onUpdate { it.copy(thresholdMinutes = 15, minIntervalMinutes = 60) } }
                )
                SettingRow(
                    "预览就寝提醒",
                    value = "立即发一条提醒通知",
                    showChevron = true,
                    onClick = { com.nox.sleep.reminder.BedtimeReminder.notifyNow(ctx) }
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "NOX 不需要网络权限 · 所有记录只保存在这台设备上",
                fontSize = 10.sp,
                color = NoxColor.Ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
        }
        NoxBottomBar(NoxTab.Settings, onPickTab)
    }

    if (showGoalDialog) {
        var v by remember { mutableFloatStateOf(settings.goalMinutes.toFloat()) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            containerColor = NoxColor.Midnight,
            title = { Text("睡眠目标", color = NoxColor.Ink, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        hmOf(v.toInt()),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = NoxColor.Ink
                    )
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = v,
                        onValueChange = { v = it },
                        valueRange = 240f..720f,
                        steps = 15,
                        colors = sliderColors()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate { it.copy(goalMinutes = v.toInt()) }
                    showGoalDialog = false
                }) { Text("保存", color = NoxColor.Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("取消", color = NoxColor.Ink3)
                }
            }
        )
    }

    if (showBedtimeDialog) {
        var idx by remember {
            mutableFloatStateOf(bedtimeIndexOf(settings.bedtimeHour, settings.bedtimeMinute).toFloat())
        }
        val totalMinutes = 20 * 60 + idx.toInt() * 5
        val hh = (totalMinutes / 60) % 24
        val mm = totalMinutes % 60
        AlertDialog(
            onDismissRequest = { showBedtimeDialog = false },
            containerColor = NoxColor.Midnight,
            title = { Text("就寝提醒时间", color = NoxColor.Ink, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "%02d:%02d".format(hh, mm),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = NoxColor.Ink
                    )
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = idx,
                        onValueChange = { idx = it },
                        valueRange = 0f..84f,
                        steps = 83,
                        colors = sliderColors()
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("20:00", fontSize = 9.5.sp, color = NoxColor.Ink3)
                        Text("03:00", fontSize = 9.5.sp, color = NoxColor.Ink3)
                    }
                    Spacer(Modifier.height(12.dp))
                    MutedText("每晚提醒一次。系统可能在设定时间前后几分钟内送达。", size = 10.5.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate { it.copy(bedtimeHour = hh, bedtimeMinute = mm) }
                    showBedtimeDialog = false
                }) { Text("保存", color = NoxColor.Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showBedtimeDialog = false }) {
                    Text("取消", color = NoxColor.Ink3)
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = NoxColor.Midnight,
            title = { Text("清除全部记录？", color = NoxColor.Ink, fontSize = 16.sp) },
            text = { MutedText("共 ${sessions.size} 条记录将被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearDialog = false
                }) { Text("确认清除", color = NoxColor.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = NoxColor.Ink3)
                }
            }
        )
    }

    // 消除未使用告警
    if (false) Text("$baseline")
}

private fun hmOf(minutes: Int): String = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

/** 20:00 起、每 5 分钟一档（0..84）。越界值收敛到两端——UI 只会产出范围内的值。 */
private fun bedtimeIndexOf(hour: Int, minute: Int): Int {
    val off = ((hour * 60 + minute) - 20 * 60 + 1440) % 1440
    return (off / 5).coerceIn(0, 84)
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = NoxColor.Violet,
    inactiveTrackColor = Color(0x24FFFFFF)
)

private suspend fun exportJson(ctx: android.content.Context, sessions: List<SleepSession>) {
    val sb = StringBuilder()
    sb.append("[\n")
    sessions.forEachIndexed { i, s ->
        sb.append("  {\"startAt\":${s.startAt},\"endAt\":${s.endAt ?: 0},")
        sb.append("\"awakeMillis\":${s.awakeMillis},\"wakeCount\":${s.wakeCount},")
        sb.append("\"confirmedBy\":\"${s.confirmedBy}\",\"mood\":${s.mood?.let { "\"$it\"" } ?: "null"},")
        sb.append("\"quality\":${s.quality ?: 0},\"note\":${s.note?.let { "\"$it\"" } ?: "null"}}")
        if (i != sessions.lastIndex) sb.append(",")
        sb.append("\n")
    }
    sb.append("]")
    val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    withContext(Dispatchers.IO) {
        runCatching {
            val f = java.io.File(dir, "nox_export_${System.currentTimeMillis()}.json")
            f.writeText(sb.toString())
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    ctx, "已导出到 ${f.name}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

/* ================================================================ 亮屏阈值 */

@Composable
fun ThresholdScreen(
    settings: NoxSettings,
    sessions: List<SleepSession>,
    onUpdate: ((NoxSettings) -> NoxSettings) -> Unit,
    onBack: () -> Unit
) {
    var v by remember { mutableFloatStateOf(settings.thresholdMinutes.toFloat()) }
    val presets = listOf("严格 5 分" to 5, "标准 15 分" to 15, "宽松 30 分" to 30)

    // 最近 14 天的「晨间亮屏」分布（逐条记录取清醒分钟数）
    val dist = remember(sessions) {
        val zone = ZoneId.systemDefault()
        (13 downTo 0).map { back ->
            val day = LocalDate.now().minusDays(back.toLong())
            sessions.firstOrNull {
                it.endAt != null &&
                    Instant.ofEpochMilli(it.endAt!!).atZone(zone).toLocalDate() == day
            }?.let { (it.awakeMillis / 60_000L).toInt() } ?: 0
        }
    }
    val avg = dist.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.sum() / it.size }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        NoxTopBar(title = "亮屏阈值", onBack = onBack)

        Spacer(Modifier.height(22.dp))
        Label("累计亮屏多久后判定为已醒")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                v.toInt().toString(),
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraLight,
                color = NoxColor.Ink,
                lineHeight = 48.sp
            )
            Text(
                "分钟",
                fontSize = 16.sp,
                color = NoxColor.Ink2,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        MutedText("睡眠期间累计亮屏超过这个时长，就认为你已经醒了", size = 11.5.sp)

        Spacer(Modifier.height(22.dp))
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = {
                onUpdate { it.copy(thresholdMinutes = v.toInt().coerceIn(5, 60)) }
            },
            valueRange = 5f..60f,
            steps = 10,
            colors = sliderColors()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(5, 15, 30, 45, 60).forEach {
                Text("$it", fontSize = 9.5.sp, color = NoxColor.Ink3)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            presets.forEach { (name, value) ->
                NoxChip(name, selected = v.toInt() == value) {
                    v = value.toFloat()
                    onUpdate { it.copy(thresholdMinutes = value) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        NoxCard(padding = 15.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("你最近 14 天的晨间亮屏", Modifier.weight(1f))
                if (avg > 0) {
                    Text("均值 $avg 分", fontSize = 11.sp, color = NoxColor.Cyan)
                }
            }
            Spacer(Modifier.height(12.dp))
            DistributionChart(dist, v.toInt())
            Spacer(Modifier.height(9.dp))
            MutedText(
                "虚线是当前的 ${v.toInt()} 分钟阈值。低于它的那几天，说明你早晨几乎没碰手机，自动结束不会触发。",
                size = 10.sp
            )
        }

        if (v.toInt() > 30) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NoxColor.Amber.copy(alpha = 0.10f))
                    .border(1.dp, NoxColor.Amber.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .padding(13.dp)
            ) {
                MutedText("设置越大越不会自动结束，但记录可能偏长。", size = 11.5.sp)
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

/* ================================================================ 自定义背景 */

@Composable
fun BackgroundScreen(
    settings: NoxSettings,
    onUpdate: ((NoxSettings) -> NoxSettings) -> Unit,
    onBack: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onUpdate { it.copy(customBackgroundUri = uri.toString()) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        NoxTopBar(title = "背景", onBack = onBack)

        Spacer(Modifier.height(16.dp))
        Label("效果预览")
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, NoxColor.LineStrong, RoundedCornerShape(18.dp))
        ) {
            Box(Modifier.fillMaxSize().background(backgroundById(settings.backgroundId).brush))
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        NoxColor.Void.copy(
                            alpha = (0.92f - 0.30f * (settings.backgroundBrightness / 30f)).coerceIn(0.55f, 0.95f)
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Label("昨夜睡眠", fontSize = 10.sp)
                Spacer(Modifier.height(2.dp))
                BigDuration("7", "42", bigSize = 29.sp, unitSize = 13.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BuiltInBackgrounds.forEach { def ->
                val on = settings.customBackgroundUri == null && def.id == settings.backgroundId
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(def.brush)
                        .border(
                            if (on) 1.5.dp else 1.dp,
                            if (on) NoxColor.VioletLt.copy(alpha = 0.8f) else NoxColor.Line,
                            RoundedCornerShape(11.dp)
                        )
                        .clickable {
                            onUpdate { it.copy(backgroundId = def.id, customBackgroundUri = null) }
                        }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MutedText(
            backgroundById(settings.backgroundId).let { "${it.name} · ${it.desc}" },
            size = 10.5.sp
        )

        Spacer(Modifier.height(14.dp))
        SliderRow("亮度", settings.backgroundBrightness, 0, 30, "${settings.backgroundBrightness}%") {
            onUpdate { s -> s.copy(backgroundBrightness = it) }
        }
        SliderRow("模糊", settings.backgroundBlur, 0, 24, "${settings.backgroundBlur}") {
            onUpdate { s -> s.copy(backgroundBlur = it) }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton("从相册选择图片", Modifier.fillMaxWidth()) {
            picker.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NoxColor.Surface)
                .border(1.dp, NoxColor.LineStrong, RoundedCornerShape(18.dp))
                .clickable {
                    onUpdate { it.copy(customBackgroundUri = null, backgroundId = "dusk") }
                }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("恢复默认背景", fontSize = 14.5.sp, color = NoxColor.Ink3)
        }

        Spacer(Modifier.height(16.dp))
        MutedText(
            "睡眠中页与启动动画始终使用纯深色，不套用背景。所选图片只在本地处理，不会离开这台设备。",
            size = 10.sp
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SliderRow(
    name: String,
    value: Int,
    min: Int,
    max: Int,
    display: String,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 12.5.sp, color = NoxColor.Ink2, modifier = Modifier.width(34.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat().coerceAtLeast(min + 1f),
            colors = sliderColors(),
            modifier = Modifier.weight(1f)
        )
        Text(
            display,
            fontSize = 11.5.sp,
            color = NoxColor.Ink3,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp)
        )
    }
}
