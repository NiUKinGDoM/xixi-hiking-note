package com.xixi.hiking.nativeapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.ui.liquid.LocalLiquidBackdrop
import com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass
import com.xixi.hiking.nativeapp.ui.liquid.glassBar
import com.xixi.hiking.nativeapp.ui.liquid.glassPill
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassSurface
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.xixi.hiking.nativeapp.ui.theme.Ink
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Speed

/**
 * App 的「外壳」：底部标签栏。
 *
 * ★从 MainActivity 抽出来（2026-11-01）：一是 MainActivity 已经太长，
 *   二是底栏以前是 private，截图测试根本渲染不到它 —— 结果「底栏透明」这个
 *   致命 bug 一直没被发现。现在它是公开组件，测试里能直接铺出来。
 */
/**
 * ★★「再点一次当前 tab」信号（对齐 Web switchTab 里的 `tabId === currentTabId` 分支）
 *
 * Web 的行为：点当前已激活的 tab → **平滑滚回页面顶部** + 刷新该页数据
 * （记录页还会把分页重置回第 1 页）。用户说的"双击底栏回最上层"就是这个。
 *
 * 这里用一个自增计数当信号：底栏 reselect 时 +1，各页面 LaunchedEffect 监听到变化
 * 就把自己的 ScrollState 滚回 0。用计数而不是布尔，是因为「连点两次」也要能各自触发。
 */
object TabReselect {
    val tick = androidx.compose.runtime.mutableIntStateOf(0)
}

enum class AppTab(val label: String, val icon: ImageVector) {
    // ★★2026-11-03 全部换成 **Filled**（用户：「底栏的记录 icon 还是看着小」）
    //
    //   真因不是尺寸 —— 是 Web 用的是 material-icons **字体图标**，那套字形本身就是
    //   filled（实心）。原生之前用 Outlined（只有一圈轮廓），同样 21dp 下：
    //     · 轮廓图形「视觉面积」只有实心的一半 ⇒ 看着小、看着轻
    //     · terrain 尤其明显：它的轮廓线又细又散，跟另外三个实心图标完全不是一个重量
    //   换回 filled 后四个重新等重，记录那颗自然就不显小了。
    OVERVIEW("概览", Icons.Filled.Analytics),
    PLANS("计划", Icons.Filled.EventNote),
    RECORDS("记录", Icons.Filled.Terrain),
    SETTINGS("设置", Icons.Filled.Settings)
}

/**
 * 胶囊形变 —— squash & stretch 的纯计算（单测覆盖）。
 *
 * ★2026-11-06 公式思路来自 Kyant0/AndroidLiquidGlass 的 `LiquidBottomTabs`：
 *     scaleX /= 1 − (v × a).coerceIn(±A)
 *     scaleY *= 1 − (v × b).coerceIn(±B)
 *   `v` 是归一化速度（每秒跨过几整条底栏，见 onDrag 里的除法）。
 *
 * ★★量级第四次校准（用户来回给了三次反馈）：
 *   官方 a=0.75 / A=0.20 → 最大 25%，用户：「太夸张了，一直在不和谐的跳动」
 *   0.025 / 0.035 → 最大 3.5%，用户：「再多一点，现在太不明显了」
 *   0.06 / 0.08 → 最大 8%，用户：「从 8% 调到 12%」
 *   现在 0.09 / 0.12 → 最大 12%。100dp 的胶囊宽度变化 12dp。
 *
 * @param velocity 归一化速度，负值 = 向左
 * @param press    按住进度 0..1；按住时整体放大到 1.39 倍（对齐官方 78/56）
 * @param bounce   额外缩放系数（1 = 不缩放，保留参数给调用方组合）
 * @return (scaleX, scaleY)
 */
internal fun pillScale(velocity: Float, press: Float, bounce: Float): Pair<Float, Float> {
    val base = 1f + 0.39f * press.coerceIn(0f, 1f)
    val stretch = (velocity * 0.09f).coerceIn(-0.12f, 0.12f)
    val squash = (velocity * 0.035f).coerceIn(-0.05f, 0.05f)
    return (base / (1f - stretch) * bounce) to (base * (1f - squash) * bounce)
}

/**
 * 悬浮底部标签栏（液态玻璃 · 按住即可拖动切换）。
 *
 * ── 结构（对齐 Kyant0/backdrop 的 LiquidBottomTabs）────────────
 * 选中块**不再是每个 tab 自己的背景**，而是一个独立的浮动指示块，
 * 由 `indicator` 这个浮点索引驱动位置 —— 这样才能跟手滑。
 *
 *   Box（底栏玻璃）
 *     └ BoxWithConstraints（内容区，宽度已知）
 *         ├ 指示块（底层，offset = index × tabW，胶囊玻璃）
 *         └ Row（上层，4 个纯图标+文字）
 *
 * ★指示块放**底层**：内容永远清晰，不被玻璃的模糊糊到。
 *
 * ── 手势（2026-11-06 改为「按住直接拖」）──────────────────────
 *   `detectDragGestures`：
 *     · 按下后移动超过 touch slop 就接管 → 指示块放大凸显（pressed）
 *     · 左右滑 → 指示块跟着手指在 tab 之间移动，实时跟手
 *     · 松手 → 就近吸附到哪个 tab 就切到哪个
 *   短按仍然走 clickable（单击直接切），两者不冲突：
 *   手指不动就不会触发 onDragStart，clickable 正常收到点击。
 *
 *   ★之前是 `detectDragGesturesAfterLongPress`（要按住 500ms），
 *     那个延迟让"跟手"从一开始就输了半拍 —— 用户的原话是
 *     「按住左右滑动的时候…缺灵动感」，这次一并去掉。
 */
@Composable
fun GlassBottomBar(
    current: AppTab,
    backdrop: com.kyant.backdrop.Backdrop,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalIsDark.current
    // ★★这里**故意不**再给底栏挂 exportedBackdrop（2026-11-02 修方块）：
    //   exported 会在 draw 阶段额外录一张**矩形**图层，虽然理论上会被上层 clip 裁掉，
    //   但实测切换时仍会闪出方角。而指示块本来就采样页面级的 LocalLiquidBackdrop，
    //   这张图从头到尾没人用 —— 纯属多录一次。删了链路更短，方块消失。

    val tabs = AppTab.entries
    val lastIndex = tabs.lastIndex
    // ══════════════════════════════════════════════════════════════
    //  ★★★2026-11-06「能滑到概览，但页面不切」的真根因
    //
    //  `pointerInput(tabW)` 的 block 只在 **tabW 变化**时重建，而它捕获的
    //  `current` 是**首次组合时的那个值**（默认概览）。之后用户点去记录页、
    //  current 变成 RECORDS，这个 lambda 里读到的仍然是 OVERVIEW ——
    //  于是松手时判断 `target != current` 恒为 false：
    //
    //      target = OVERVIEW（拖到的那格）
    //      current = OVERVIEW（第一次组合时的旧值，不是现在的 RECORDS）
    //      ⇒ 走 else 分支，只把胶囊 animateTo(0)，**永远不调 onSelect**
    //
    //  表现出来就是：胶囊滑过去了，页面纹丝不动。
    //
    //  ⇒ 用 rememberUpdatedState 拿"最新的 current"：
    //    它返回一个 State，lambda 每次读都取当前值，而 pointerInput 不会被重启
    //    （重启会打断进行中的拖动，所以不能简单把 current 塞进 key）。
    // ══════════════════════════════════════════════════════════════
    val liveCurrent by rememberUpdatedState(current)
    // 震动入口：LocalView 只能在 composable 作用域取，lambda 里用这个值
    val hapticView = androidx.compose.ui.platform.LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 指示块位置：浮点索引，可以停在 1.4 这种"两格之间"的位置（拖动跟手）
    val indicator = remember { Animatable(tabs.indexOf(current).toFloat()) }
    // ★★面板惯性位移（对标官方 LiquidBottomTabs 的 offsetAnimation）：
    //   拖动时整条底栏跟着手指轻微滑动（最多 4dp），松手弹回。
    //   这一点点位移让「整条底栏被拖」比「只有胶囊在动」真实得多。
    val panelShift = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    // ★★2026-11-06 撤掉胶囊的「点击先缩到 0.86 再弹回」（用户：「按下去的反馈
    //   有点不太好看」）。那个动画和手指没有关系 —— 手指还没松就已经缩了，
    //   松手再弹一次，等于按一下反应两回。现在胶囊只负责两件事：
    //   跟手指走 + 按住时鼓起来，不再参与"点击回弹"。

    // 外部切页（单击 / 松手后）→ 指示块滑过去
    // ══════════════════════════════════════════════════════════════
    //  ★★★2026-11-06「拖到概览松手，又自己跳回去」的根因
    //
    //  原来这里 key 是 `(current, dragging)`，而松手时 onDragEnd 里是**先**写
    //  `dragging = false`、**再**调 `onSelect(...)` 改 current：
    //
    //      dragging = false            ← 这一下就够触发 effect 重启
    //      scope.launch { indicator.animateTo(idx) }
    //      onSelect(OVERVIEW)          ← current 这时才变
    //
    //  effect 重启时若 current 还是旧值（记录页），它会 `animateTo(2)` ——
    //  **把胶囊硬拽回记录**，并且 Animatable 的互斥锁会顺手取消掉
    //  前面那个"滑向概览"的动画。两路 animateTo 打同一个 Animatable，
    //  谁后启动谁赢 ⇒ 表现出来就是"松手后弹回去、永远停不到概览"。
    //
    //  修法：key 只留 `current`。drag 松手不再自己播动画，交给这里；
    //  没切页（还在同一格）才由 onDragEnd 自己弹回。一条路径、一个动画源。
    // ══════════════════════════════════════════════════════════════
    LaunchedEffect(current) {
        // 拖动中不抢：手指还在拖时，动画会把指示块拽走
        if (!dragging) {
            indicator.animateTo(
                tabs.indexOf(current).toFloat(),
                // 0.78 太软，松手那下像被拖回来；0.62 才有"吸附"的干脆感
                spring(dampingRatio = 0.62f, stiffness = 420f)
            )
        }
    }

    val barHeight = 60.dp
    val pad = 6.dp

    // ══════════════════════════════════════════════════════════════
    //  ★★★ 三个**兄弟**节点，绝对不能嵌套（2026-11-02 方块第五次修复）
    //
    //  之前的结构是「底栏玻璃(含离屏层) ⊃ 指示块」，这才是方块的真根因：
    //  库里 DrawBackdropNode 写着 `shouldAutoInvalidate = false` —— 那层离屏图
    //  **不会因为子节点移动而自动失效**。指示块在里面一移动，屏幕上还是旧位图，
    //  于是旧位置和新位置各留一块。
    //
    //  现在的画序（从下到上）：
    //    ① 底栏玻璃（唯一有离屏层的，但它自己不动，也不含指示块）
    //    ② 指示块（纯自绘，零离屏，想怎么移就怎么移）
    //    ③ 图标文字（画在最上面，永不被胶囊挡）
    //  三者平级，谁也不在谁的离屏层里 ⇒ 结构上不可能残留。
    // ══════════════════════════════════════════════════════════════
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        // ★尺寸保护：BoxWithConstraints 在极端情况下会给出 Infinity 约束，
        //   直接 .toPx() 会抛 IllegalArgumentException —— 而这段代码跑在测量阶段，
        //   抛出来就是整个 App 闪退。这里先夹到有限值。
        val safeW = if (maxWidth.value.isFinite()) maxWidth else 0.dp
        val tabWDp = ((safeW - pad * 2) / tabs.size).coerceAtLeast(0.dp)
        val tabW = with(density) { tabWDp.toPx() }
        // 面板位移：按屏幕宽度归一化后取 EaseOut，封顶 4dp
        val maxPanelShift = 4.dp.value * density.density
        val panelFraction = if (safeW.value > 0f) (panelShift.value / (safeW.value * density.density))
            .coerceIn(-1f, 1f) else 0f
        val panelOffset = maxPanelShift * kotlin.math.sign(panelFraction) *
            androidx.compose.animation.core.EaseOut.transform(kotlin.math.abs(panelFraction))


        // ① 底栏玻璃（保留真折射；不移动，所以离屏层不会残留）
        Box(
            Modifier
                .fillMaxSize()
                // 面板惯性位移：整条底栏跟手滑一点点
                .graphicsLayer { translationX = panelOffset }
                .liquidGlassSurface(
                    backdrop = backdrop,
                    corner = 26,
                    // ★★depth 开启（2026-11-02 用户明确要求）：
                    //   depthEffect 走的是 AGSL 的**体积折射**分支 —— 不是只弯边缘，
                    //   而是按整块玻璃的厚度做体积弯折，中间区域也会跟着折射。
                    //   这是"满血液态玻璃"和"只在边上弯一下"的本质区别。
                    //   ✔ 已用 try-catch 兜住 shader 编译失败（会永久降级成纯模糊）
                    refract = 22f,
                    depth = true,
                    frost = if (dark) 0.08f else 0.06f,
                    veilAlpha = 0f,
                    tint = if (dark) Color(0xFF9FB4E8) else Color(0xFFDCE6FF),
                    tintAlpha = 0.015f,
                    // 24dp 半径的默认阴影比底栏还高，会在细长条上叠出方形灰影
                    showShadow = false,
                    // ★2026-11-06 白边收细（用户：调细一点）：0.7 → 0.42dp
                    edgeWidth = 0.42f
                )
        )

        // ★按压进度（连续值，不是布尔）：长按拖动时从 0 平滑鼓到 1
        //   ★2026-11-06 按官方 DampedDragAnimation 的 pressProgressAnimationSpec
        //     （spring(1f, 1000f)）思路放宽：阻尼 0.60 / 刚度 250 —— 松手时它会
        //     先过冲一点再收回，这一下就是胶囊"弹回去"的观感。
        val pressProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.60f, stiffness = 250f),
            label = "pillPress"
        )

        // ★★★2026-11-06 第二次修正（用户：「形变太夸张了，一丁点就行，
        //    我长按滑动一直在不和谐的跳动，丑死了」）
        //
        //  上一版为什么抖：速度值走的是 `animateFloatAsState(target = dragVel)`，
        //  而 dragVel 是**每帧都在跳**的采样值 ⇒ Spring 每帧被重新瞄准一个新目标，
        //  低阻尼（0.5）又带过冲 ⇒ 胶囊的 scaleX/scaleY 在原地来回抽搐。
        //  这是"用动画去追一个高频抖动的信号"的典型翻车。
        //
        //  ⇒ 改法：**速度不进动画管道**。
        //     · 拖动中：手算指数平滑 → 直接 snap 到 Animatable（瞬时，无追帧、无过冲）
        //     · 松手后：一次 animateTo(0) 临界阻尼收回（不弹、不抖）
        //     · 形变量级砍到 ±3.5%（官方是 ±20%，那是演示动画的量级，不是真机手感）
        //
        //  另外 panelShift（整条底栏跟手微移）保留，但它只平移不缩放，
        //  加的是"被拽了一下"的实感，不是形变。
        // ══════════════════════════════════════════════════════════════

        // 形变速度（归一化到「每秒跨过几格」）
        val shapeVel = remember { Animatable(0f) }
        // 手算平滑值：**必须 remember**，否则每次重组都归零（拖到一半重组一次就抖一下）。
        // 用 mutableFloatStateOf 而不是普通 var，但没有任何 composable 读它 ⇒
        // 每帧写它不会触发重组，纯粹当一个可写的槽位用。
        var smoothedVel by remember { mutableFloatStateOf(0f) }
        // 速度采样器：按时间戳算 px/s 再归一化（官方用 VelocityTracker 同理）
        val velTracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }


        // ② 选中胶囊 —— 液态玻璃（v1.0.5.8 的形态，2026-11-03 回退）
        //
        //   把之前那版「长按投射球 / 常驻球」整段撤掉了：用户要的就是这一枚胶囊。
        //   胶囊和底栏是**兄弟节点**，不嵌在底栏的离屏层里（否则移动会留方块残影）。
        Box(
            modifier = Modifier
                .padding(horizontal = pad, vertical = pad)
                .offset { IntOffset((indicator.value * tabW).roundToInt(), 0) }
                .width(tabWDp)
                .fillMaxHeight()
                // 动画层常驻（只改参数，链结构不变 —— 这是不出残影的前提）
                .graphicsLayer {
                    // ★squash & stretch：只留"一丁点"（见 pillScale 注释）。
                    //   量级从官方的 ±20% 砍到 ±3.5%，真机上刚好能看出"被拽了一下"。
                    val s = pillScale(shapeVel.value, pressProgress, 1f)
                    scaleX = s.first
                    scaleY = s.second
                    translationY = -1.dp.toPx() * pressProgress
                }
                .liquidGlassSurface(
                    backdrop = backdrop,
                    // 胶囊圆角 = 高度的一半（60 - 2×6 ≈ 48 → 24）
                    corner = 24,
                    refract = 10f,
                    depth = true,
                    frost = if (dark) 0.10f else 0.08f,
                    // 薄白膜：折射看得见，字也压得住
                    veilAlpha = if (dark) 0.32f else 0.28f,
                    tint = if (dark) Color(0xFF8FA6DC) else Color(0xFFE3EBFF),
                    tintAlpha = 0.05f,
                    showShadow = false,
                    // 胶囊的白边同样收细，跟底栏保持一致的发丝线
                    edgeWidth = 0.42f
                )
        )

        // ③ 图标 + 文字（最上层）+ 长按拖动手势
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = pad, vertical = pad)
                .pointerInput(tabW) {
                    // ★★2026-11-06 用户：「改成按住就能直接拖」
                    //   从 detectDragGesturesAfterLongPress 换成 detectDragGestures：
                    //   按下后移动超过系统 touch slop（约 8dp）就接管，不再等 500ms。
                    //   两者不冲突 —— 手没移动时 onDragStart 不会触发，clickable 照常收到点击。
                    detectDragGestures(
                        onDragStart = {
                            // 立即拖动不再有"长按"这件事，提示改用轻震（CLOCK_TICK），
                            // 原来的 LONG_PRESS 震动在直接拖动时听起来很怪。
                            try {
                                if (com.xixi.hiking.nativeapp.data.UiPrefs.haptic) {
                                    hapticView.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.CLOCK_TICK
                                    )
                                }
                            } catch (t: Throwable) {
                            }
                            dragPos = indicator.value
                            // ★速度采样从「这次拖动」重新开始，别把上一次的残余带进来
                            velTracker.resetTracking()
                            smoothedVel = 0f
                            scope.launch { shapeVel.snapTo(0f) }
                            dragging = true
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragPos = (dragPos + amount.x / tabW)
                                .coerceIn(0f, lastIndex.toFloat())
                            scope.launch { indicator.snapTo(dragPos) }

                            // ── 速度量测（squash & stretch 的输入）──────────────
                            // 采样的是**浮点索引位置**，除以 lastIndex 归一化成
                            // 「每秒跨过几整条底栏」，这样不同 tab 数手感一致
                            // （官方 DampedDragAnimation.updateVelocity 也是这个除法）。
                            //
                            // ★两级平滑，缺一不可：
                            //   ① VelocityTracker 内部已经是线性回归
                            //   ② 再指数平滑一次（0.30），把单帧噪声压掉
                            //   然后**直接 snap**，不进 spring —— 上一版就是让它进
                            //   spring 追帧，才有"一直在跳"的那种抽搐。
                            try {
                                velTracker.addPosition(
                                    change.uptimeMillis,
                                    Offset(dragPos, 0f)
                                )
                                val raw = velTracker.calculateVelocity().x /
                                    lastIndex.coerceAtLeast(1).toFloat()
                                smoothedVel += (raw - smoothedVel) * 0.30f
                            } catch (t: Throwable) {
                                // 速度只是观感，取不到就当 0，绝不能因此崩
                                smoothedVel = 0f
                            }
                            scope.launch { shapeVel.snapTo(smoothedVel) }

                            // ── 面板惯性位移（官方 offsetAnimation）──────────────
                            // 整条底栏跟着手指挪，最多 4dp（见 panelOffset 的封顶），
                            // 松手 spring 回 0 —— "被拽着晃了一下"的手感。
                            scope.launch { panelShift.snapTo(panelShift.value + amount.x) }
                        },
                        onDragEnd = {
                            dragging = false
                            val idx = dragPos.roundToInt().coerceIn(0, lastIndex)
                            val target = tabs.getOrNull(idx)
                            if (target != null && target != liveCurrent) {
                                // ★切页了：**不要**在这里 animateTo。
                                //   位置交给上面的 `LaunchedEffect(current)` 去滑 ——
                                //   两处同时 animateTo 同一个 Animatable 会互相取消，
                                //   谁后启动谁赢，就成了"松手后弹回去"（见上方长注释）。
                                onSelect(target)
                            } else {
                                // 没切页（还在同一格 / 越界）→ 自己弹回该格。
                                // 这里可以放心播动画，因为 current 没变、上面不会插手。
                                scope.launch {
                                    indicator.animateTo(
                                        idx.toFloat(),
                                        spring(dampingRatio = 0.62f, stiffness = 420f)
                                    )
                                }
                            }
                            // ★形变收回：临界阻尼（dampingRatio = 1）直接躺平，
                            //   绝不过冲 —— 过冲就是抖动。240 的刚度收得干净利落。
                            scope.launch {
                                shapeVel.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = 1f,
                                        stiffness = 240f,
                                        visibilityThreshold = 0.001f
                                    )
                                )
                            }
                            // 面板位移回弹（官方 spring(1f, 300f, 0.5f)：临界阻尼，不晃）
                            scope.launch {
                                panelShift.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = 1f,
                                        stiffness = 300f,
                                        visibilityThreshold = 0.5f
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch {
                                // ★这里同样必须用 liveCurrent（旧的 current 会把胶囊送到错的格子）
                                indicator.animateTo(
                                    tabs.indexOf(liveCurrent).toFloat(),
                                    spring(dampingRatio = 0.8f, stiffness = 400f)
                                )
                            }
                            scope.launch {
                                shapeVel.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = 1f,
                                        stiffness = 240f,
                                        visibilityThreshold = 0.001f
                                    )
                                )
                            }
                            scope.launch {
                                panelShift.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = 1f,
                                        stiffness = 300f,
                                        visibilityThreshold = 0.5f
                                    )
                                )
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                val active = t == current

                // ══════════════════════════════════════════════════════════
                //  ★★★2026-11-07 第六版按压反馈（用户：「把按压底删掉，不需要。
                //     我想的是按压的时候，整个按钮可以像水玻璃一样小幅度弹动」）
                //
                //  第五版加的那层淡色圆角底被否掉了 —— 用户不要"变色块"，
                //  要的是**形态上的轻微弹动**：像一块软玻璃被手指按下去，松手回弹。
                //
                //  ⇒ 去掉底色，只留形变，而且做成**非等比缩放**：
                //       scaleX = 1 − 4.5%·p     （横向略微收）
                //       scaleY = 1 − 7%·p       （纵向收得更多）
                //     两个轴缩得不一样，才会"扁"一下 —— 等比缩只是变小，
                //     压扁才像被手指按住的软材料，这是"水玻璃"和"缩小"的区别。
                //
                //  ⇒ 弹簧用 dampingRatio 0.38：低于 1 就会过冲，
                //     按下的一瞬间冲过头再收回来，松手再弹一次 —— 那一哆嗦就是"弹动"。
                //     幅度只有 4~7%，冲过头也就 1~2%，刚好在"有生气但不吵"的量级。
                // ══════════════════════════════════════════════════════════
                val tabInteraction = remember { MutableInteractionSource() }
                val pressed by tabInteraction.collectIsPressedAsState()
                val pressProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (pressed) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.38f, stiffness = 620f),
                    label = "navPress"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // ★形变加在整格上：graphicsLayer 只影响绘制、不影响测量和命中，
                        //   按下去画面会缩，但可点区域还是整格，不会越按越难点。
                        .graphicsLayer {
                            val p = pressProgress
                            scaleX = 1f - 0.045f * p
                            scaleY = 1f - 0.070f * p
                        }
                        // ★★★方块的真身：**点击涟漪**（2026-11-02 第六次，也是最后一次）
                        //   `clickable` 默认会画 ripple，颜色取自 onSurface ——
                        //   浅色主题是灰、深色主题是白（用户的原话刚好对上）。
                        //   涟漪的边界是**节点的矩形**，扩散后要几百毫秒才淡出，
                        //   于是看起来就是"椭圆按钮背后一块灰/白方块，一直在"。
                        //   之前几轮我一直在查离屏层、阴影、clip，全查错了方向。
                        //   底栏不需要涟漪（胶囊本身有按下凸显），这里 indication 传 null。
                        .clickable(
                            interactionSource = tabInteraction,
                            indication = null
                        ) {
                            // ★震动：Web 版是全局 click 委托（tab-btn 也在列表里）
                            SafeTap.tap(hapticView)
                            SafeTap.run { onSelect(t) }
                        },
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ★★灵动感（2026-11-02）：选中时图标弹一下、整体轻微上浮。
                    //   用 spring 的**低阻尼**（0.45）制造一次可见的回弹，
                    //   不是生硬的线性缩放 —— 这一下"弹"就是所谓的灵动。
                    val iconScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (active) 1.2f else 1f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 780f),
                        label = "navIconScale"
                    )
                    val lift by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (active) -1.5f else 0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
                        label = "navLift"
                    )
                    Icon(
                        imageVector = t.icon,
                        contentDescription = t.label,
                        tint = if (active) Ink
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(21.dp)
                            .graphicsLayer {
                                // 按压缩放在整格上，这里只留选中放大
                                scaleX = iconScale
                                scaleY = iconScale
                                translationY = lift.dp.toPx()
                            }
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = t.label,
                        fontSize = 10.sp,
                        color = if (active) Ink
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.graphicsLayer {
                            translationY = lift.dp.toPx() * 0.5f
                        }
                    )
                }       // ← 闭合 tab 的 Column
            }           // ← 闭合 tabs.forEach
        }               // ← 闭合图标 Row
    }                   // ← 闭合 BoxWithConstraints
}                       // ← 闭合 GlassBottomBar

// ══════════════════════════════════════════════════════════════════
//  顶栏 —— 真模糊 + 羽化过渡（2026-11-02 重做）
//
//  用户原话：「顶栏的模糊过渡依然很丑……别切两条横条一弄就完事」。
//
//  ★为什么之前丑
//    上一版是在内容区顶上盖一条 12dp 的「背景色 → 透明」渐变。那是**盖色块**，
//    不是过渡：色块有边、有厚度，底下没有东西被糊掉，看起来就是贴了两条横杠。
//
//  ★别的软件（iOS 导航栏 / 安卓大型标题栏 / Spotify、Telegram）怎么做的
//    ① 顶栏是一层**真材质**：采样底下滚动的内容，做高斯模糊（不是半透明白）；
//    ② 这层材质的下沿**用 alpha 蒙版羽化**——模糊强度从"全"渐隐到"无"，
//       而不是模糊突然截断；
//    ③ 颜色只是很薄的一层 tint，主要靠模糊把内容"化掉"。
//    所以这里也这么做：drawBackdrop 真糊 + DstIn 渐变蒙版收口。
//
//  ★内容必须能从玻璃底下划过 —— 所以各页面顶部留 TopBarHeight 的内边距，
//    而不是把页面整体往下推（推下去就永远没有东西经过玻璃，模糊等于没做）。
// ══════════════════════════════════════════════════════════════════

/**
 * 标题行的行高（玻璃的"实体"部分）。羽化带从这条线的下沿开始。
 *
 * ★2026-11-03 放大：46 → 50dp，标题 19 → 22sp。
 *   用户原话：「顶部的大标题横条太小太窄了，放大，字也放大」。
 *   22sp 不是随手取的 —— Web 在手机端就是这条规则（app.css:2071
 *   `@media(max-width:640px) { #appTitle { font-size:22px } }`），桌面端才是 18px。
 *   46dp 是照 19sp 小字配的；字涨到 22sp 后行高必须跟上，否则羽化带会咬到字底。
 */
private val TopBarRowHeight = 42.dp

/** 羽化带高度：模糊/膜在这个区间里渐变到零。短了像切一刀，长了会把正文糊掉 */
private val TopBarFeather = 14.dp

/**
 * 顶栏占用的总高度（实体 + 羽化）。
 *
 * ★页面拿它当顶部留白 —— 必须包含**羽化带**：
 *   只让开实体部分的话，第一行内容正好落在羽化带里，会被膜色洗白（截图里就是那种"发灰的一条"）。
 *   让开整条之后：静止时内容完全在玻璃外，一滚动才慢慢化进玻璃里。
 */
val TopBarHeight = TopBarRowHeight + TopBarFeather

/** 羽化曲线：smoothstep（1 − 3t² + 2t³）。比线性柔和得多，肉眼看不出"分段" */
private fun featherAlpha(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (x * x * (3f - 2f * x))
}

/**
 * 生成"上实下虚"的竖向 alpha 蒙版。
 *
 * @param solidFraction 固实段占比（= 顶栏高度 / 总高度）
 * @param color         蒙版颜色。做 alpha 遮罩时传白色 + [BlendMode.DstIn]；
 *                      做膜色时传背景色，alpha 会乘 [peak]。
 * @param peak          峰值不透明度（膜色用，遮罩传 1f）
 */
private fun featherBrush(solidFraction: Float, color: Color, peak: Float = 1f): Brush {
    val f = solidFraction.coerceIn(0f, 0.98f)
    val span = 1f - f
    val stops = listOf(0f, 0.20f, 0.42f, 0.62f, 0.80f, 0.92f)
    val list = ArrayList<Pair<Float, Color>>(stops.size + 2)
    list += 0f to color.copy(alpha = color.alpha * peak)
    list += f to color.copy(alpha = color.alpha * peak)
    stops.forEach { t ->
        list += (f + span * t) to color.copy(alpha = color.alpha * peak * featherAlpha(t))
    }
    list += 1f to color.copy(alpha = 0f)
    return Brush.verticalGradient(colorStops = list.toTypedArray())
}

/**
 * 顶栏（浮层 · 真模糊 · 羽化收口）。
 *
 * 必须画在 [com.xixi.hiking.nativeapp.ui.liquid.LiquidBackdropHost] **之外**
 * （和底栏一样），否则采样的就是自己录的那一层 = 自折射。
 *
 * @param backdrop 整页内容的 backdrop（滚到玻璃底下的东西就是模糊它的）
 * @param title    标题（点击改名，与 Web 版一致）
 */
@Composable
fun TopGlassBar(
    title: String,
    onEditTitle: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
    modifier: Modifier = Modifier,
    /** 右侧插槽：帧率胶囊（Web 版 fpsDisplay 就在标题行右边） */
    trailing: (@Composable () -> Unit)? = null
) {
    val dark = LocalIsDark.current
    // ★玻璃一直铺到屏幕**最顶**（含状态栏），不在状态栏下沿留一道接缝：
    //   那样会出现"状态栏一块、顶栏一块"的两段感，正是用户嫌弃的东西。
    //   状态栏高度在这里自己算，标题行往下让，其余照旧。
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val solidPart = statusTop + TopBarRowHeight
    val total = solidPart + TopBarFeather
    val solidFraction = solidPart / total

    // 膜色必须和 App 背景最上面那一条**完全一致**，否则顶栏上沿和状态栏之间会留一道缝。
    // （与 MainActivity.drawAppBackground 的起始色相同）
    val bgTop = if (dark) Color(0xFF0B1120) else Color(0xFFF7F8FD)

    // 模糊半径：顶栏要"糊成一片色"，太小等于没糊（8dp 还能看出字影）
    val effects: BackdropEffectScope.() -> Unit = remember {
        {
            // 真机上个别驱动创建 RenderEffect 会抛 —— 抛在 draw 阶段就是闪退，必须兜住
            try {
                blur(radius = 22.dp.toPx())
            } catch (t: Throwable) {
                // 没有模糊就只剩膜色，视觉降级但绝不崩
            }
        }
    }
    val shapeProvider: () -> Shape = remember { { RectangleShape } }

    Box(modifier = modifier.fillMaxWidth().height(total)) {
        if (LiquidGlass.gpuEffectsUsable) {
            // ① 真模糊层 —— 采样页面内容，再用羽化蒙版把下沿"化开"
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // DstIn：只保留蒙版里有 alpha 的像素 ⇒ 下沿自然渐隐
                        drawRect(
                            brush = featherBrush(solidFraction, Color.White),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = shapeProvider,
                        effects = effects,
                        highlight = null,
                        shadow = null,
                        innerShadow = null,
                        layerBlock = null
                    )
            )
        }
        // ② 膜色（同一根羽化曲线）
        //
        // ★★★2026-11-07 用户：「顶栏的白色模糊，能不能弄成透明的？
        //   现在顶栏有一坨白色，模糊是模糊的，能不能再美观一点？」
        //
        //   上一版 peak 是 0.62（深色 0.72）—— 62% 的白膜铺在 22dp 模糊层上，
        //   等于在模糊外面又刷了一层白漆，屏幕上就是一条实心白条，
        //   模糊的效果全被盖住了，"玻"是有了，"璃"没了。
        //
        //   ⇒ 膜降到 0.26（深色 0.34）。这层膜的唯一职责是**把滚过去的字压住**，
        //    让它不跟标题打架；真正把内容"化开"的是下面那层 22dp 模糊。
        //    降到 0.26 之后：模糊能看出来了（背后有内容的明暗起伏），
        //    顶栏不再是白块，而是一层薄雾。
        //   ★不能再低了（试过 0.16）：快速滚动时文字轮廓会透出来，
        //    标题行会显得脏 —— 0.26 是"能看见模糊、又不透字"的分界。
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = featherBrush(
                            solidFraction, bgTop,
                            peak = if (dark) 0.34f else 0.26f
                        )
                    )
                }
        )
        // ③ 标题（永远清晰，不参与羽化）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(TopBarRowHeight)
                .padding(start = 18.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏔️", fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            // ★★2026-11-03 修「帧率胶囊把标题挡住」：
            //   上一版标题用 weight(1f, fill=false) + 一个 weight(1f) 的 Spacer，
            //   两个权重按剩余空间**按比例**分 —— 标题先量、余量不够时就把文字画到
            //   胶囊底下去（Compose 里 Text 不受 sibling 挤压，只会被 weight 分配的空间裁）。
            //   现在标题自己吃掉**剩余的全部宽度**（weight(1f) + Ellipsis），
            //   胶囊在 Row 末尾按自身宽度占位，两者永远不可能重叠。
            Text(
                title.ifBlank { "徒步小记" },
                // ★2026-11-03 第三次调（用户：「顶栏标题和下面的内容有点太宽了，隔得有点远」）
                //   22sp 是 Web 的 #appTitle 值，但那是给「浏览器里一条细标题栏」用的；
                //   原生的顶栏下面是面板标题（17.5sp），两者差 4.5sp + 一条羽化带，
                //   视觉上就是"上面一个大字、隔老远才见内容"。
                //   现在 20sp、行高 46、羽化 22 —— 顶栏总高从 76dp 收到 68dp。
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .safeClickable(onClick = onEditTitle)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/**
 * ★顶栏帧率胶囊（1:1 对齐 Web 版 .fps-display）
 *
 * Web：橙色 rgba(251,146,60,0.35) 底 + 白字 + 等宽字体 + 999 圆角 + 白边，
 * 内容是「speed 图标 + 数字 + FPS」。默认关闭（和 Web 版一致：常驻 rAF 有合成开销）。
 */
@Composable
fun FpsPill(fps: Int, jank: Int = 0) {
    // ★★2026-11-10 颜色随帧率变（用户：「帧率胶囊的颜色变化你没做，加上」）
    //
    // 光有数字没用 —— "52" 是好是坏得心里有数才知道，而开这个开关的人
    // 就是想**一眼**看出卡不卡。所以按状态给色，且避开绿色和紫色（项目设计红线）：
    //   · 灰   fps 还没采到
    //   · 靛蓝 流畅：≥55 且这一秒没掉帧（品牌色，代表"正常"）
    //   · 橙   一般：≥40 或有零星掉帧（沿用 Web 的橙，代表"注意"）
    //   · 红   卡：<40 或掉帧 ≥3 次（代表"有问题"）
    // 用 animateColorAsState 过渡 320ms，数字每帧跳时底色不会闪。
    val target = when {
        fps <= 0 -> Color(0xFF94A3B8)
        fps >= 55 && jank == 0 -> Color(0xFF6366F1)
        fps >= 40 && jank < 3 -> Color(0xFFFB923C)
        else -> Color(0xFFEF4444)
    }
    val tone by androidx.compose.animation.animateColorAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(320),
        label = "fpsTone"
    )
    Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(tone.copy(alpha = 0.38f))
            .border(
                1.dp, Color.White.copy(alpha = 0.50f),
                androidx.compose.foundation.shape.RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            androidx.compose.material.icons.Icons.Filled.Speed, null,
            tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            // 帧率 · 掉帧数（>0 才有意义，平时不显示，免得一直闪）
            if (fps <= 0) "--" else if (jank > 0) "$fps · $jank" else fps.toString(),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "FPS", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
    }
}

/**
 * 帧率 + 卡顿统计（2026-11-05 增强）
 *
 * 只显示 FPS 是不够的：56fps 和 "每秒卡 3 下、每下 40ms" 体感完全不同，
 * 但平均帧率可能一样。所以额外统计**掉帧数**（单帧 > 32ms ≈ 掉 2 帧以上）。
 *
 * 打开「显示帧率」后胶囊会显示 `52 · 3`：左边帧率，右边这一秒里掉了几次。
 * 用户报"卡"的时候，这个数字比"感觉有点卡"有用得多。
 *
 * 关闭时不挂任何循环（零开销）。
 */
data class FpsStat(val fps: Int = 0, val jank: Int = 0)

@Composable
fun rememberFps(enabled: Boolean): Int = rememberFpsStat(enabled).fps

@Composable
fun rememberFpsStat(enabled: Boolean): FpsStat {
    var stat by remember { mutableStateOf(FpsStat()) }
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var frames = 0
        var jank = 0
        var last = 0L
        var windowStart = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                frames++
                if (windowStart == 0L) { windowStart = t; last = t }
                if (last > 0L && t - last > 32_000_000L) jank++
                last = t
                if (t - windowStart >= 1_000_000_000L) {
                    stat = FpsStat(frames, jank)
                    frames = 0
                    jank = 0
                    windowStart = t
                }
            }
        }
    }
    return stat
}
