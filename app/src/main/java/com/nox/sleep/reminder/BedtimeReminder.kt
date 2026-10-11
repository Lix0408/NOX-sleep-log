package com.nox.sleep.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nox.sleep.MainActivity
import com.nox.sleep.R
import com.nox.sleep.data.NoxSettings
import com.nox.sleep.data.SettingsStore
import com.nox.sleep.domain.nextBedtimeTriggerMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 就寝提醒的调度与投递。
 *
 * 为什么用「非精确」闹钟（setInexactRepeating）而不是精确闹钟：
 *  - 就寝提醒对分钟级精度不敏感，前后几分钟送达都可接受；
 *  - 精确闹钟在 Android 13+ 要用户去系统设置里单独授权（SCHEDULE_EXACT_ALARM），
 *    对一个小功能来说权限成本过重，也违背 NOX「零敏感权限」的约定；
 *  - 睡前场景中设备通常处于活跃状态，此时非精确闹钟基本按点送达。
 *
 * 闹钟不跨重启存活、也会被「强制停止」清除，因此有三重保障：
 *  1. BootReceiver：开机后重排；
 *  2. NoxViewModel 观察设置：App 启动与任何设置变化都会重排；
 *  3. sync() 幂等，任意入口都可以安全重复调用。
 */
object BedtimeReminder {

    private const val TAG = "NoxReminder"
    private const val REQUEST_CODE = 9101
    private const val CHANNEL_ID = "nox_reminder"
    private const val NOTIFICATION_ID = 1003

    /** 按当前设置重排或取消闹钟。幂等。 */
    fun sync(context: Context, settings: NoxSettings) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = alarmIntent(context)
        if (!settings.bedtimeReminder) {
            am.cancel(pi)
            Log.i(TAG, "就寝提醒：已关闭")
            return
        }
        val trigger = nextBedtimeTriggerMillis(settings.bedtimeHour, settings.bedtimeMinute)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger, AlarmManager.INTERVAL_DAY, pi)
        val label = "%02d:%02d".format(settings.bedtimeHour, settings.bedtimeMinute)
        Log.i(TAG, "就寝提醒：已排定每天 $label，首次触发 $trigger")
    }

    /**
     * 发一条就寝提醒通知。
     * 闹钟到点由 [BedtimeReminderReceiver] 调用；调试包里的「预览就寝提醒」也走这里。
     */
    fun notifyNow(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, nm)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = context.getString(R.string.reminder_text)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
            .onFailure { Log.w(TAG, "通知发送失败（可能未授予通知权限）：${it.message}") }
    }

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_reminder),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { setShowBadge(true) }
        )
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BedtimeReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** 闹钟到点：发一条就寝提醒通知。 */
class BedtimeReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("NoxReminder", "就寝提醒触发")
        BedtimeReminder.notifyNow(context)
    }
}

/** 开机后重排（闹钟不跨设备重启存活）。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BedtimeReminder.sync(app, SettingsStore(app).current())
            } catch (t: Throwable) {
                Log.w("NoxReminder", "开机重排失败", t)
            } finally {
                pending.finish()
            }
        }
    }
}
