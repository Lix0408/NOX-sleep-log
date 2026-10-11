package com.nox.sleep.domain

import java.time.Instant
import java.time.ZoneId

/**
 * 就寝提醒的下一次触发时刻。
 *
 * 纯函数，不碰 Android API，便于单测（见 ReminderMathTest）。
 *
 * 规则：取「今天的 hour:minute」；若该时刻已过或恰为当前时刻，则取明天。
 *   - 现在 20:00、目标 23:00 → 今天 23:00
 *   - 现在 23:30、目标 23:00 → 明天 23:00
 *   - 现在 23:00、目标 00:30 → 明天 00:30（跨零点）
 *   - 现在 00:10、目标 00:30 → 今天 00:30（20 分钟后）
 */
fun nextBedtimeTriggerMillis(
    hour: Int,
    minute: Int,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val nowZ = Instant.ofEpochMilli(now).atZone(zone)
    var target = nowZ.toLocalDate().atTime(hour, minute)
    if (!target.isAfter(nowZ.toLocalDateTime())) {
        target = target.plusDays(1)
    }
    return target.atZone(zone).toInstant().toEpochMilli()
}
