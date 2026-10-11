package com.nox.sleep.domain

/**
 * 按天分段汇总的分钟口径。
 *
 * 与详情页「四数口径」（metricsOf）、按天汇总 SQL（SleepDao.observeDayAggregates）
 * 三处共用同一公式，任何一处改动必须同步 —— 否则会出现
 * 「当日合计 ≠ 页面里各段之和」这类对不上的数字。
 *
 * 取整规则：先各自取整，再相减（而不是先相减再取整）。
 * 这样 6h20m30s 的一段固定显示为 6h20m，多段相加后「合计 == 各段之和」严格成立。
 */

/** 段内「在床」分钟 = ⌊(end − start) / 60s⌋，异常负值夹到 0。 */
fun inBedMinutesOf(startAt: Long, endAt: Long): Long =
    ((endAt - startAt) / 60_000L).coerceAtLeast(0L)

/** 段内「睡眠」分钟 = ⌊在床⌋ − ⌊清醒⌋，下限 0（脏数据下也不会为负）。 */
fun sleptMinutesOf(startAt: Long, endAt: Long, awakeMillis: Long): Long =
    (inBedMinutesOf(startAt, endAt) - (awakeMillis / 60_000L)).coerceAtLeast(0L)
