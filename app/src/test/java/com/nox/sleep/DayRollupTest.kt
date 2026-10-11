package com.nox.sleep

import com.nox.sleep.core.dayKeyOf
import com.nox.sleep.domain.inBedMinutesOf
import com.nox.sleep.domain.sleptMinutesOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 按天汇总的两个基准：归属日规则 与 分钟口径。
 * 口径必须与「按天汇总 SQL」（SleepDao.observeDayAggregates）逐字一致，
 * 否则会出现「当日合计 ≠ 页面里各段之和」。
 */
class DayRollupTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ------------------------------------------------ 归属日

    @Test
    fun `跨零点主睡眠归属醒来那天`() {
        // 10-10 23:14 入睡 → 10-11 06:56 醒来：记到 10-11
        assertEquals("2026-10-11", dayKeyOf(at(11, 6, 56), zone))
    }

    @Test
    fun `刚过零点结束仍归属当天`() {
        assertEquals("2026-10-11", dayKeyOf(at(11, 0, 30), zone))
        assertEquals("2026-10-11", dayKeyOf(at(11, 0, 0), zone))
    }

    @Test
    fun `零点前结束归属前一天`() {
        assertEquals("2026-10-10", dayKeyOf(at(10, 23, 59), zone))
    }

    // ------------------------------------------------ 分钟口径

    @Test
    fun `段内分钟先取整再相减`() {
        // 390 分钟在床、10 分钟清醒 → 380
        assertEquals(380L, sleptMinutesOf(at(11, 0, 30), at(11, 7, 0), 10 * 60_000L))
        // 73 秒在床、57 秒清醒 → ⌊73s⌋−⌊57s⌋ = 1−0 = 1（毫秒误差不跨段传递）
        val t0 = at(11, 0, 0)
        assertEquals(1L, sleptMinutesOf(t0, t0 + 73_000L, 57_000L))
    }

    @Test
    fun `清醒超过在床时下限为 0`() {
        // 在床 60 分钟、清醒 90 分钟（脏数据）→ 0，而不是负数
        assertEquals(0L, sleptMinutesOf(at(11, 0, 0), at(11, 1, 0), 90 * 60_000L))
    }

    @Test
    fun `异常负区间不产生负数`() {
        assertEquals(0L, inBedMinutesOf(at(11, 7, 0), at(11, 0, 30)))
        assertEquals(0L, sleptMinutesOf(at(11, 7, 0), at(11, 0, 30), 0L))
    }

    // ------------------------------------------------ 当日合计 = 各段相加

    @Test
    fun `同日三段相加等于当日累计`() {
        // 算例（与设计文档一致）：
        // ① 00:30 → 07:00 在床 390，清醒 10 → 380
        // ② 13:30 → 14:10 在床  40，清醒  0 →  40
        // ③ 20:10 → 20:40 在床  30，清醒  0 →  30
        val segs = listOf(
            Triple(at(11, 0, 30), at(11, 7, 0), 10 * 60_000L),
            Triple(at(11, 13, 30), at(11, 14, 10), 0L),
            Triple(at(11, 20, 10), at(11, 20, 40), 0L)
        )
        val inBed = segs.sumOf { inBedMinutesOf(it.first, it.second) }
        val slept = segs.sumOf { sleptMinutesOf(it.first, it.second, it.third) }
        assertEquals(460L, inBed)   // 在床合计 7h40m
        assertEquals(450L, slept)   // 睡眠合计 7h30m —— 与分段之和严格一致
    }
}
