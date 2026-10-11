package com.nox.sleep

import com.nox.sleep.domain.nextBedtimeTriggerMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 就寝提醒的触发时刻计算测试。
 *
 * 这条规则要在真机上等一整天才能验证完整行为，放在这里用固定时间轴断言，
 * 既是回归保护，也是「下一次提醒是什么时候」的可执行规格。
 */
class ReminderMathTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private val base = LocalDateTime.of(2026, 10, 11, 0, 0)

    private fun at(hour: Int, minute: Int): Long =
        base.withHour(hour).withMinute(minute).atZone(zone).toInstant().toEpochMilli()

    private fun expect(hour: Int, minute: Int, dayOffset: Long = 0): Long =
        base.withHour(hour).withMinute(minute).plusDays(dayOffset)
            .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `目标时刻在今天稍后 —— 取今天`() {
        assertEquals(expect(23, 0), nextBedtimeTriggerMillis(23, 0, at(20, 0), zone))
    }

    @Test
    fun `目标时刻今天已过 —— 取明天`() {
        assertEquals(expect(23, 0, 1), nextBedtimeTriggerMillis(23, 0, at(23, 30), zone))
    }

    @Test
    fun `恰好等于当前时刻 —— 取明天，避免同一时刻重复触发`() {
        assertEquals(expect(23, 0, 1), nextBedtimeTriggerMillis(23, 0, at(23, 0), zone))
    }

    @Test
    fun `跨零点 —— 晚上 23 点看、目标是凌晨 0 点 30，取次日`() {
        assertEquals(expect(0, 30, 1), nextBedtimeTriggerMillis(0, 30, at(23, 0), zone))
    }

    @Test
    fun `跨零点 —— 刚过零点看、目标是 0 点 30，取今天`() {
        assertEquals(expect(0, 30), nextBedtimeTriggerMillis(0, 30, at(0, 10), zone))
    }
}
