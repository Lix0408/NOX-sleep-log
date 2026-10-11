package com.nox.sleep

import com.nox.sleep.ui.SplashStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动动画样式枚举的容错测试。
 *
 * key 会持久化到用户设置里，解析必须永远有兜底：
 * 旧版本残留 key、脏数据、null 都不能让启动流程崩溃。
 */
class SplashStyleTest {

    @Test
    fun `未知或空的 key 回退到默认的收光`() {
        assertEquals(SplashStyle.CollectLight, SplashStyle.of(null))
        assertEquals(SplashStyle.CollectLight, SplashStyle.of(""))
        assertEquals(SplashStyle.CollectLight, SplashStyle.of("not-a-style"))
    }

    @Test
    fun `每个样式的 key 都能往返解析`() {
        SplashStyle.entries.forEach { s ->
            assertEquals(s, SplashStyle.of(s.key))
        }
    }

    @Test
    fun `key 唯一、时长为正、文案非空`() {
        val keys = SplashStyle.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        SplashStyle.entries.forEach { s ->
            assertTrue(s.durationMillis > 0)
            assertTrue(s.label.isNotBlank() && s.desc.isNotBlank())
        }
    }
}
