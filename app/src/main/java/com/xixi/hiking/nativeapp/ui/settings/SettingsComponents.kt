package com.xixi.hiking.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.safeClickable
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark

/**
 * ★★★设置页组件 —— **严格按 Web 版 (hiking-app3/www/index.html + app.css) 1:1 复刻**
 *
 * 用户要求原话：「所有都对照，1:1 复刻，设置页……我要和他一模一样的，
 * 本来你也是对于他的升级版，你先改的和他一样，再升级吧」。
 *
 * ── 从 app.css 抄下来的规格（别再自己发挥）──────────────────
 * `.settings-group`       bg  white/0.10（深 0.05）
 *                         border 1px rgba(100,116,139,0.06)（深 rgba(255,255,255,0.08)）
 *                         radius 20   padding 16   margin-bottom 16
 * `.settings-group-title` gap 8   15sp/700   margin-bottom 14   图标 20 靛蓝
 * `.settings-item`        bg white*(alpha*0.6)   border rgba(15,23,42,0.08)
 *                         radius 16   padding 14/16   gap 12
 *                         行间距 10（.grid 之后是 12）
 * `.settings-item-icon`   38×38   radius 12
 *                         gradient(135°, rgba(99,122,235,0.18), rgba(128,84,199,0.18))
 * `.settings-item-title`  15sp/700   `.settings-item-desc` 12sp 次要，margin-top 2
 * `.settings-switch`      48×28，圆钮 22，未选 rgba(148,163,184,0.4)，
 *                         选中 white/0.15 + 内描边靛蓝 0.35
 *
 * ★唯一有意偏离：**文字颜色**保持全站的黑白（用户之前明确要求「只要白字和黑字」），
 *   其余（结构、尺寸、间距、圆角、图标底色）全部照抄 Web 版。
 */

/** Web 版 `--c-indigo` 系：图标与描边用，是页面上唯一的彩色 */
private val WebIndigo = Color(0xFF4F46E5)
private val WebIndigoLight = Color(0xFFA5B4FC)

/** Web 版强调色（浅色靛蓝 / 深色亮靛）—— 设置页的图标与开关描边用它 */
@Composable
fun indigoColor(): Color = if (LocalIsDark.current) WebIndigoLight else WebIndigo

/** 分组卡片：`.settings-group` */
@Composable
fun SettingsGroup(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    bottomMargin: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val dark = LocalIsDark.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = if (bottomMargin) 16.dp else 0.dp)
            // ★★★2026-11-02 修闪退：原来这里是 clip + background + border 三条 —
            //   `Modifier.clip` 会创建**离屏层**，而设置页有 4 个分组、每个分组里又套着
            //   玻璃按钮（GridGlassButton 的 drawBackdrop 又是一层离屏）。
            //   设置页因此成了全 App 离屏层最密的页面，真机 GPU 直接扛不住 → 点开就崩。
            //   改成 drawBehind 自己画圆角矩形 + 描边：**零离屏层**，视觉完全一致。
            .drawBehind {
                val r = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx())
                drawRoundRect(
                    color = if (dark) Color.White.copy(alpha = 0.05f)
                    else Color.White.copy(alpha = 0.10f),
                    cornerRadius = r
                )
                drawRoundRect(
                    // ★描边再淡一档（0.06→0.03）：分组靠底色区分就够，不必每条都画线
                    color = if (dark) Color.White.copy(alpha = 0.04f)
                    else Color(0xFF64748B).copy(alpha = 0.03f),
                    cornerRadius = r,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
            .padding(16.dp)
    ) {
        // 分组标题
        // ★2026-11-03 文字层级统一：分组标题原来是 15sp/Bold，和行标题**一样大**，
        //   于是整页读起来没有层次（都是大字加粗）。改成 13sp/700 + 字距，
        //   行标题 14sp/600 —— 页面 24 > 行 14 > 分组 13 > 说明 12。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = indigoColor(), modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
            )
            if (trailing != null) {
                Spacer(Modifier.width(2.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** 设置行：`.settings-item` */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    topGap: Int = 10,
    /** 标题后的小灰字（Web「照片占用（点开看详情）」那枚），不需要就留空 */
    titleSuffix: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val dark = LocalIsDark.current
    val base = modifier
        .fillMaxWidth()
        .padding(top = topGap.dp)
        // 同 SettingsGroup：drawBehind 画圆角底 + 描边，不建离屏层
        .drawBehind {
            val r = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx())
            drawRoundRect(
                color = if (dark) Color.White.copy(alpha = 0.04f)
                else Color.White.copy(alpha = 0.30f),
                cornerRadius = r
            )
            drawRoundRect(
                color = if (dark) Color(0xFF94B4FF).copy(alpha = 0.06f)
                else Color(0xFF0F172A).copy(alpha = 0.035f),
                cornerRadius = r,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }
    Row(
        modifier = (if (onClick != null) base.safeClickable(onClick = onClick) else base)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标方块：38×38 / radius 12 / 靛紫渐变
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            WebIndigo.copy(alpha = if (dark) 0.28f else 0.18f),
                            Color(0xFF8054C7).copy(alpha = if (dark) 0.28f else 0.18f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = indigoColor(), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    // 行标题：14/600（原来是 15/Bold，和分组标题撞在一起）
                    title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!titleSuffix.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        // Web: margin-left:4px; font-size:10px; font-weight:400; color:#64748b
                        titleSuffix, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
            if (desc.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    // 说明文字：12 / 行高 17 / 略降透明度 —— 三行以内都能读舒服
                    desc, fontSize = 12.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * 液态玻璃开关：`.settings-switch`（48×28，钮 22）
 *
 * 未选中：轨道 rgba(148,163,184,0.4)；选中：white/0.15 + 内描边靛蓝
 * 用 Canvas 自己画，不引 Material3 Switch（那玩意尺寸对不上 Web 版）。
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    tag: String? = null,
    /** 置灰不可点：用于「跟随时由系统决定」这类被上级开关接管的场景 */
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    val dark = LocalIsDark.current

    // ★★2026-11-05 加动画（用户：「开关按钮加点小动画呗，现在好生硬」）
    //
    //   原来的问题：`if (checked) 23.dp else 3.dp` + 硬切颜色 —— 圆钮是**瞬移**的，
    //   轨道颜色也是瞬间跳变，点起来像按了个复选框，没有"拨一下"的手感。
    //
    //   现在三件事：
    //     ① 位移走 spring（阻尼 0.62 / 刚度 520）：到位时轻微过冲一点再收住，
    //        就是真开关那种"咔哒"感，而不是线性平移
    //     ② 颜色跟着同一个进度插值（lerp）—— 白玻璃管 → 靛蓝玻璃管是渐变，
    //        中间那一瞬是"半蓝的玻璃"，不是闪一下
    //     ③ 按下时圆钮缩到 0.86 再弹回：手指按住有反馈，松开才滑过去
    //        （scale 作用在 graphicsLayer，纯绘制层，不触发重组）
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.62f,
            stiffness = 520f
        ),
        label = "switchProgress"
    )
    // 颜色插值必须夹在 0..1：spring 会过冲到 1.05，颜色外插会串色
    val cp = progress.coerceIn(0f, 1f)

    val interaction = androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val pressed by interaction.collectIsPressedAsState()
    val knobScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && enabled) 0.86f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.42f,
            stiffness = 900f
        ),
        label = "switchKnobPress"
    )

    val borderColor = if (dark) Color(0xFF94B4FF).copy(alpha = 0.55f) else WebIndigo.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .then(
                if (tag != null) Modifier.testTag(tag) else Modifier
            )
            // ★禁用态整体压暗，并吃掉点击（不能让"跟随系统"时还能偷偷改手动值）
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            // 开关也一样：drawBehind 画胶囊，不建离屏层
            .drawBehind {
                val h = size.height
                val r = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
                // ★2026-11-05 液态玻璃化（用户：「设置页的开关按钮，可以改为 liquid glass 同款的吗」）
                //
                //   原来是**一块实色轨道 + 纯白圆钮** —— 在一排玻璃按钮/卡片中间显得干、平，
                //   因为实色不参与环境光。现在按玻璃的做法重画：
                //     · 轨道：垂直渐变（上亮下暗，模拟光从上方来），半透明
                //     · 顶边：一道 1dp 的白高光（玻璃的边光）
                //     · 选中：外面再套一圈靛蓝细环（和 Web .settings-switch 一致）
                //     · 圆钮：白渐变 + 下方 1dp 软阴影 + 极淡描边，才有"浮在轨道上"的厚度
                //
                //   真玻璃没有"灰色"这个状态：未选中就是一根**透明的白玻璃管**，
                //   选中是**靛蓝玻璃管**，两者都靠边光和高光立形。
                //   ★2026-11-05 颜色改为按 cp 插值 ⇒ 切换时是渐变而不是闪一下。
                fun mix(a: Color, b: Color) = androidx.compose.ui.graphics.lerp(a, b, cp)
                val offTop = if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.60f)
                val offBottom = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.30f)
                val onTop = if (dark) Color(0xFF818CF8).copy(alpha = 0.55f) else WebIndigo.copy(alpha = 0.50f)
                val onBottom = if (dark) Color(0xFF6366F1).copy(alpha = 0.45f) else Color(0xFF4F46E5).copy(alpha = 0.38f)
                val top = mix(offTop, onTop)
                val bottom = mix(offBottom, onBottom)

                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom)),
                    cornerRadius = r
                )
                // 顶边高光（整圈 1dp，上缘最亮）
                drawRoundRect(
                    color = Color.White.copy(alpha = if (dark) 0.18f else 0.62f),
                    cornerRadius = r,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                // 靛蓝细环：跟着进度淡入（未选中时完全透明）
                if (cp > 0.01f) {
                    drawRoundRect(
                        color = borderColor.copy(alpha = borderColor.alpha * cp),
                        cornerRadius = r,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
            }
            .safeClickable(enabled = enabled, interactionSource = interaction) { onChange(!checked) }
    ) {
        // 圆钮：左 3 / 上 3 / 直径 22，行程 20dp（3 → 23）
        // progress 走 spring，所以这里是跟着"弹"过去的，不是线性平移
        Box(
            Modifier
                .padding(start = (3f + 20f * progress).dp, top = 3.dp)
                .size(22.dp)
                // 按下缩、松开弹回（读的是 layer 属性，不重组）
                .graphicsLayer {
                    scaleX = knobScale
                    scaleY = knobScale
                }
                .clip(CircleShape)
                // 圆钮：白渐变 + 下缘软阴影（玻璃珠的厚度感，不用 Modifier.shadow ——
                // 那个和 clip 叠加在部分驱动上会漏白条，弹窗那边已经踩过一次）
                .drawBehind {
                    val rr = size.minDimension / 2f
                    drawCircle(
                        color = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f),
                        radius = rr,
                        center = center.copy(y = center.y + 0.8.dp.toPx())
                    )
                }
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White,
                        1f to if (dark) Color(0xFFE8EDF7) else Color(0xFFF4F6FB)
                    )
                )
                .border(
                    1.dp,
                    if (dark) Color.Black.copy(alpha = 0.22f) else Color(0xFF0F172A).copy(alpha = 0.08f),
                    CircleShape
                )
        )
    }
}

/**
 * 网格大按钮（Web 版的 `.grid grid-cols-2` / `grid-cols-3` + `glass-btn`）
 *
 * @param cols  2 列（导入/导出，py-4）或 3 列（上传/下载/管理，py-3）
 */
@Composable
fun GridGlassButton(
    icon: ImageVector,
    label: String,
    cols: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            // ★满血液态玻璃（用户明确要求不阉割）：真 backdrop 采样 + 折射 + 高光
            .liquidGlassChip(corner = 12, flat = true)
            .safeClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = if (cols == 2) 16.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, null,
                tint = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(if (cols == 2) 24.dp else 20.dp)
            )
            Spacer(Modifier.height(if (cols == 2) 6.dp else 4.dp))
            Text(
                label,
                // 网格按钮是"图标 + 标签"的紧凑块，标签不该和行标题同号
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
    }
}

// 按钮材质统一走 glassStatic（零 shader），见 GridGlassButton 里的说明
