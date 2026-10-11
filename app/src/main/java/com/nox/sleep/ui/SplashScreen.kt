package com.nox.sleep.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nox.sleep.core.Crescent
import com.nox.sleep.core.NoxColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val EASE_OUT_SOFT = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

private data class StarSpec(
    val angle: Double,
    val radius: Float,
    val delay: Float,
    val duration: Float,
    val size: Float
)

/**
 * 启动动画 —— 3.7 秒。
 *
 * 整段动画是「收光」而非「放光」：从一颗极小的高光点展开成月牙，
 * 再缓缓沉入星野，目的是把用户的眼睛从亮屏刺激里调暗下来。
 *
 * 动画会自行结束进入首页；点击可提前跳过。若用户在设置里关掉了启动动画，
 * 由调用方把 autoAdvanceMillis 传成很小的值。
 */
@Composable
fun SplashScreen(
    autoAdvanceMillis: Long = 4200L,
    onFinish: () -> Unit
) {

    val dotAlpha = remember { Animatable(0f) }
    val dotScale = remember { Animatable(0.15f) }
    val moonAlpha = remember { Animatable(0f) }
    val moonScale = remember { Animatable(0.22f) }
    val moonRot = remember { Animatable(-24f) }
    val moonLift = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }
    val glowScale = remember { Animatable(0.35f) }
    val auroraAlpha = remember { Animatable(0f) }
    val starT = remember { Animatable(0f) }
    val nameAlpha = remember { Animatable(0f) }
    val nameSpacing = remember { Animatable(0.6f) }
    val nameLift = remember { Animatable(10f) }
    val subAlpha = remember { Animatable(0f) }
    val hintAlpha = remember { Animatable(0f) }
    val floatLift = remember { Animatable(0f) }

    val stars = remember {
        val rnd = Random(20261011)
        List(30) {
            StarSpec(
                angle = rnd.nextDouble() * 2 * PI,
                radius = 40f + rnd.nextFloat() * 190f,
                delay = 0.30f + rnd.nextFloat() * 0.22f,
                duration = 0.30f + rnd.nextFloat() * 0.24f,
                size = 1.2f + rnd.nextFloat() * 2.2f
            )
        }
    }

    // 动画走完自动进入首页，不需要用户点一下
    LaunchedEffect(autoAdvanceMillis) {
        delay(autoAdvanceMillis)
        onFinish()
    }

    LaunchedEffect(Unit) {
        // 0.00 – 1.05  光点
        launch {
            dotAlpha.animateTo(1f, tween(120))
            delay(180)
            dotAlpha.animateTo(0f, tween(500))
        }
        launch {
            dotScale.animateTo(4.4f, tween(1050, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
        }
        // 0.42 – 1.32  月牙成形
        delay(420)
        launch { moonAlpha.animateTo(1f, tween(500)) }
        launch { moonRot.animateTo(0f, tween(900, easing = EASE_OUT_SOFT)) }
        launch { moonLift.animateTo(0f, tween(900, easing = EASE_OUT_SOFT)) }
        moonScale.animateTo(1f, tween(900, easing = EASE_OUT_SOFT))
        // 0.78 – 1.98  光晕 + 极光
        delay(360)
        launch {
            glowAlpha.animateTo(0.92f, tween(600))
            glowAlpha.animateTo(0.5f, tween(600))
        }
        launch { glowScale.animateTo(1.3f, tween(600)) }
        launch { glowScale.animateTo(1f, tween(600)) }
        launch { auroraAlpha.animateTo(0.95f, tween(800)) }
        // 0.82 – 2.00  星野
        delay(40)
        launch { starT.animateTo(1f, tween(2000)) }
        // 1.50 – 2.40  品牌字
        delay(680)
        launch { nameAlpha.animateTo(1f, tween(560, easing = EASE_OUT_SOFT)) }
        launch { nameSpacing.animateTo(0.06f, tween(900, easing = EASE_OUT_SOFT)) }
        nameLift.animateTo(0f, tween(900, easing = EASE_OUT_SOFT))
        // 2.25 – 3.00  副标题
        delay(750)
        launch { subAlpha.animateTo(1f, tween(760)) }
        // 2.95 – 3.70  底部提示
        delay(700)
        hintAlpha.animateTo(1f, tween(760))
        // 收尾：月牙进入悬浮循环
        launch {
            while (true) {
                floatLift.animateTo(-9f, tween(3000, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)))
                floatLift.animateTo(0f, tween(3000, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0E1436), Color(0xFF070B1C), Color(0xFF04060E)),
                    radius = 1300f
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFinish() },
        contentAlignment = Alignment.Center
    ) {

        // 极光带
        Box(
            Modifier
                .fillMaxSize()
                .alpha(auroraAlpha.value * 0.5f)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            NoxColor.Cyan.copy(alpha = 0.30f),
                            NoxColor.Violet.copy(alpha = 0.36f),
                            NoxColor.Rose.copy(alpha = 0.26f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 星野
        Box(Modifier.fillMaxSize()) {
            stars.forEach { s ->
                val p = ((starT.value - s.delay) / s.duration).coerceIn(0f, 1f)
                if (p > 0f) {
                    val alpha = sin(p * PI).toFloat() * 0.9f
                    val r = s.radius * p
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = (cos(s.angle) * r).dp,
                                y = (sin(s.angle) * r * 0.86f).dp
                            )
                            .size((s.size * p.coerceAtLeast(0.3f)).dp)
                            .graphicsLayer { this.alpha = alpha }
                            .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // 光晕
                Box(
                    Modifier
                        .size(268.dp)
                        .scale(glowScale.value)
                        .alpha(glowAlpha.value)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    NoxColor.Violet.copy(alpha = 0.75f),
                                    NoxColor.Violet.copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            ),
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
                // 光点
                Box(
                    Modifier
                        .size(10.dp)
                        .scale(dotScale.value)
                        .alpha(dotAlpha.value)
                        .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                )
                // 月牙
                Box(
                    Modifier
                        .offset(y = floatLift.value.dp)
                        .scale(moonScale.value)
                        .graphicsLayer { rotationZ = moonRot.value; this.alpha = moonAlpha.value }
                ) {
                    Crescent(size = 136.dp)
                }
            }

            Spacer(Modifier.height(34.dp))

            Text(
                "Nox",
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = nameSpacing.value.em,
                color = NoxColor.Ink,
                modifier = Modifier
                    .offset(y = nameLift.value.dp)
                    .alpha(nameAlpha.value)
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "记 录 每 一 个 夜 晚",
                fontSize = 12.sp,
                letterSpacing = 0.10.em,
                color = Color(0xFF8E97BE),
                modifier = Modifier.alpha(subAlpha.value)
            )
        }

        Text(
            "睡前点一下 · 醒来点一下",
            fontSize = 11.5.sp,
            letterSpacing = 0.2.em,
            color = Color(0xFF5C6493),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-54).dp)
                .alpha(hintAlpha.value)
        )
    }
}
