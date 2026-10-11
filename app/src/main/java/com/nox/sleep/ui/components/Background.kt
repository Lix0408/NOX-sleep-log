package com.nox.sleep.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nox.sleep.core.NoxColor
import com.nox.sleep.data.NoxSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内置背景。自定义背景是「可选的第二选择」，先用一批已经校准过可读性的内置图打底。
 */
data class NoxBackgroundDef(
    val id: String,
    val name: String,
    val desc: String,
    val brush: Brush
)

private fun grad(vararg colors: Long): Brush =
    Brush.linearGradient(colors.map { Color(it) })

val BuiltInBackgrounds: List<NoxBackgroundDef> = listOf(
    NoxBackgroundDef(
        "dusk", "暮山", "默认 · 破晓暖调",
        Brush.radialGradient(
            colors = listOf(Color(0xFF6B3A55), Color(0xFF3A2A55), Color(0xFF1B2148)),
            radius = 1400f
        )
    ),
    NoxBackgroundDef(
        "sea", "夜海", "冷调 · 低存在感",
        grad(0xFF081226, 0xFF122A45, 0xFF1E4A5C)
    ),
    NoxBackgroundDef(
        "aurora", "极光", "高张力 · 需要降饱和",
        grad(0xFF0B1230, 0xFF1B2A5C, 0xFF2E4A6B)
    ),
    NoxBackgroundDef(
        "forest", "深林", "绿调 · 沉静",
        grad(0xFF08130F, 0xFF123028, 0xFF0E2420)
    ),
    NoxBackgroundDef(
        "haze", "紫霭", "与主色同源",
        grad(0xFF150F28, 0xFF2A1F3D, 0xFF3D2A4A)
    ),
    NoxBackgroundDef(
        "pure", "纯夜", "无图 · 最省电",
        grad(0xFF05070F, 0xFF0C1230, 0xFF131A3E)
    )
)

fun backgroundById(id: String): NoxBackgroundDef =
    BuiltInBackgrounds.firstOrNull { it.id == id } ?: BuiltInBackgrounds.first()

/** 亮度滑块 0–30 → 蒙版不透明度 0.92–0.62 */
private fun maskAlpha(brightness: Int): Float {
    val b = brightness.coerceIn(0, 30) / 30f
    return 0.92f - 0.30f * b
}

/**
 * 加载自定义背景图（降采样 2 倍，失败/无图返回 null）。
 * 全局背景与「背景」设置页的效果预览共用，保证两边渲染一致。
 */
@Composable
fun rememberCustomBackgroundBitmap(uri: String?, enabled: Boolean = true): android.graphics.Bitmap? {
    val ctx = LocalContext.current
    var bitmap by remember(uri, enabled) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(uri, enabled) {
        if (uri.isNullOrBlank() || !enabled) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    BitmapFactory.decodeStream(
                        input, null,
                        BitmapFactory.Options().apply { inSampleSize = 2 }
                    )
                }
            }.getOrNull()
        }
    }
    return bitmap
}

/**
 * 全局背景。
 *
 * 硬约束：睡眠中页、启动动画、确认卡片不套用背景 —— 由调用方决定是否使用本组件。
 */
@Composable
fun NoxBackground(
    settings: NoxSettings,
    modifier: Modifier = Modifier,
    applyCustom: Boolean = true,
    content: @Composable () -> Unit
) {
    val bitmap = rememberCustomBackgroundBitmap(settings.customBackgroundUri, applyCustom)

    Box(modifier.fillMaxSize().background(NoxColor.Void)) {
        val bmp = bitmap
        if (bmp != null && applyCustom) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(settings.backgroundBlur.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(backgroundById(settings.backgroundId).brush))
        }
        // 压暗蒙版 —— 可读性的第一道保证
        Box(
            Modifier
                .fillMaxSize()
                .background(NoxColor.Void.copy(alpha = maskAlpha(settings.backgroundBrightness)))
        )
        // 极光叠色，让所有背景回到同一套色系
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NoxColor.Violet.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        radius = 1100f
                    )
                )
        )
        content()
    }
}
