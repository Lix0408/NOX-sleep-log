package com.nox.sleep

import com.nox.sleep.data.NoxSettings
import com.nox.sleep.domain.WakeLogic
import com.nox.sleep.domain.metricsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 醒来检测的判定逻辑测试。
 *
 * 这几条规则在真机上要等一整晚才能验，放在这里用固定时间轴断言，
 * 既是回归保护，也是这套机制的可执行规格。
 */
class WakeLogicTest {

    private val min = 60_000L

    private fun cfg(
        threshold: Int = 15,
        minInterval: Int = 60,
        on: Boolean = true
    ) = NoxSettings(
        wakeDetection = on,
        thresholdMinutes = threshold,
        minIntervalMinutes = minInterval
    )

    // ---------------------------------------------------------------- 触发条件

    @Test
    fun `未满最短间隔 —— 即使亮屏很久也不触发`() {
        val c = cfg(minInterval = 60)
        val start = 0L
        val now = 30 * min          // 才过 30 分钟
        val awake = 25 * min        // 已累计 25 分钟亮屏
        assertFalse(WakeLogic.shouldTrigger(start, now, awake, c))
    }

    @Test
    fun `累计亮屏未到阈值 —— 不触发`() {
        val c = cfg(threshold = 15, minInterval = 60)
        val start = 0L
        val now = 8 * 60 * min      // 已过 8 小时
        val awake = 14 * min        // 只累计 14 分钟
        assertFalse(WakeLogic.shouldTrigger(start, now, awake, c))
    }

    @Test
    fun `两条都满足 —— 触发`() {
        val c = cfg(threshold = 15, minInterval = 60)
        val start = 0L
        val now = 8 * 60 * min
        val awake = 15 * min
        assertTrue(WakeLogic.shouldTrigger(start, now, awake, c))
    }

    @Test
    fun `醒来自动结束被关掉时永不触发`() {
        val c = cfg(on = false)
        assertFalse(WakeLogic.shouldTrigger(0L, 10 * 60 * min, 10 * 60 * min, c))
    }

    // ------------------------------------------------------------------ 回溯

    @Test
    fun `回溯到让累计跨过阈值的那一段起点`() {
        val c = cfg(threshold = 15, minInterval = 60)
        val segs = listOf(
            120 * min..125 * min,     // 起夜 5 分钟
            400 * min..410 * min,     // 起夜 10 分钟
            480 * min..500 * min      // 醒来后用手机 20 分钟 → 单独就超过 15 分钟
        )
        // 最后一段自己就够，所以终点应回溯到它的起点 480
        assertEquals(480 * min, WakeLogic.backtrackEnd(segs, 0L, c))
    }

    @Test
    fun `最后一段不够时要往前合并`() {
        val c = cfg(threshold = 15, minInterval = 60)
        val segs = listOf(
            100 * min..110 * min,     // 10 分钟
            480 * min..486 * min      // 6 分钟 —— 自己不够
        )
        // 6 + 10 = 16 ≥ 15，跨过阈值的那一段是 100 分钟那段
        assertEquals(100 * min, WakeLogic.backtrackEnd(segs, 0L, c))
    }

    @Test
    fun `回溯不得早于最短间隔`() {
        val c = cfg(threshold = 15, minInterval = 60)
        val segs = listOf(
            10 * min..30 * min,       // 20 分钟，但发生在第 10 分钟，早于 H_min
            50 * min..60 * min        // 10 分钟
        )
        // 算出来会落到第 10 分钟，但下限被抬到 start + 60 分钟
        assertEquals(60 * min, WakeLogic.backtrackEnd(segs, 0L, c))
    }

    @Test
    fun `没有亮屏片段时回溯到起点`() {
        assertEquals(0L, WakeLogic.backtrackEnd(emptyList(), 0L, cfg()))
    }

    // -------------------------------------------------------------- 早期放弃

    @Test
    fun `前最短间隔内狂刷手机 —— 判定为根本没打算睡`() {
        val c = cfg(threshold = 15, minInterval = 60)
        val start = 0L
        val now = 50 * min          // 还没到 60 分钟
        val awake = 45 * min        // 已经是 3 × 阈值
        assertTrue(WakeLogic.earlyGiveUp(start, now, awake, c))
    }

    @Test
    fun `过了最短间隔就不再走早期放弃这条路`() {
        val c = cfg(threshold = 15, minInterval = 60)
        assertFalse(WakeLogic.earlyGiveUp(0L, 70 * min, 50 * min, c))
    }

    @Test
    fun `正常入睡不会被早期放弃误判`() {
        val c = cfg(threshold = 15, minInterval = 60)
        assertFalse(WakeLogic.earlyGiveUp(0L, 50 * min, 3 * min, c))
    }

    // ------------------------------------------------------------ 四数口径

    @Test
    fun `四数口径与设计稿的示范数据一致`() {
        val start = 0L
        val end = 7 * 3_600_000L + 54 * min      // 在床 7h54m
        val awake = 12 * min                     // 清醒 12 分钟
        val m = metricsOf(start, end, awake, goalMinutes = 480)

        assertEquals(474L, m.inBedMinutes)
        assertEquals(12L, m.awakeMinutes)
        assertEquals(462L, m.sleepMinutes)       // 7h42m
        assertEquals(97, m.efficiency)
        assertEquals(96, m.goalPercent)
    }

    @Test
    fun `清醒时长不会超过在床时长`() {
        val m = metricsOf(0L, 10 * min, 30 * min, 480)
        assertEquals(10L, m.inBedMinutes)
        assertEquals(10L, m.awakeMinutes)
        assertEquals(0L, m.sleepMinutes)
    }
}
