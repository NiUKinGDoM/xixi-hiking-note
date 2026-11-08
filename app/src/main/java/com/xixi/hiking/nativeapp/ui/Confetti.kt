package com.xixi.hiking.nativeapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 彩屑庆祝 —— 对齐 Web 版 `spawnConfetti`。
 *
 * 计划走完、里程碑达成时撒一把，是这套产品里少有的「情绪时刻」。
 * Web 版撒 400 粒，这里只放 140 粒：手机上 400 个独立矩形每帧重绘会掉帧，
 * 140 粒在视觉上一模一样，帧率稳得多。
 *
 * 用法：`ConfettiOverlay(play = true) { play = false }`
 * 覆盖层应该放在 Box 的最上层（`Modifier.matchParentSize()`），它会自己消失。
 */
@Composable
fun ConfettiOverlay(
    play: Boolean,
    onFinished: () -> Unit = {}
) {
    if (!play) return

    var time by remember { mutableFloatStateOf(0f) }

    // 粒子：一次性生成，动画期间不变（避免每帧 new 对象）
    val parts = remember {
        val rnd = Random(System.currentTimeMillis())
        val colors = listOf(
            Color(0xFF667EEA), Color(0xFFF472B6), Color(0xFFFBBF24),
            Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF87171), Color(0xFFA78BFA)
        )
        List(140) {
            Particle(
                x = rnd.nextFloat(),                                  // 0..1 相对宽度
                size = 5f + rnd.nextFloat() * 7f,                     // dp
                aspect = 0.55f + rnd.nextFloat() * 0.25f,
                vy = 0.55f + rnd.nextFloat() * 0.75f,                 // 屏幕高/秒
                sway = 0.03f + rnd.nextFloat() * 0.07f,
                phase = rnd.nextFloat() * 6.28f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,               // 度/秒
                delay = rnd.nextFloat() * 0.5f,
                color = colors[rnd.nextInt(colors.size)]
            )
        }
    }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time = (now - start) / 1_000_000_000f
            if (time > DURATION) { onFinished(); break }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val t = time
        if (t <= 0f) return@Canvas
        parts.forEach { p ->
            val local = t - p.delay
            if (local <= 0f) return@forEach
            // 竖直匀速下落（重力近似为常数，视觉上更好控）
            val y = -0.06f + p.vy * local
            if (y > 1.1f) return@forEach
            // 横向摆动
            val x = p.x + sin(local * 3.1f + p.phase) * p.sway
            val px = x * size.width
            val py = y * size.height
            val w = p.size.dp.toPx()
            val hgt = w * p.aspect
            // 越接近尾声越淡，避免「啪」地一下消失
            val alpha = if (t > DURATION_PAUSE) {
                ((DURATION - t) / (DURATION - DURATION_PAUSE)).coerceIn(0f, 1f)
            } else 1f
            rotate(degrees = p.spin * local, pivot = Offset(px, py)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(px - w / 2f, py - hgt / 2f),
                    size = Size(w, hgt)
                )
            }
        }
    }
}

private const val DURATION = 2.6f          // 总时长（秒）
private const val DURATION_PAUSE = 1.9f     // 从这一刻开始淡出

private data class Particle(
    val x: Float,
    val size: Float,
    val aspect: Float,
    val vy: Float,
    val sway: Float,
    val phase: Float,
    val spin: Float,
    val delay: Float,
    val color: Color
)
