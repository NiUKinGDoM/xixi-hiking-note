package com.xixi.hiking.nativeapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark

/**
 * ★★页面级背景流光 —— 1:1 搬 Web 版的 `.aurora-bg`（app-modal.css:1299）
 *
 * 用户原话：「软件的背景你学一下 web 版的，照搬过来，有流动感的那种」。
 *
 * ── Web 到底做了什么 ──────────────────────────────────────────
 *   body 最底层一个 `position:fixed; inset:0; z-index:-1` 的容器，里面三颗 `<i>`：
 *
 *     · ① 左上偏外，蓝 rgba(96,165,250,.30)，70vw，5.5s
 *     · ② 右下偏外，粉 rgba(244,114,182,.26)，76vw，6.0s
 *     · ③ 中上偏外，青 rgba(45,212,191,.22)，54vw，4.5s
 *
 *   每颗都是「径向渐变圆 + 三个关键帧的 translate3d/scale 往返」（0% → 50% → 100%
 *   再原路返回，ease-in-out）。三个周期互不相同（5.5 / 6 / 4.5s），所以永远
 *   对不上拍子 —— 这才是"流动"而不是"呼吸"的关键。周期一样的话三颗会同步放大
 *   缩小，看起来是整块在喘气。
 *
 * ── 为什么不用 Canvas 每帧重绘 ────────────────────────────────
 *   这个 App 的每张玻璃卡都在采样 `bgBackdrop`。如果背景每帧真的重绘一屏，
 *   采样它的 N 层玻璃就要跟着每帧重新采样 —— 帧率会直接掉下去（Web 上等价于
 *   每帧重画三个大渐变，那边的做法是靠合成器扛）。
 *   这里走的是另一条路：**只动 graphicsLayer 的平移/缩放**。位图只生成一次，
 *   之后交给合成器搬 —— 全程零重绘、零重新采样，和 Web 的 transform 动画一个道理。
 *
 * ── 深色 ─────────────────────────────────────────────────────
 *   深色换靛 / 玫 / 青（Web body.dark-mode 同款），亮度压低，避免深底上发白。
 */
@Composable
fun AuroraLayer(modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current

    // 三颗光斑的配色（浅 / 深），alpha 直接取自 Web 的 rgba
    val c1 = if (dark) Color(0xFF6366F1).copy(alpha = 0.26f) else Color(0xFF60A5FA).copy(alpha = 0.30f)
    val c2 = if (dark) Color(0xFFBE185D).copy(alpha = 0.24f) else Color(0xFFF472B6).copy(alpha = 0.26f)
    val c3 = if (dark) Color(0xFF0F766E).copy(alpha = 0.22f) else Color(0xFF2DD4BF).copy(alpha = 0.22f)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        // 三颗各自一条无限往返。用 keyframes 精确落在 0%/50%/100% 三个点上，
        // RepeatMode.Reverse 让它到 100% 之后原路退回（等价 Web 的 alternate）。
        val t = rememberInfiniteTransition(label = "aurora")
        val p1 by t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 4400
                    0.5f at 2200 using LinearEasing
                },
                repeatMode = RepeatMode.Reverse
            ),
            label = "aurora1"
        )
        val p2 by t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 4800
                    0.5f at 2400 using LinearEasing
                },
                repeatMode = RepeatMode.Reverse
            ),
            label = "aurora2"
        )
        val p3 by t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 3600
                    0.5f at 1800 using LinearEasing
                },
                repeatMode = RepeatMode.Reverse
            ),
            label = "aurora3"
        )

        // ★2026-11-04 用户：「整个背景的光球再大一点，流动再快一点点。
        //   我好像只看到下方有，上方的光球呢？」
        //
        //   上方那颗（青）原来 baseY = -20%h、直径 54%w —— 圆心落在屏幕上方 67dp 处，
        //   加上渐变 68% 就开始透明，实际只有极淡一条边露在顶部，几乎看不见。
        //   现在整体直径放大（0.70→0.95 / 0.76→1.00 / 0.54→0.80），
        //   并把上方那颗下移到 -6%h、居中偏左，圆心落在顶栏区域，看得见。
        //   周期同步加快（5.5/6.0/4.5 → 4.4/4.8/3.6s），三者仍互质，不会同步呼吸。

        // ① 蓝 —— 左上（-22vw / -10vh），直径 95vw
        val d1 = w * 0.95f
        Spot(
            color = c1,
            size = d1,
            baseX = w * -0.22f,
            baseY = h * -0.10f,
            dx = { seg(p1, 0f, w.value * 0.12f, w.value * -0.09f).dp },
            dy = { seg(p1, 0f, h.value * -0.08f, h.value * 0.10f).dp },
            scale = { seg(p1, 1f, 1.25f, 1.08f) }
        )
        // ② 粉 —— 右下，直径 100vw
        val d2 = w * 1.00f
        Spot(
            color = c2,
            size = d2,
            baseX = w - d2 + w * 0.18f,
            baseY = h - d2 + h * 0.10f,
            dx = { seg(p2, 0f, w.value * -0.11f, w.value * 0.08f).dp },
            dy = { seg(p2, 0f, h.value * 0.06f, h.value * -0.10f).dp },
            scale = { seg(p2, 1.12f, 0.88f, 1.28f) }
        )
        // ③ 青 —— 中上（16vw / -6vh），直径 80vw（★这颗原来几乎在屏幕外）
        val d3 = w * 0.80f
        Spot(
            color = c3,
            size = d3,
            baseX = w * 0.16f,
            baseY = h * -0.06f,
            dx = { seg(p3, 0f, w.value * -0.10f, w.value * 0.12f).dp },
            dy = { seg(p3, 0f, h.value * -0.09f, h.value * 0.06f).dp },
            scale = { seg(p3, 0.95f, 1.22f, 1.05f) }
        )
    }
}

/**
 * 一颗光斑。
 *
 * 位置 = 基准偏移（[baseX]/[baseY]）+ 动画位移（[dx]/[dy]），三者都在 graphicsLayer 里，
 * 所以整颗的位图只栅格化一次，之后每帧只是合成器改矩阵。
 */
@Composable
private fun Spot(
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    baseX: androidx.compose.ui.unit.Dp,
    baseY: androidx.compose.ui.unit.Dp,
    // ★★2026-11-05 性能：改成**在绘制阶段取动画值**（传 lambda，不传结果）。
    //   原来动画值在组合期被读取 ⇒ 每个动画帧都重组这个 composable、
    //   重建 3 个 radialGradient（60fps = 每秒 60 次无效重组 + 180 次分配）。
    //   现在读发生在 graphicsLayer 的 layer 阶段：只重绘这一层，不触发重组。
    dx: () -> androidx.compose.ui.unit.Dp,
    dy: () -> androidx.compose.ui.unit.Dp,
    scale: () -> Float
) {
    // Brush 只跟颜色有关，缓存住
    val brush = androidx.compose.runtime.remember(color) {
        Brush.radialGradient(
            // Web: radial-gradient(circle, rgba(c,.a) 0%, rgba(c,0) 68%)
            colorStops = arrayOf(0f to color, 0.68f to Color.Transparent)
        )
    }
    Box(
        Modifier
            .offset(x = baseX, y = baseY)
            .size(size)
            .graphicsLayer {
                translationX = dx().toPx()
                translationY = dy().toPx()
                scaleX = scale()
                scaleY = scale()
                // 光斑是纯装饰：不参与命中测试，也不建立新的层叠上下文以外的东西
                alpha = 1f
            }
            .background(brush)
    )
}

/** 0→0.5→1 三点折线插值，端点用 smoothstep 收口（贴近 CSS 的 ease-in-out） */
private fun seg(p: Float, a: Float, b: Float, c: Float): Float {
    val x = p.coerceIn(0f, 1f)
    return if (x <= 0.5f) {
        val k = smooth(x / 0.5f)
        a + (b - a) * k
    } else {
        val k = smooth((x - 0.5f) / 0.5f)
        b + (c - b) * k
    }
}

private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
