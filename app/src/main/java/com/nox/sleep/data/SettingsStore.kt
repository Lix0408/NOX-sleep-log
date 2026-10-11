package com.nox.sleep.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.noxDataStore: DataStore<Preferences> by preferencesDataStore(name = "nox_settings")

/**
 * 全部可配置项。除亮屏阈值外都藏在默认值后面，用户不配置也不会出错。
 */
data class NoxSettings(
    val goalMinutes: Int = 480,
    val bedtimeReminder: Boolean = false,
    val bedtimeHour: Int = 23,
    val bedtimeMinute: Int = 0,

    // 醒来检测
    val wakeDetection: Boolean = true,
    val thresholdMinutes: Int = 15,
    val askBeforeEnd: Boolean = true,
    val minIntervalMinutes: Int = 60,
    val maxIntervalHours: Int = 16,
    val confirmWindowSeconds: Int = 90,

    // 外观
    val backgroundId: String = "dusk",
    val backgroundBrightness: Int = 22,
    val backgroundBlur: Int = 12,
    val customBackgroundUri: String? = null,
    val autoDim: Boolean = true,
    val playSplash: Boolean = true
) {
    val thresholdMillis: Long get() = thresholdMinutes * 60_000L
    val minIntervalMillis: Long get() = minIntervalMinutes * 60_000L
    val maxIntervalMillis: Long get() = maxIntervalHours * 3_600_000L
    val confirmWindowMillis: Long get() = confirmWindowSeconds * 1000L
    val earlyGiveUpMillis: Long get() = thresholdMillis * 3
}

private object K {
    val GOAL = intPreferencesKey("goal_minutes")
    val BED_REMIND = booleanPreferencesKey("bedtime_reminder")
    val BED_H = intPreferencesKey("bedtime_hour")
    val BED_M = intPreferencesKey("bedtime_minute")

    val WAKE_ON = booleanPreferencesKey("wake_detection")
    val THRESHOLD = intPreferencesKey("threshold_minutes")
    val ASK = booleanPreferencesKey("ask_before_end")
    val MIN_INTERVAL = intPreferencesKey("min_interval_minutes")
    val MAX_INTERVAL = intPreferencesKey("max_interval_hours")
    val CONFIRM_WINDOW = intPreferencesKey("confirm_window_seconds")

    val BG_ID = stringPreferencesKey("background_id")
    val BG_BRIGHT = intPreferencesKey("background_brightness")
    val BG_BLUR = intPreferencesKey("background_blur")
    val BG_URI = stringPreferencesKey("custom_background_uri")
    val AUTO_DIM = booleanPreferencesKey("auto_dim")
    val SPLASH = booleanPreferencesKey("play_splash")
}

class SettingsStore(private val context: Context) {

    val flow: Flow<NoxSettings> = context.noxDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            NoxSettings(
                goalMinutes = p[K.GOAL] ?: 480,
                bedtimeReminder = p[K.BED_REMIND] ?: false,
                bedtimeHour = p[K.BED_H] ?: 23,
                bedtimeMinute = p[K.BED_M] ?: 0,
                wakeDetection = p[K.WAKE_ON] ?: true,
                thresholdMinutes = p[K.THRESHOLD] ?: 15,
                askBeforeEnd = p[K.ASK] ?: true,
                minIntervalMinutes = p[K.MIN_INTERVAL] ?: 60,
                maxIntervalHours = p[K.MAX_INTERVAL] ?: 16,
                confirmWindowSeconds = p[K.CONFIRM_WINDOW] ?: 90,
                backgroundId = p[K.BG_ID] ?: "dusk",
                backgroundBrightness = p[K.BG_BRIGHT] ?: 22,
                backgroundBlur = p[K.BG_BLUR] ?: 12,
                customBackgroundUri = p[K.BG_URI],
                autoDim = p[K.AUTO_DIM] ?: true,
                playSplash = p[K.SPLASH] ?: true
            )
        }

    suspend fun current(): NoxSettings = flow.first()

    suspend fun update(transform: (NoxSettings) -> NoxSettings) {
        val next = transform(current())
        context.noxDataStore.edit { p ->
            p[K.GOAL] = next.goalMinutes
            p[K.BED_REMIND] = next.bedtimeReminder
            p[K.BED_H] = next.bedtimeHour
            p[K.BED_M] = next.bedtimeMinute
            p[K.WAKE_ON] = next.wakeDetection
            p[K.THRESHOLD] = next.thresholdMinutes
            p[K.ASK] = next.askBeforeEnd
            p[K.MIN_INTERVAL] = next.minIntervalMinutes
            p[K.MAX_INTERVAL] = next.maxIntervalHours
            p[K.CONFIRM_WINDOW] = next.confirmWindowSeconds
            p[K.BG_ID] = next.backgroundId
            p[K.BG_BRIGHT] = next.backgroundBrightness
            p[K.BG_BLUR] = next.backgroundBlur
            if (next.customBackgroundUri == null) p.remove(K.BG_URI)
            else p[K.BG_URI] = next.customBackgroundUri
            p[K.AUTO_DIM] = next.autoDim
            p[K.SPLASH] = next.playSplash
        }
    }
}
