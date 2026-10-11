package com.nox.sleep.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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

/* ==================================================================== 样式定义 */

/**
 * 启动动画样式。key 持久化在设置里（settings.splashStyle），
 * 新增样式时不要改动既有 key，避免用户设置失效。
 */
enum class SplashStyle(
    val key: String,
    val label: String,
    val desc: String,
    val durationMillis: Long
) {
    CollectLight("collect", "收光", "光点展开成月牙，再沉入星野", 4200L),
    Moonrise("moonrise", "月出", "月牙从夜色深处缓缓升起", 3600L),
    Constellation("constellation", "星汇", "群星汇聚成月，再散作星野", 3800L),
    Minimal("minimal", "极简", "安静地淡入，最快进入首页", 1900L);

    companion object {
        /** 容错解析：未知 key 回退到默认的「收光」 */
        fun of(key: String?): SplashStyle = entries.firstOrNull { it.key == key } ?: CollectLight
    }
}

private val EASE_OUT_SOFT = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
private val EASE_IN_OUT = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
private val EASE_SHARP_OUT = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** 共享的深空背景 */
private val SPLASH_BG = Brush.radialGradient(
    colors = listOf(Color(0xFF0E1436), Color(0xFF070B1C), Color(0xFF04060E)),
    radius = 1300f
)

/* ==================================================================== 入口 */

/**
 * 启动动画。
 *
 * 四款样式共用同一套品牌元素（月牙 / 星野 / 品牌字），差异只在出场方式：
 *  - 收光：光点 → 月牙 → 星野（默认）
 *  - 月出：星野渐显，月牙从下方升起
 *  - 星汇：群星聚拢成月，再散作星野
 *  - 极简：安静淡入（最快）
 *
 * 动画自行结束进入首页；点击画面可提前跳过。
 * 关闭启动动画时（设置里），由调用方把 autoAdvanceMillis 传成很小的值。
 */
@Composable
fun SplashScreen(
    style: SplashStyle = SplashStyle.CollectLight,
    autoAdvanceMillis: Long = style.durationMillis,
    onFinish: () -> Unit
) {
    val hintAlpha = remember { Animatable(0f) }

    LaunchedEffect(autoAdvanceMillis) {
        delay(autoAdvanceMillis)
        onFinish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(SPLASH_BG)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFinish() },
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            SplashStyle.CollectLight -> CollectLightScene(hintAlpha)
            SplashStyle.Moonrise -> MoonriseScene(hintAlpha)
            SplashStyle.Constellation -> ConstellationScene(hintAlpha)
            SplashStyle.Minimal -> MinimalScene(hintAlpha)
        }

        // 底部提示（各款控制出现时机）
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

/* ==================================================================== 共享组件 */

/** 品牌字组合：Nox + 副标题。各款用自己的时点调用。 */
@Composable
private fun BrandBlock(
    delayMillis: Long,
    subDelayMillis: Long = delayMillis + 650L,
    withSpacing: Boolean = false
) {
    val alpha = remember { Animatable(0f) }
    val lift = remember { Animatable(10f) }
    val spacing = remember { Animatable(if (withSpacing) 0.6f else 0.06f) }
    val sub = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(delayMillis)
        launch { alpha.animateTo(1f, tween(560, easing = EASE_OUT_SOFT)) }
        launch { lift.animateTo(0f, tween(900, easing = EASE_OUT_SOFT)) }
        if (withSpacing) {
            launch { spacing.animateTo(0.06f, tween(900, easing = EASE_OUT_SOFT)) }
        }
        delay((subDelayMillis - delayMillis).coerceAtLeast(0L))
        sub.animateTo(1f, tween(760))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Nox",
            fontSize = 46.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = spacing.value.em,
            color = NoxColor.Ink,
            modifier = Modifier
                .offset(y = lift.value.dp)
                .alpha(alpha.value)
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "记 录 每 一 个 夜 晚",
            fontSize = 12.sp,
            letterSpacing = 0.10.em,
            color = Color(0xFF8E97BE),
            modifier = Modifier.alpha(sub.value)
        )
    }
}

/** 月牙 + 光晕的一个小组件，参数化透明度与缩放、纵向偏移。 */
@Composable
private fun MoonStack(
    moonAlpha: Float,
    moonScale: Float = 1f,
    glowAlpha: Float,
    glowScale: Float = 1f,
    offsetYDp: Float = 0f,
    rotationZ: Float = 0f,
    moonSize: Int = 136
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(y = offsetYDp.dp)
                .size(268.dp)
                .scale(glowScale)
                .alpha(glowAlpha)
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
        Box(
            Modifier
                .offset(y = offsetYDp.dp)
                .scale(moonScale)
                .graphicsLayer { this.rotationZ = rotationZ; this.alpha = moonAlpha }
        ) {
            Crescent(size = moonSize.dp)
        }
    }
}

/* ==================================================================== 收光（默认） */

/**
 * 「收光」——完整版：光点 → 月牙 → 星野 → 品牌字。
 * 整段是「收光」而非「放光」：把用户的眼睛从亮屏刺激里调暗下来。
 */
@Composable
private fun CollectLightScene(hintAlpha: Animatable<Float, AnimationVector1D>) {

    data class StarSpec(
        val angle: Double,
        val radius: Float,
        val delay: Float,
        val duration: Float,
        val size: Float
    )

    val dotAlpha = remember { Animatable(0f) }
    val dotScale = remember { Animatable(0.15f) }
    val moonAlpha = remember { Animatable(0f) }
    val moonScale = remember { Animatable(0.22f) }
    val moonRot = remember { Animatable(-24f) }
    val glowAlpha = remember { Animatable(0f) }
    val glowScale = remember { Animatable(0.35f) }
    val auroraAlpha = remember { Animatable(0f) }
    val starT = remember { Animatable(0f) }
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

    LaunchedEffect(Unit) {
        launch {
            dotAlpha.animateTo(1f, tween(120))
            delay(180)
            dotAlpha.animateTo(0f, tween(500))
        }
        launch { dotScale.animateTo(4.4f, tween(1050, easing = EASE_SHARP_OUT)) }
        launch { delay(420); moonAlpha.animateTo(1f, tween(500)) }
        launch { delay(420); moonRot.animateTo(0f, tween(900, easing = EASE_OUT_SOFT)) }
        launch { delay(420); moonScale.animateTo(1f, tween(900, easing = EASE_OUT_SOFT)) }
        launch {
            delay(780)
            glowAlpha.animateTo(0.92f, tween(600))
            glowAlpha.animateTo(0.5f, tween(600))
        }
        launch {
            delay(780)
            glowScale.animateTo(1.3f, tween(600))
            glowScale.animateTo(1f, tween(600))
        }
        launch { delay(820); auroraAlpha.animateTo(0.95f, tween(800)) }
        launch { delay(820); starT.animateTo(1f, tween(2000)) }
        launch {
            delay(1800)
            while (true) {
                floatLift.animateTo(-9f, tween(3000, easing = EASE_IN_OUT))
                floatLift.animateTo(0f, tween(3000, easing = EASE_IN_OUT))
            }
        }
        launch { delay(2950); hintAlpha.animateTo(1f, tween(760)) }
    }

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

    // 星野（从中心向外扩散）
    Box(Modifier.fillMaxSize()) {
        stars.forEach { s ->
            val p = ((starT.value - s.delay) / s.duration).coerceIn(0f, 1f)
            if (p > 0f) {
                val alpha = sin(p * PI).toFloat() * 0.9f
                val r = s.radius * p
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = (cos(s.angle) * r).dp, y = (sin(s.angle) * r * 0.86f).dp)
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

        // 品牌字（这一款的字母间距会收拢）
        CollectBrandText()
    }
}

@Composable
private fun CollectBrandText() {
    // 收光款的品牌字：带 letterSpacing 收拢 + 上浮，时点与旧版一致
    val alpha = remember { Animatable(0f) }
    val lift = remember { Animatable(10f) }
    val spacing = remember { Animatable(0.6f) }
    val sub = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(1500)
        launch { alpha.animateTo(1f, tween(560, easing = EASE_OUT_SOFT)) }
        launch { lift.animateTo(0f, tween(900, easing = EASE_OUT_SOFT)) }
        launch { spacing.animateTo(0.06f, tween(900, easing = EASE_OUT_SOFT)) }
        delay(750)
        sub.animateTo(1f, tween(760))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Nox",
            fontSize = 46.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = spacing.value.em,
            color = NoxColor.Ink,
            modifier = Modifier
                .offset(y = lift.value.dp)
                .alpha(alpha.value)
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "记 录 每 一 个 夜 晚",
            fontSize = 12.sp,
            letterSpacing = 0.10.em,
            color = Color(0xFF8E97BE),
            modifier = Modifier.alpha(sub.value)
        )
    }
}

/* ==================================================================== 月出 */

/** 「月出」——星野先亮，月牙从夜色深处缓缓升起，光晕随之增强。 */
@Composable
private fun MoonriseScene(hintAlpha: Animatable<Float, AnimationVector1D>) {

    data class FixedStar(val x: Float, val y: Float, val delay: Float, val size: Float)

    val stars = remember {
        val rnd = Random(20261012)
        List(42) {
            FixedStar(
                x = (rnd.nextFloat() - 0.5f) * 340f,
                y = (rnd.nextFloat() - 0.5f) * 560f - 60f,
                delay = rnd.nextFloat() * 0.62f,
                size = 1.1f + rnd.nextFloat() * 2.0f
            )
        }
    }

    val starT = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val floatLift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { starT.animateTo(1f, tween(1600)) }
        launch { delay(350); rise.animateTo(1f, tween(1500, easing = EASE_OUT_SOFT)) }
        launch {
            delay(550)
            glow.animateTo(0.55f, tween(1100))
            glow.animateTo(0.45f, tween(700))
        }
        launch {
            delay(1850)
            while (true) {
                floatLift.animateTo(-9f, tween(3000, easing = EASE_IN_OUT))
                floatLift.animateTo(0f, tween(3000, easing = EASE_IN_OUT))
            }
        }
        launch { delay(2550); hintAlpha.animateTo(1f, tween(750)) }
    }

    // 星野（固定位置，交错淡入）
    Box(Modifier.fillMaxSize()) {
        stars.forEach { s ->
            val a = ((starT.value - s.delay) / 0.38f).coerceIn(0f, 1f)
            if (a > 0f) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = s.x.dp, y = s.y.dp)
                        .size(s.size.dp)
                        .graphicsLayer { this.alpha = a * 0.85f }
                        .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 月牙：从下方 210dp 处升起，升起过程中渐显
        val yOffset = (1f - rise.value) * 210f
        val moonA = (rise.value * 1.7f).coerceAtMost(1f)
        MoonStack(
            moonAlpha = moonA,
            moonScale = 0.92f + 0.08f * rise.value,
            glowAlpha = glow.value,
            glowScale = 0.8f + 0.2f * rise.value,
            offsetYDp = yOffset + floatLift.value
        )

        Spacer(Modifier.height(34.dp))

        BrandBlock(delayMillis = 1700L)
    }
}

/* ==================================================================== 星汇 */

/** 「星汇」——群星从四周聚拢到中心，汇聚成月牙，随即散作星野。 */
@Composable
private fun ConstellationScene(hintAlpha: Animatable<Float, AnimationVector1D>) {

    data class GatherStar(val angle: Double, val r1: Float, val r2: Float, val size: Float)

    val stars = remember {
        val rnd = Random(20261013)
        List(36) {
            GatherStar(
                angle = rnd.nextDouble() * 2 * PI,
                r1 = 70f + rnd.nextFloat() * 170f,
                r2 = 90f + rnd.nextFloat() * 200f,
                size = 1.2f + rnd.nextFloat() * 1.8f
            )
        }
    }

    // T: 0 → 1 聚拢；1 → 2 散开
    val t = remember { Animatable(0f) }
    val moonAlpha = remember { Animatable(0f) }
    val moonScale = remember { Animatable(0.38f) }
    val glow = remember { Animatable(0f) }
    val floatLift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { delay(300); t.animateTo(1f, tween(1250, easing = EASE_IN_OUT)) }
        launch { delay(1450); moonAlpha.animateTo(1f, tween(420)) }
        launch { delay(1450); moonScale.animateTo(1f, tween(760, easing = EASE_OUT_SOFT)) }
        launch {
            delay(1450)
            glow.animateTo(0.88f, tween(500))
            glow.animateTo(0.48f, tween(650))
        }
        launch { delay(1550); t.animateTo(2f, tween(1500, easing = EASE_OUT_SOFT)) }
        launch {
            delay(2200)
            while (true) {
                floatLift.animateTo(-9f, tween(3000, easing = EASE_IN_OUT))
                floatLift.animateTo(0f, tween(3000, easing = EASE_IN_OUT))
            }
        }
        launch { delay(2900); hintAlpha.animateTo(1f, tween(750)) }
    }

    // 星野：两段半径（外→中心，中心→外）
    Box(Modifier.fillMaxSize()) {
        val tv = t.value
        stars.forEach { s ->
            val r = if (tv <= 1f) s.r1 * (1f - tv) else s.r2 * (tv - 1f)
            val alpha = if (tv <= 1f) {
                (0.25f + 0.75f * tv)
            } else {
                (0.5f + 0.4f * (2f - tv))
            }
            if (tv > 0.02f) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = (cos(s.angle) * r).dp, y = (sin(s.angle) * r * 0.86f).dp)
                        .size((s.size * (0.6f + 0.6f * if (tv <= 1f) tv else (2f - tv))).dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MoonStack(
            moonAlpha = moonAlpha.value,
            moonScale = moonScale.value,
            glowAlpha = glow.value,
            glowScale = 0.6f + 0.4f * moonAlpha.value,
            offsetYDp = floatLift.value
        )

        Spacer(Modifier.height(34.dp))

        BrandBlock(delayMillis = 2250L)
    }
}

/* ==================================================================== 极简 */

/** 「极简」——不做星野与光点，月牙安静淡入。最快结束的一款。 */
@Composable
private fun MinimalScene(hintAlpha: Animatable<Float, AnimationVector1D>) {

    val moonAlpha = remember { Animatable(0f) }
    val moonScale = remember { Animatable(0.92f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { moonAlpha.animateTo(1f, tween(650)) }
        launch { moonScale.animateTo(1f, tween(950, easing = EASE_OUT_SOFT)) }
        launch { glow.animateTo(0.42f, tween(950)) }
        launch { delay(950); hintAlpha.animateTo(1f, tween(600)) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MoonStack(
            moonAlpha = moonAlpha.value,
            moonScale = moonScale.value,
            glowAlpha = glow.value,
            glowScale = 0.9f + 0.1f * moonAlpha.value
        )

        Spacer(Modifier.height(34.dp))

        BrandBlock(delayMillis = 240L)
    }
}
