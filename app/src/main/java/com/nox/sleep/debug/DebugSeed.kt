package com.nox.sleep.debug

import com.nox.sleep.core.dayKeyOf
import com.nox.sleep.core.toEpochMillis
import com.nox.sleep.data.SessionKind
import com.nox.sleep.data.SleepRepository
import com.nox.sleep.data.SleepSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Random

/**
 * 仅调试包使用的示例数据生成器。
 *
 * 为什么需要它：「按天汇总」要看到「同日多段相加 / 跨零点归属 / 午睡并入」
 * 这些效果，靠真实记录得攒一个月。这里一次性写入 30 天的确定性样例，
 * 走与真实记录完全相同的字段与口径（dayKey 在写入时计算）。
 *
 * 「清除示例数据」按 confirmedBy = "sample" 精确删除，不会碰任何真实记录。
 */
object DebugSeed {

    suspend fun seed(repo: SleepRepository) {
        repo.clearSamples() // 幂等：重复点击不会叠加
        val rnd = Random(20261011L)
        val today = LocalDate.now()

        for (back in 0..29) {
            val day = today.minusDays(back.toLong())
            val prev = day.minusDays(1)

            // 主睡眠：前一天 23:00~23:30 入睡 → 当天 06:30~07:30 醒来（跨零点，归属当天）
            insert(
                repo,
                startDay = prev, startMinOfDay = 23 * 60 + rnd.nextInt(4) * 10,
                endDay = day, endMinOfDay = 6 * 60 + 30 + rnd.nextInt(7) * 10,
                wakes = rnd.nextInt(3), rnd = rnd
            )

            // 午睡：每 3 天一次；「两天前」再加一段傍晚小睡，凑出一个 3 段的样例日
            if (back % 3 == 0 || back == 2) {
                insert(
                    repo, day, 13 * 60 + 20, day, 13 * 60 + 50 + rnd.nextInt(3) * 5,
                    wakes = 0, rnd = rnd, kind = SessionKind.Nap.key
                )
            }
            if (back == 2 || back == 7 || back == 15) {
                insert(
                    repo, day, 20 * 60 + 40, day, 21 * 60 + 10,
                    wakes = 0, rnd = rnd, kind = SessionKind.Nap.key
                )
            }

            // 今天再补一段（若当前时间已过），让「今日累计」直接呈现两段相加
            if (back == 0 && System.currentTimeMillis() > millisOf(day, 14 * 60 + 5)) {
                insert(
                    repo, day, 13 * 60 + 30, day, 14 * 60 + 5,
                    wakes = 0, rnd = rnd, kind = SessionKind.Nap.key
                )
            }
        }
    }

    private fun millisOf(day: LocalDate, minOfDay: Int): Long =
        LocalDateTime.of(day, LocalTime.of(minOfDay / 60, minOfDay % 60)).toEpochMillis()

    private suspend fun insert(
        repo: SleepRepository,
        startDay: LocalDate,
        startMinOfDay: Int,
        endDay: LocalDate,
        endMinOfDay: Int,
        wakes: Int,
        rnd: Random,
        kind: String = SessionKind.Sleep.key
    ) {
        val startAt = millisOf(startDay, startMinOfDay)
        val endAt = millisOf(endDay, endMinOfDay)

        // 与 awakeMillis 自洽的清醒片段：第 i 次醒来放在时长的中后段，各 3~12 分钟
        val span = endAt - startAt
        val segs = (0 until wakes).map { i ->
            val s = startAt + (span * (0.35 + 0.25 * i)).toLong()
            val e = (s + (3 + rnd.nextInt(10)) * 60_000L).coerceAtMost(endAt - 60_000L)
            s..e
        }.filter { it.last > it.first }
        val awakeMillis = segs.sumOf { it.last - it.first }

        repo.insertSample(
            SleepSession(
                startAt = startAt,
                endAt = endAt,
                dayKey = dayKeyOf(endAt),
                kind = kind,
                awakeMillis = awakeMillis,
                wakeCount = segs.size,
                confirmedBy = "sample",
                integrity = 1,
                awakeSegments = SleepSession.encodeSegments(segs),
                createdAt = endAt
            )
        )
    }
}
