package com.nox.sleep.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nox.sleep.MainActivity
import com.nox.sleep.R
import com.nox.sleep.core.clock
import com.nox.sleep.core.hmParts
import com.nox.sleep.data.NoxDatabase
import com.nox.sleep.data.SettingsStore
import com.nox.sleep.data.SleepRepository
import com.nox.sleep.data.SleepSession
import com.nox.sleep.domain.metricsOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 桌面小组件。
 *
 * 2×2 给「想看一眼昨晚」，4×1 给「想看一眼连续性」。
 * 图标本体与 App 主图标同源，点击直接进入 App。
 *
 * 两种尺寸必须是两个独立的 BroadcastReceiver（清单里同名组件只能声明一次），
 * 因此把逻辑放在这个基类里，由下面两个极薄的子类分别承载。
 */
open class NoxWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = NoxDatabase.get(context)
                val repo = SleepRepository(db.dao(), SettingsStore(context))
                val last = repo.latest()
                val settings = repo.settingsNow()
                val streak = streakOf(repo.all.first())

                appWidgetIds.forEach { id ->
                    val info = appWidgetManager.getAppWidgetInfo(id)
                    val wide = info?.initialLayout == R.layout.widget_nox_wide
                    appWidgetManager.updateAppWidget(
                        id,
                        render(context, last, settings.goalMinutes, streak, wide)
                    )
                }
            } catch (_: Throwable) {
                // 小组件不应因数据异常而崩
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(
        context: Context,
        last: SleepSession?,
        goalMinutes: Int,
        streak: Int,
        wide: Boolean
    ): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            if (wide) R.layout.widget_nox_wide else R.layout.widget_nox_small
        )

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)

        if (last?.endAt == null) {
            if (wide) {
                views.setTextViewText(R.id.widget_value, "还没有记录")
                views.setTextViewText(R.id.widget_sub, "点一下开始入睡")
                views.setTextViewText(R.id.widget_streak, "—")
            } else {
                views.setTextViewText(R.id.widget_label, "NOX")
                views.setTextViewText(R.id.widget_value, "—")
                views.setTextViewText(R.id.widget_sub, "还没有记录")
            }
            return views
        }

        val endAt = last.endAt
        val m = metricsOf(last.startAt, endAt, last.awakeMillis, goalMinutes)
        val (h, mm, _) = hmParts(m.sleepMinutes)
        val text = "${h}h${mm}m"

        if (wide) {
            views.setTextViewText(R.id.widget_value, "昨夜 $text")
            views.setTextViewText(R.id.widget_sub, "达成 ${m.goalPercent}%")
            views.setTextViewText(
                R.id.widget_streak,
                if (streak > 0) "${streak}天" else "—"
            )
        } else {
            views.setTextViewText(R.id.widget_label, "昨夜")
            views.setTextViewText(R.id.widget_value, text)
            views.setTextViewText(
                R.id.widget_sub,
                "${clock(last.startAt)} → ${clock(endAt)} · 达成 ${m.goalPercent}%"
            )
        }
        return views
    }

    private fun streakOf(sessions: List<SleepSession>): Int {
        if (sessions.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val days = sessions.mapNotNull { s ->
            s.endAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        }.distinct().sortedDescending()
        if (days.isEmpty()) return 0
        var cursor = LocalDate.now()
        val first = days.first()
        if (first != cursor && first != cursor.minusDays(1)) return 0
        if (first == cursor.minusDays(1)) cursor = cursor.minusDays(1)
        var count = 0
        for (d in days) {
            if (d == cursor) {
                count++
                cursor = cursor.minusDays(1)
            } else if (d.isBefore(cursor)) break
        }
        return count
    }

    companion object {
        /** 记录落库后主动刷新所有小组件 */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            listOf(
                NoxWidgetSmallProvider::class.java,
                NoxWidgetWideProvider::class.java
            ).forEach { cls ->
                val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
                if (ids.isEmpty()) return@forEach
                val i = Intent(context, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(i)
            }
        }
    }
}

/** 2×2 · 昨夜睡眠 */
class NoxWidgetSmallProvider : NoxWidgetProvider()

/** 4×1 · 连续记录 */
class NoxWidgetWideProvider : NoxWidgetProvider()
