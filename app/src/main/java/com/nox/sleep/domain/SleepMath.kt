package com.nox.sleep.domain

import com.nox.sleep.data.NoxSettings
import kotlin.math.roundToInt

/**
 * 睡眠时长的四数口径。设计稿 06 节：
 *
 *   在床时长 = T_end − T_start
 *   清醒时长 = 区间内屏幕处于交互态的总时长
 *   睡眠时长 = 在床 − 清醒
 *   睡眠效率 = 睡眠 ÷ 在床
 *
 * 拆成四个数是为了避免「总时长减亮屏」那种一句话公式的口径不一致，
 * 同时正好补全详情页原有的「卧床时长 / 睡眠效率」两个字段。
 */
data class SleepMetrics(
    val inBedMinutes: Long,
    val awakeMinutes: Long,
    val sleepMinutes: Long,
    val efficiency: Int,
    val goalPercent: Int
)

fun metricsOf(startAt: Long, endAt: Long, awakeMillis: Long, goalMinutes: Int): SleepMetrics {
    // 与 domain/DayRollup.kt 共用同一分钟换算，避免两处口径漂移
    val inBed = inBedMinutesOf(startAt, endAt)
    val sleep = sleptMinutesOf(startAt, endAt, awakeMillis)
    val awake = inBed - sleep
    val eff = if (inBed <= 0L) 0 else ((sleep * 100.0) / inBed).roundToInt()
    val goal = if (goalMinutes <= 0) 0 else ((sleep * 100.0) / goalMinutes).roundToInt()
    return SleepMetrics(inBed, awake, sleep, eff, goal)
}

/**
 * 醒来检测的全部判定逻辑。纯函数，不碰 Android API，便于单测。
 */
object WakeLogic {

    /** 四条同时满足才触发：等状态由调用方保证。 */
    fun shouldTrigger(
        startAt: Long,
        now: Long,
        accumulatedAwakeMillis: Long,
        cfg: NoxSettings
    ): Boolean {
        if (!cfg.wakeDetection) return false
        if (now - startAt < cfg.minIntervalMillis) return false
        if (accumulatedAwakeMillis < cfg.thresholdMillis) return false
        return true
    }

    /** 早期放弃：前 H_min 内累计亮屏就超过 3×TH，说明根本没打算睡。 */
    fun earlyGiveUp(
        startAt: Long,
        now: Long,
        accumulatedAwakeMillis: Long,
        cfg: NoxSettings
    ): Boolean {
        if (!cfg.wakeDetection) return false
        if (now - startAt >= cfg.minIntervalMillis) return false
        return accumulatedAwakeMillis >= cfg.earlyGiveUpMillis
    }

    /**
     * 回溯 T_end：从后往前累加时间片，找到让累计跨过阈值的那一段，取其起点。
     *
     * 为什么必须回溯：用户 06:56 拿起手机、06:59 累计达标触发。
     * 若把 T_end 记成触发时刻，就凭空多算了 3 分钟睡眠。
     *
     * 下限保护：T_end 不得早于 startAt + H_min，避免起夜那一段被当作终点。
     */
    fun backtrackEnd(
        segments: List<LongRange>,
        startAt: Long,
        cfg: NoxSettings
    ): Long {
        if (segments.isEmpty()) return startAt
        var acc = 0L
        var segStart = startAt
        for (i in segments.indices.reversed()) {
            val s = segments[i]
            acc += (s.last - s.first)
            if (acc >= cfg.thresholdMillis) {
                segStart = s.first
                break
            }
        }
        val floor = startAt + cfg.minIntervalMillis
        val ceil = segments.last().last
        return segStart.coerceIn(floor.coerceAtMost(ceil), ceil)
    }
}
