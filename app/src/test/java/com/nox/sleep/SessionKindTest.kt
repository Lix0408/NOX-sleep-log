package com.nox.sleep

import com.nox.sleep.data.SessionKind
import com.nox.sleep.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记录类型（睡眠 / 小憩）的解析与判定。
 * 容错要求：历史数据迁移回填为 sleep，解析永不抛错、未知值一律回退睡眠。
 */
class SessionKindTest {

    @Test
    fun `两个 key 都能正确解析`() {
        assertEquals(SessionKind.Sleep, SessionKind.of("sleep"))
        assertEquals(SessionKind.Nap, SessionKind.of("nap"))
    }

    @Test
    fun `未知或空值回退为睡眠`() {
        assertEquals(SessionKind.Sleep, SessionKind.of(null))
        assertEquals(SessionKind.Sleep, SessionKind.of(""))
        assertEquals(SessionKind.Sleep, SessionKind.of("unknown"))
    }

    @Test
    fun `isNap 只对 nap 为真`() {
        assertTrue(SleepSession(startAt = 0L, endAt = 1L, kind = "nap").isNap)
        assertFalse(SleepSession(startAt = 0L, endAt = 1L, kind = "sleep").isNap)
        assertFalse(SleepSession(startAt = 0L, endAt = 1L).isNap)
    }
}
