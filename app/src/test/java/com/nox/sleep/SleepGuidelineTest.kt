package com.nox.sleep

import com.nox.sleep.core.SleepGuideline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepGuidelineTest {

    @Test
    fun `成人区间边界含端点`() {
        assertTrue(SleepGuideline.inAdultRange(7 * 60L))   // 下限 7h 在区间内
        assertTrue(SleepGuideline.inAdultRange(9 * 60L))   // 上限 9h 在区间内
        assertTrue(SleepGuideline.inAdultRange(8 * 60L))   // 中点
        assertFalse(SleepGuideline.inAdultRange(7 * 60L - 1)) // 6h59m 不在
        assertFalse(SleepGuideline.inAdultRange(9 * 60L + 1)) // 9h01m 不在
    }

    @Test
    fun `对照条位置映射`() {
        // 轴 5h..11h
        assertEquals(0f, SleepGuideline.positionFraction(5 * 60L), 0.001f)
        assertEquals(1f, SleepGuideline.positionFraction(11 * 60L), 0.001f)
        assertEquals(0.5f, SleepGuideline.positionFraction(8 * 60L), 0.001f)
        // 越界钳制
        assertEquals(0f, SleepGuideline.positionFraction(3 * 60L), 0.001f)
        assertEquals(1f, SleepGuideline.positionFraction(13 * 60L), 0.001f)
    }

    @Test
    fun `推荐区间带在轴上的左右边界`() {
        // 7–9h 在 5–11h 轴：起点 1/3、终点 2/3
        assertEquals(1f / 3f, SleepGuideline.bandStartFraction, 0.001f)
        assertEquals(2f / 3f, SleepGuideline.bandEndFraction, 0.001f)
    }
}
