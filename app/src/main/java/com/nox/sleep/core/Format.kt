package com.nox.sleep.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

fun LocalDateTime.toEpochMillis(): Long =
    atZone(zone).toInstant().toEpochMilli()

fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

/** "7h42m" —— 用于卡片与列表 */
fun hm(minutes: Long): String {
    val m = minutes.coerceAtLeast(0)
    return "${m / 60}h${(m % 60).toString().padStart(2, '0')}m"
}

/** "7" / "42" 拆开，便于大数字排版时把单位做小 */
fun hmParts(minutes: Long): Triple<String, String, Boolean> {
    val m = minutes.coerceAtLeast(0)
    return Triple((m / 60).toString(), (m % 60).toString(), m >= 60)
}

/** "7 小时 42 分" */
fun hmCn(minutes: Long): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val mm = m % 60
    return if (h <= 0) "$mm 分" else if (mm == 0L) "$h 小时" else "$h 小时 $mm 分"
}

/** "23:02" */
fun clock(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

/** "10月11日 周日" */
fun dateCn(epochMillis: Long): String {
    val d = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    return "M月d日".let {
        val weekday = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
        "${d.monthValue}月${d.dayOfMonth}日 $weekday"
    }
}

/** "2026年10月" */
fun monthCn(year: Int, month: Int): String = "${year}年${month}月"

/** "周日" */
fun weekdayCn(epochMillis: Long): String {
    val d = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    return arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
}

/** 计时器 "01:23:45" */
fun hms(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** 睡眠时段按起床日归属 */
fun sleepDayOf(endAt: Long): LocalDate =
    Instant.ofEpochMilli(endAt).atZone(zone).toLocalDate()

/**
 * 归属日 key "yyyy-MM-dd"（= 结束时刻的本地日期，即起床日）。
 *
 * 分段记录结束时写入该值（见 SleepRepository.finish / NoxDatabase 迁移 1→2），
 * 按天汇总以它为分组键。带 zone 参数是为了让归属规则可测试 —— 测试里传入固定时区。
 */
fun dayKeyOf(endAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(endAt).atZone(zone).toLocalDate().toString()

fun greetingFor(hour: Int): String = when {
    hour in 0..4 -> "夜深了"
    hour in 5..10 -> "早上好"
    hour in 11..13 -> "中午好"
    hour in 14..17 -> "下午好"
    hour in 18..22 -> "晚上好"
    else -> "夜深了"
}
