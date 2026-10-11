package com.nox.sleep.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nox.sleep.MainActivity
import com.nox.sleep.R
import com.nox.sleep.core.hm
import com.nox.sleep.data.NoxDatabase
import com.nox.sleep.data.NoxSettings
import com.nox.sleep.data.SettingsStore
import com.nox.sleep.data.SleepRepository
import com.nox.sleep.domain.WakeLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 睡眠计时前台服务。
 *
 * 这一层同时承担两件事：
 *  1. 让计时在切出 App 后继续走（前台服务）
 *  2. 监听屏幕亮灭，累计「清醒时长」并执行醒来检测
 *
 * 两者天然重合，所以不需要为醒来检测单独做验证程序——把监听注册上并打日志即可。
 */
class SleepTrackingService : Service() {

    companion object {
        private const val TAG = "NoxService"

        const val ACTION_START = "com.nox.sleep.action.START"
        const val ACTION_STOP = "com.nox.sleep.action.STOP"
        const val ACTION_CONFIRM_WAKE = "com.nox.sleep.action.CONFIRM_WAKE"
        const val ACTION_KEEP_SLEEPING = "com.nox.sleep.action.KEEP_SLEEPING"

        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_START_AT = "start_at"

        private const val CH_TRACKING = "nox_tracking"
        private const val CH_CONFIRM = "nox_confirm"
        private const val NOTIF_TRACKING = 1001
        private const val NOTIF_CONFIRM = 1002

        /** UI 可直接观察的运行时状态 */
        val running = MutableStateFlow(false)
        val elapsed = MutableStateFlow(0L)
        val awakeMinutes = MutableStateFlow(0L)
        val awaitingConfirm = MutableStateFlow(false)
        val pendingEndAt = MutableStateFlow(0L)

        fun start(context: Context, sessionId: Long, startAt: Long) {
            val i = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_START_AT, startAt)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun send(context: Context, action: String) {
            val i = Intent(context, SleepTrackingService::class.java).setAction(action)
            runCatching { context.startService(i) }
                .onFailure { Log.w(TAG, "send($action) 失败: ${it.message}") }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: SleepRepository

    private var sessionId = -1L
    private var startAt = 0L
    private var cfg: NoxSettings = NoxSettings()

    private val segments = mutableListOf<LongRange>()
    private var screenOnAt = 0L
    private var interactive = false
    private var triggered = false
    private var keepSleepingUntil = 0L
    private var confirmJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    interactive = true
                    screenOnAt = System.currentTimeMillis()
                    Log.d(TAG, "屏幕点亮 @ $screenOnAt")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    closeCurrentSegment()
                    interactive = false
                    Log.d(TAG, "屏幕熄灭，累计亮屏 ${awakeMillisNow() / 60000} 分，共 ${segments.size} 段")
                    scope.launch { checkpoint() }
                }
                Intent.ACTION_USER_PRESENT -> {
                    if (screenOnAt == 0L) screenOnAt = System.currentTimeMillis()
                    interactive = true
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext
        val db = NoxDatabase.get(app)
        repo = SleepRepository(db.dao(), SettingsStore(app))
        createChannels()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // 这三个都是系统广播，系统始终可以投递到非导出接收器
        ContextCompat.registerReceiver(
            this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                startAt = intent.getLongExtra(EXTRA_START_AT, System.currentTimeMillis())
                triggered = false
                keepSleepingUntil = 0L
                awaitingConfirm.value = false
                segments.clear()
                screenOnAt = if (isInteractive()) System.currentTimeMillis() else 0L
                interactive = isInteractive()

                startForegroundCompat()
                scope.launch { cfg = repo.settingsNow() }
                startTicker()
                Log.i(TAG, "开始记录 session=$sessionId startAt=$startAt")
            }

            ACTION_STOP -> {
                scope.launch { finalizeNow("manual", System.currentTimeMillis()) }
            }

            ACTION_CONFIRM_WAKE -> {
                confirmJob?.cancel()
                awaitingConfirm.value = false
                val end = pendingEndAt.value.takeIf { it > 0 } ?: System.currentTimeMillis()
                scope.launch { finalizeNow("auto", end) }
            }

            ACTION_KEEP_SLEEPING -> {
                confirmJob?.cancel()
                onKeepSleeping()
            }
        }
        return START_STICKY
    }

    private fun startTicker() {
        scope.launch {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                elapsed.value = now - startAt
                awakeMinutes.value = awakeMillisNow() / 60_000L
                updateTrackingNotification()
                if (elapsed.value % 30_000L < 1000L) checkpoint()
                evaluate(now)
            }
        }
    }

    // ---------------------------------------------------------------- 判定

    private var lastEvalSecond = 0L
    private fun evaluate(now: Long) {
        val sec = now / 1000
        if (sec == lastEvalSecond) return
        lastEvalSecond = sec
        if (triggered || awaitingConfirm.value) return
        if (!cfg.wakeDetection) return
        if (now < keepSleepingUntil) return
        if (!isInteractive()) return

        val awake = awakeMillisNow()

        if (WakeLogic.earlyGiveUp(startAt, now, awake, cfg)) {
            Log.i(TAG, "早期放弃判定命中：${awake / 60000} 分")
            triggered = true
            postConfirm(now, early = true)
            return
        }

        if (WakeLogic.shouldTrigger(startAt, now, awake, cfg)) {
            val end = WakeLogic.backtrackEnd(segments, startAt, cfg)
            Log.i(TAG, "触发醒来检测：now=$now 回溯终点=$end 累计亮屏=${awake / 60000} 分")
            triggered = true
            pendingEndAt.value = end
            if (cfg.askBeforeEnd) postConfirm(end, early = false)
            else scope.launch { finalizeNow("auto", end) }
        }
    }

    private fun postConfirm(endAt: Long, early: Boolean) {
        pendingEndAt.value = endAt
        awaitingConfirm.value = true

        val yes = PendingIntent.getService(
            this, 2,
            Intent(this, SleepTrackingService::class.java).setAction(ACTION_CONFIRM_WAKE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val no = PendingIntent.getService(
            this, 3,
            Intent(this, SleepTrackingService::class.java).setAction(ACTION_KEEP_SLEEPING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val minutes = ((endAt - startAt) / 60_000L).coerceAtLeast(0L)
        val text = if (early)
            "看起来你还醒着，要结束这次记录吗？"
        else
            "要把这段记为 ${hm(minutes)} 吗？结束点会回到你点亮屏幕的那一刻。"

        val n = NotificationCompat.Builder(this, CH_CONFIRM)
            .setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle(getString(R.string.notif_confirm_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "对，我醒了", yes)
            .addAction(0, "我还想再睡", no)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIF_CONFIRM, n)
        }.onFailure { Log.w(TAG, "确认通知发送失败（可能是通知权限被关）: ${it.message}") }

        confirmJob?.cancel()
        confirmJob = scope.launch {
            delay(cfg.confirmWindowMillis)
            if (awaitingConfirm.value) {
                Log.i(TAG, "确认窗口超时 —— 按「继续睡眠」处理，保留自动结束")
                onKeepSleeping()
            }
        }
    }

    /**
     * 用户说「我还想再睡」，或确认窗口超时。
     * 两者都清零累计，但只有前者会关闭本次会话的自动结束（门槛后推 30 分钟）。
     */
    private fun onKeepSleeping() {
        segments.clear()
        screenOnAt = if (isInteractive()) System.currentTimeMillis() else 0L
        triggered = false
        awaitingConfirm.value = false
        keepSleepingUntil = System.currentTimeMillis() + 30 * 60_000L
        NotificationManagerCompat.from(this).cancel(NOTIF_CONFIRM)
        scope.launch { checkpoint() }
        updateTrackingNotification()
    }

    // ---------------------------------------------------------------- 收口

    private suspend fun finalizeNow(by: String, endAt: Long) {
        closeCurrentSegment()
        val awake = awakeMillisNow()
        val safeEnd = endAt.coerceIn(startAt, System.currentTimeMillis())
        runCatching {
            repo.finish(
                id = sessionId,
                endAt = safeEnd,
                awakeMillis = awake,
                wakeCount = segments.size,
                confirmedBy = by,
                segments = segments.toList()
            )
        }.onFailure { Log.e(TAG, "落库失败", it) }

        // 记录变了，刷新桌面小组件
        runCatching { com.nox.sleep.widget.NoxWidgetProvider.refresh(this) }

        Log.i(TAG, "结束记录 session=$sessionId 在床=${(safeEnd - startAt) / 60000}分 清醒=${awake / 60000}分")
        NotificationManagerCompat.from(this).cancel(NOTIF_CONFIRM)
        awaitingConfirm.value = false
        stopForegroundCompat()
        stopSelf()
    }

    private fun closeCurrentSegment() {
        if (screenOnAt > 0L) {
            val end = System.currentTimeMillis()
            if (end - screenOnAt >= 1000L) segments.add(screenOnAt..end)
            screenOnAt = 0L
        }
    }

    private fun awakeMillisNow(): Long {
        var total = 0L
        segments.forEach { total += (it.last - it.first) }
        if (screenOnAt > 0L) total += (System.currentTimeMillis() - screenOnAt)
        return total
    }

    private suspend fun checkpoint() {
        if (sessionId < 0L) return
        runCatching {
            repo.checkpoint(sessionId, awakeMillisNow(), segments.size, segments.toList())
        }
    }

    private fun isInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }

    // ---------------------------------------------------------------- 通知

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CH_TRACKING, getString(R.string.notif_channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_CONFIRM, getString(R.string.notif_channel_confirm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setShowBadge(true)
            }
        )
    }

    private fun startForegroundCompat() {
        val n = buildTrackingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_TRACKING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_TRACKING, n)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(NOTIF_TRACKING)
    }

    private fun buildTrackingNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, SleepTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val mins = elapsed.value / 60_000L
        val awake = awakeMinutes.value
        return NotificationCompat.Builder(this, CH_TRACKING)
            .setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle(getString(R.string.notif_tracking_title))
            .setContentText("已记录 ${hm(mins)} · 累计亮屏 ${awake} 分")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "我醒了", stopPi)
            .build()
    }

    private fun updateTrackingNotification() {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIF_TRACKING, buildTrackingNotification())
        }
    }

    // ---------------------------------------------------------------- 生命周期

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 划掉最近任务不清除记录
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running.value = false
        confirmJob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        super.onDestroy()
    }
}
