package com.nox.sleep.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nox.sleep.data.SessionKind
import com.nox.sleep.ui.components.NoxBackground
import com.nox.sleep.ui.components.NoxTab

sealed interface Screen {
    data object Splash : Screen
    data object Home : Screen
    data object Sleeping : Screen
    data class Summary(val sessionId: Long) : Screen
    data object Stats : Screen
    data object History : Screen
    data class Detail(val sessionId: Long) : Screen
    data object Settings : Screen
    data object Threshold : Screen
    data object Backgrounds : Screen
    data object Guideline : Screen
    data object Sources : Screen
    data object Confirm : Screen
}

@Composable
fun NoxRoot(vm: NoxViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val elapsed by vm.elapsed.collectAsStateWithLifecycle()
    val awakeMinutes by vm.awakeMinutes.collectAsStateWithLifecycle()
    val awaitingConfirm by vm.awaitingConfirm.collectAsStateWithLifecycle()
    val pendingEndAt by vm.pendingEndAt.collectAsStateWithLifecycle()
    val dayStats by vm.dayStats.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
    var lastActiveId by remember { mutableLongStateOf(0L) }
    var pendingSummary by remember { mutableStateOf<Long?>(null) }

    // 知识屏可能从统计页或设置页进入，返回时回到来源页
    var guidelineFrom by remember { mutableStateOf<Screen>(Screen.Stats) }

    // 设置页里选中动画样式后的一次性预览：非空时全屏播放该动画
    var splashPreview by remember { mutableStateOf<SplashStyle?>(null) }

    // 只有真的见过活跃会话，才允许因为它消失而离开睡眠中页。
    // 否则「点进入睡眠」那一瞬间 active 还是 null，会被误判成已结束而弹回首页。
    var sawActive by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        val cur = active
        if (cur != null) {
            sawActive = true
            lastActiveId = cur.id
        }
    }
    LaunchedEffect(screen) { if (screen != Screen.Sleeping) sawActive = false }

    // 服务触发醒来检测后，无论用户当前在哪一屏，都切到确认页
    LaunchedEffect(awaitingConfirm) {
        if (awaitingConfirm && screen != Screen.Splash) screen = Screen.Confirm
    }

    // 等服务把 endAt 落库之后再去结算页，避免读到半截数据
    LaunchedEffect(active, sessions, pendingSummary) {
        val pid = pendingSummary ?: return@LaunchedEffect
        if (sessions.any { it.id == pid && it.endAt != null }) {
            pendingSummary = null
            screen = Screen.Summary(pid)
        }
    }

    LaunchedEffect(active, tracking, pendingSummary, sawActive) {
        if (screen == Screen.Sleeping && sawActive && active == null &&
            !tracking && pendingSummary == null
        ) {
            screen = Screen.Home
        }
    }

    val goTab: (NoxTab) -> Unit = { tab ->
        screen = when (tab) {
            NoxTab.Home -> Screen.Home
            NoxTab.Stats -> Screen.Stats
            NoxTab.History -> Screen.History
            NoxTab.Settings -> Screen.Settings
        }
    }

    BackHandler(enabled = screen != Screen.Home && screen != Screen.Splash) {
        screen = when (screen) {
            is Screen.Detail, Screen.Stats, Screen.History, Screen.Settings -> Screen.Home
            Screen.Threshold, Screen.Backgrounds -> Screen.Settings
            Screen.Guideline -> guidelineFrom
            Screen.Sources -> Screen.Guideline
            is Screen.Summary -> Screen.Home
            Screen.Confirm, Screen.Sleeping -> Screen.Home
            else -> Screen.Home
        }
    }

    // 背景只在允许套用的页面生效：睡眠中、启动动画、确认卡片与动画预览一律纯深色
    val allowBackground = screen != Screen.Sleeping &&
        screen != Screen.Splash &&
        screen != Screen.Confirm &&
        splashPreview == null

    NoxBackground(settings = settings, applyCustom = allowBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (val s = screen) {

                Screen.Splash -> {
                    val splashStyle = SplashStyle.of(settings.splashStyle)
                    SplashScreen(
                        style = splashStyle,
                        autoAdvanceMillis = if (settings.playSplash) splashStyle.durationMillis else 350L,
                        onFinish = {
                            screen = when {
                                awaitingConfirm -> Screen.Confirm
                                active != null -> Screen.Sleeping
                                else -> Screen.Home
                            }
                        }
                    )
                }

                Screen.Home -> HomeScreen(
                    settings = settings,
                    latest = sessions.maxByOrNull { it.endAt ?: 0L },
                    weeklyAvgMinutes = weeklyAverage(sessions),
                    streakDays = streak(sessions),
                    onStartSleep = {
                        vm.startSleep(SessionKind.Sleep.key)
                        screen = Screen.Sleeping
                    },
                    onStartNap = {
                        vm.startSleep(SessionKind.Nap.key)
                        screen = Screen.Sleeping
                    },
                    onOpenSettings = { screen = Screen.Settings },
                    onPickTab = goTab
                )

                Screen.Sleeping -> {
                    val cur = active
                    if (cur != null) {
                        SleepingScreen(
                            settings = settings,
                            startAt = cur.startAt,
                            elapsedMillis = elapsed,
                            awakeMinutes = awakeMinutes,
                            nap = cur.isNap,
                            onWakeUp = {
                                pendingSummary = cur.id
                                vm.stopSleep()
                            },
                            onDiscard = {
                                vm.discardActive()
                                screen = Screen.Home
                            }
                        )
                    }
                }

                Screen.Confirm -> ConfirmScreen(
                    startAt = active?.startAt ?: (pendingEndAt - 60_000L),
                    endAt = if (pendingEndAt > 0L) pendingEndAt else System.currentTimeMillis(),
                    awaitSeconds = settings.confirmWindowSeconds,
                    nap = active?.isNap == true,
                    onConfirm = {
                        pendingSummary = lastActiveId
                        vm.confirmWake()
                    },
                    onKeepSleeping = {
                        vm.keepSleeping()
                        screen = Screen.Sleeping
                    }
                )

                is Screen.Summary -> {
                    val target = sessions.firstOrNull { it.id == s.sessionId }
                    if (target == null || target.endAt == null) {
                        LaunchedEffect(s.sessionId) { screen = Screen.Home }
                    } else {
                        SummaryScreen(
                            session = target,
                            settings = settings,
                            onSave = { mood, quality, note ->
                                vm.saveMeta(target.id, mood, quality, note)
                                screen = Screen.Home
                            },
                            onDiscard = {
                                vm.delete(target)
                                screen = Screen.Home
                            }
                        )
                    }
                }

                Screen.Stats -> StatsScreen(
                    sessions = sessions,
                    dayStats = dayStats,
                    settings = settings,
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenGuideline = {
                        guidelineFrom = Screen.Stats
                        screen = Screen.Guideline
                    },
                    onPickTab = goTab
                )

                Screen.History -> HistoryScreen(
                    sessions = sessions,
                    settings = settings,
                    onOpen = { screen = Screen.Detail(it.id) },
                    onPickTab = goTab
                )

                is Screen.Detail -> DetailScreen(
                    session = sessions.firstOrNull { it.id == s.sessionId },
                    settings = settings,
                    onBack = { screen = Screen.History },
                    onDelete = {
                        sessions.firstOrNull { it.id == s.sessionId }?.let { vm.delete(it) }
                        screen = Screen.History
                    },
                    onSaveNote = { note -> vm.updateNote(s.sessionId, note) },
                    onSaveFeel = { mood, quality -> vm.updateFeel(s.sessionId, mood, quality) }
                )

                Screen.Settings -> SettingsScreen(
                    settings = settings,
                    sessions = sessions,
                    onUpdate = vm::updateSettings,
                    onOpenThreshold = { screen = Screen.Threshold },
                    onOpenBackground = { screen = Screen.Backgrounds },
                    onOpenGuideline = {
                        guidelineFrom = Screen.Settings
                        screen = Screen.Guideline
                    },
                    onOpenSources = { screen = Screen.Sources },
                    onClearAll = vm::clearAll,
                    onPreviewSplash = { splashPreview = it },
                    onSeedData = vm::seedSampleData,
                    onClearSamples = vm::clearSampleData,
                    onPickTab = goTab
                )

                Screen.Guideline -> GuidelineScreen(
                    onBack = { screen = guidelineFrom },
                    onOpenSources = { screen = Screen.Sources }
                )

                Screen.Sources -> SourcesScreen(
                    onBack = { screen = Screen.Guideline }
                )

                Screen.Threshold -> ThresholdScreen(
                    settings = settings,
                    sessions = sessions,
                    onUpdate = vm::updateSettings,
                    onBack = { screen = Screen.Settings }
                )

                Screen.Backgrounds -> BackgroundScreen(
                    settings = settings,
                    onUpdate = vm::updateSettings,
                    onBack = { screen = Screen.Settings }
                )
            }

            // 设置页触发的一次性预览：全屏播放所选动画，点击或播完自动退出
            splashPreview?.let { st ->
                SplashScreen(
                    style = st,
                    autoAdvanceMillis = st.durationMillis,
                    onFinish = { splashPreview = null }
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ 聚合工具 */

private fun weeklyAverage(sessions: List<com.nox.sleep.data.SleepSession>): Long {
    val cutoff = System.currentTimeMillis() - 7L * 24 * 3_600_000L
    val recent = sessions.filter { (it.endAt ?: 0L) >= cutoff }
    if (recent.isEmpty()) return 0L
    val total = recent.sumOf { s ->
        val e = s.endAt ?: return@sumOf 0L
        (((e - s.startAt) / 60_000L) - s.awakeMillis / 60_000L).coerceAtLeast(0L)
    }
    return total / recent.size
}

private fun streak(sessions: List<com.nox.sleep.data.SleepSession>): Int {
    if (sessions.isEmpty()) return 0
    val zone = java.time.ZoneId.systemDefault()
    val days = sessions.mapNotNull { s ->
        s.endAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    }.distinct().sortedDescending()
    var count = 0
    var cursor = java.time.LocalDate.now()
    val first = days.firstOrNull() ?: return 0
    if (first != cursor && first != cursor.minusDays(1)) return 0
    if (first == cursor.minusDays(1)) cursor = cursor.minusDays(1)
    for (d in days) {
        if (d == cursor) {
            count++
            cursor = cursor.minusDays(1)
        } else if (d.isBefore(cursor)) break
    }
    return count
}
