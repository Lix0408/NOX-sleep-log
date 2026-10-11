package com.nox.sleep.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nox.sleep.NoxApplication
import com.nox.sleep.data.DayAggregate
import com.nox.sleep.data.NoxSettings
import com.nox.sleep.data.SleepSession
import com.nox.sleep.debug.DebugSeed
import com.nox.sleep.reminder.BedtimeReminder
import com.nox.sleep.service.SleepTrackingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoxViewModel(app: Application) : AndroidViewModel(app) {

    private val nox = app as NoxApplication
    private val repo = nox.repo

    val settings: StateFlow<NoxSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NoxSettings())

    val sessions: StateFlow<List<SleepSession>> = repo.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按天汇总（最近 30 个有记录的天），统计页「日」视图的数据源 */
    val dayStats: StateFlow<List<DayAggregate>> = repo.dayAggregates(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val active: StateFlow<SleepSession?> = repo.active
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 直接暴露服务的运行时状态，避免多一份真相源
    val tracking = SleepTrackingService.running
    val elapsed = SleepTrackingService.elapsed
    val awakeMinutes = SleepTrackingService.awakeMinutes
    val awaitingConfirm = SleepTrackingService.awaitingConfirm
    val pendingEndAt = SleepTrackingService.pendingEndAt

    init {
        recoverIfNeeded()
        syncBedtimeReminder()
    }

    /**
     * 就寝提醒调度：只在「开关 / 时间」变化时重排（幂等调用）。
     * App 每次启动也会跑一遍，顺带修复闹钟因重启、强制停止等原因丢失的情况。
     */
    private fun syncBedtimeReminder() {
        viewModelScope.launch {
            settings
                .distinctUntilChangedBy {
                    Triple(it.bedtimeReminder, it.bedtimeHour, it.bedtimeMinute)
                }
                .collect { BedtimeReminder.sync(nox, it) }
        }
    }

    /**
     * 异常恢复：存在未闭环会话但服务没在跑（进程被杀过），
     * 则按原地起点重启监测，并把该条标记为「不完整」——kill 期间的亮屏无法补记。
     */
    private fun recoverIfNeeded() {
        viewModelScope.launch {
            val act = repo.activeNow() ?: return@launch
            if (SleepTrackingService.running.value) return@launch
            val age = System.currentTimeMillis() - act.startAt
            if (age < settings.value.maxIntervalMillis) {
                repo.markIncomplete(act.id)
                SleepTrackingService.start(getApplication(), act.id, act.startAt)
            }
        }
    }

    // ------------------------------------------------------------------ 主流程

    fun startSleep() {
        if (active.value != null) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = repo.start(now)
            SleepTrackingService.start(getApplication(), id, now)
        }
    }

    /** 手动结束：服务算好时长后落库 */
    fun stopSleep() = SleepTrackingService.send(getApplication(), SleepTrackingService.ACTION_STOP)

    /** 长按放弃：先删掉未闭环的记录，再让服务收口（此时它会发现记录已不存在而直接退出） */
    fun discardActive() {
        val act = active.value ?: return
        viewModelScope.launch {
            repo.delete(act)
            SleepTrackingService.send(getApplication(), SleepTrackingService.ACTION_STOP)
        }
    }

    /** 确认卡片的两个选项 */
    fun confirmWake() =
        SleepTrackingService.send(getApplication(), SleepTrackingService.ACTION_CONFIRM_WAKE)

    fun keepSleeping() =
        SleepTrackingService.send(getApplication(), SleepTrackingService.ACTION_KEEP_SLEEPING)

    // ------------------------------------------------------------------ 记录管理

    fun saveMeta(id: Long, mood: String?, quality: Int?, note: String?) {
        viewModelScope.launch { repo.saveMeta(id, mood, quality, note) }
    }

    fun updateNote(id: Long, note: String?) {
        viewModelScope.launch { repo.updateNote(id, note) }
    }

    fun updateFeel(id: Long, mood: String?, quality: Int?) {
        viewModelScope.launch { repo.updateFeel(id, mood, quality) }
    }

    fun delete(session: SleepSession) {
        viewModelScope.launch { repo.delete(session) }
    }

    fun clearAll() {
        viewModelScope.launch { repo.clearAll() }
    }

    /** 仅调试包：生成 / 清除示例数据（用于验收「按天汇总」） */
    fun seedSampleData() {
        viewModelScope.launch { DebugSeed.seed(repo) }
    }

    fun clearSampleData() {
        viewModelScope.launch { repo.clearSamples() }
    }

    fun updateSettings(transform: (NoxSettings) -> NoxSettings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }
}
