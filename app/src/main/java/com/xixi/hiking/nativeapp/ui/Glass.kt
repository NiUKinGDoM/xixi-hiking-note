package com.xixi.hiking.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xixi.hiking.nativeapp.ui.liquid.LocalLiquidBackdrop
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassSurface
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import com.xixi.hiking.nativeapp.ui.theme.Radii
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * ★液态玻璃 v2（Liquid Glass）
 *
 * v1 的问题（用户指出「只是模糊而已」）：只有 haze 模糊 + 半透明白 ——
 * 那是 classic glassmorphism（毛玻璃），不是液态玻璃。
 *
 * 液态玻璃的四个物理特征，v2 补齐：
 *   ① 厚度感   —— 边缘「玻璃壁」比中心亮（顶部强高光 + 底部内反光）
 *   ② 色散     —— 边缘极细彩虹带（光穿过玻璃棱边被分解成青/品红/靛）
 *   ③ 折射暗示 —— 内外双描边 + 垂直渐变，制造「光被弯折」的观感
 *   ④ 弹性     —— 按下时轻微缩放 + 阴影收紧（液体的触感）
 *
 * ⚠️ 真正的「背景像素折射」需要 AGSL RuntimeShader（API 33+），见文件末尾注释。
 */

// ★Radii 已迁到 ui/theme/Design.kt（与 Type / Palette 放一起，一套设计语言只在一个文件里）
//   各处引用改成 import com.xixi.hiking.nativeapp.ui.theme.Radii


/**
 * 兼容保留：原来这层是给 Haze 标记「可被采样的内容」。
 * 2026-11-01 起 App 不再使用 Haze（见 liquidGlass 的注释），这里是空实现 ——
 * 保留函数是为了不动各页面的调用点，将来若恢复采样可以直接接回来。
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.glassSource(state: HazeState): Modifier = this


/**
 * 弹窗 / 独立浮层的玻璃底，也是页面内「分组块」用的材质。
 *
 * ★2026-11-02 换 backdrop 库：不再是「渐变 + 描边」画出来的假玻璃，
 *   而是真采样 [LocalLiquidBackdrop]（页面里的纯背景）。
 *
 * 参数取向和浮层相反：
 *   · refract 压到 6dp —— 大平面只需要边缘一点点弯折，弯多了像鱼缸
 *   · veilAlpha 给到 0.7 上下 —— 正文直接压在这块玻璃上，
 *     可以透出背景的颜色变化，但不能透出背后的字
 */
fun Modifier.glassPanel(corner: Int = 20, dark: Boolean? = null): Modifier = composed {
    val isDark = dark ?: LocalIsDark.current
    // ★★★2026-11-03 1:1 搬 Web 版的「框」（用户：「那个透明框若隐若现的特别漂亮」）
    //
    //   Web `.glass-panel` 的真实配方（app.css:1773）：
    //     background: rgba(255,255,255,0.05)      ← 几乎全透，这才叫若隐若现
    //     border:     1px solid rgba(255,255,255,0.11)
    //     border-radius: 20px
    //     box-shadow: 0 4px 24px rgba(0,0,0,0.02)
    //   深色：rgba(15,23,42,0.06) + border rgba(255,255,255,0.04)
    //
    //   之前原生版走的是 liquidGlassSurface（veil 0.34 + 折射），比 Web 实得多，
    //   看起来是"一块半透明白板"而不是"若隐若现的框"。
    //   现在完全按 Web 的数值来：淡白底 + 淡白边，靠**边框和极轻阴影**成形。
    this
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(corner.dp))
        .background(
            if (isDark) Color(0xFF0F172A).copy(alpha = 0.06f)
            else Color.White.copy(alpha = 0.05f)
        )
        .border(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.11f),
            androidx.compose.foundation.shape.RoundedCornerShape(corner.dp)
        )
}


/*
 * ── 如果要「真·背景折射」（AGSL，API 33+）────────────────────────────
 * 效果：玻璃边缘的景物被真实弯折（像凸透镜），而不是现在的高光暗示。
 *
 * val shader = RuntimeShader("""
 *     uniform shader content;
 *     uniform float2 size;
 *     half4 main(float2 xy) {
 *         float2 c = size * 0.5;
 *         float2 d = (xy - c) / (size * 0.5);
 *         float lens = smoothstep(0.85, 1.0, length(d));
 *         float2 off = normalize(d) * lens * 6.0;          // 边缘位移 = 折射
 *         half r = content.eval(xy + off * 1.02).r;        // 色散：三通道偏移不同
 *         half g = content.eval(xy + off).g;
 *         half b = content.eval(xy + off * 0.98).b;
 *         return half4(r, g, b, 1.0);
 *     }
 * """)
 *
 * 前提：① API 33+ ② 需先把「背景」渲染成 texture 喂给 shader（Compose 里要离屏 RenderNode）
 *       ③ 每帧一次全屏 fragment shader，低端机会掉帧
 */
