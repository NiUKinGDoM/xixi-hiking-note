package com.xixi.hiking.nativeapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xixi.hiking.nativeapp.ui.liquid.LocalContentBackdrop
import com.xixi.hiking.nativeapp.ui.liquid.glassStatic
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassSurface
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState

/**
 * ★★弹窗打开计数 —— 让**页面内容**在弹窗出现时做一次真模糊。
 *
 * 用户反复说的「弹窗没有 Web 那种通透感」，根因只有一个：
 *   Web 的 `.confirm-modal` 带 `backdrop-filter: blur(2px)`，背景被糊成一片色，
 *   所以弹窗只要 0.15 的白膜就能看清；
 *   原生弹窗拿不到背景模糊（离屏 + blur 在这个尺寸会崩，试过三轮），
 *   ⇒ 背后的列表每一行字都原样穿过来，只能靠把弹窗做成白板来压 —— 于是永远"太实"。
 *
 * 解法：既然弹窗不能采样背景，那就**让背景自己糊掉**。
 *   GlassOverlay 进出时给这个计数器 +1/-1，页面根节点据此加 `Modifier.blur`。
 *   API 31 以下 blur 是 no-op（自动降级成清晰背景，弹窗照样能用）。
 */
/**
 * ★★2026-11-05 弹窗内的「关闭」入口 —— 让内容里的按钮也能走**退场动画**。
 *
 * 背景：外部传进来的 onDismiss 一般是 `{ showX = false }`，调用后外层状态一变，
 * 整个 GlassOverlay 立刻从树上消失 ⇒ 没有退场动画。
 * 只有"点空白 / 返回键 / 底部关闭"走了 closeWithAnim，所以用户看到
 * 「有的弹窗有关闭动画，有的没有」。
 *
 * 现在把这个能力做成 CompositionLocal：弹窗内容里的「取消 / 关闭」按钮
 * 只要调 [LocalOverlayClose]，就会先收拢 130ms 再真正关闭。
 */
val LocalOverlayClose = androidx.compose.runtime.compositionLocalOf<() -> Unit> { {} }

/**
 * ★★2026-11-05「一镜到底」的起点 —— 最后一次手指按在屏幕的哪个位置（0..1）。
 *
 * 金标联盟那套 SharedView Kit 是系统级共享元素，个人项目接不了；
 * 但它的**观感**可以自己实现：让弹窗从"你点的那个位置"长出来，而不是从屏幕正中胀开。
 * MainActivity 在根布局用 Initial pass 记录每一次按下的坐标（不消费事件，不影响任何点击），
 * 弹窗把它当 transformOrigin 用。
 */
object OverlayOrigin {
    var fx = 0.5f
    var fy = 0.5f
}

val LocalOverlayCount = androidx.compose.runtime.compositionLocalOf {
    androidx.compose.runtime.mutableIntStateOf(0)
}

/** 当前打开着的浮层数量（保留给外部统计用） */
object OverlayOpen {
    val count = androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * 弹窗栈 —— 保证同一时刻屏幕上只有一个弹窗，且**下层能恢复**。
 *
 * ★★★2026-11-03 修一个致命 bug（用户：「足迹里怎么点不开记录了？」）：
 *
 *   上一版是 `top: String?`：后开的把 top 抢走，先开的就不绘制。
 *   但先开的那个**已经忘记了自己该重新出现** —— `top` 只在 DisposableEffect
 *   （只跑一次）里写，后开的关掉时把 top 置成 null，先开的发现
 *   `top != myId` 就 continue 返回，而它的 effect 再也不会重跑 ⇒
 *   **永久隐身**。上层 state 却还是非空，于是用户再怎么点都"点不开"。
 *   （实测：A 可见 → 开 B → 关 B → A 再也不出现。）
 *
 *   现在改成**真栈**：进入时 push、退出时 pop，只有栈顶绘制。
 *   上层 pop 掉之后，下一层自然又变回栈顶 → 一次访问就重新变可见。
 *   用 SnapshotStateList，所以"谁是栈顶"的变化会自动触发重组。
 */
object OverlayStack {
    private val ids = androidx.compose.runtime.mutableStateListOf<String>()
    private var seq = 0

    fun nextId(): String = "ov-" + (seq++)

    /** 新弹窗进来：压到栈顶（重复压会先移除再压，保证唯一） */
    fun push(id: String) {
        if (ids.contains(id)) ids.remove(id)
        ids.add(id)
    }

    /** 弹窗离开：从栈里摘掉，下面那个自动回到栈顶 */
    fun pop(id: String) {
        ids.remove(id)
    }

    /** 是不是当前唯一该画的那个 */
    fun isTop(id: String): Boolean = ids.lastOrNull() == id

    /** 给测试/自诊用：当前栈深 */
    fun depth(): Int = ids.size
}


/**
 * ★玻璃浮层（Glass Overlay）—— 用同窗口 Box 替代 Dialog。
 *
 * 为什么不用 Dialog：Dialog 是**独立 Window**，采不到主窗口背景 ⇒ 弹窗只能靠
 * 「高不透明度」伪装成玻璃，看起来就是块灰板。
 *
 * ── 材质（2026-11-02 换 backdrop 库后）──────────────────────
 * 弹窗玻璃采样 [LocalContentBackdrop]（整页内容）：
 * 滚到底下的记录列表会被真实弯折 + 色散 + 高斯模糊，这才是液态玻璃。
 *
 * ★★★关键：弹窗会给自己的内容**导出**一份 backdrop（dialogBackdrop）。
 * 弹窗里的按钮（「保存」「取消」）通过 [LocalLiquidBackdrop] 拿到的是这一份，
 * 所以按钮折射的是「弹窗自己的底」——
 * 而不是把弹窗背后那 18 行列表折进小小的按钮里（那个效果非常丑，像开了个窥视窗）。
 *
 * @param maxHeightFraction 高度上限（占屏高比例）。长列表弹窗必须限高 ——
 *        否则内容会把卡片撑到贴满上下边缘，按钮被挤出屏幕（Web 版统一 72vh）。
 */
/**
 * 「当前已经在一个弹窗里」标记。
 *
 * ★★★2026-11-05 修 bug（用户：「照片和灯箱部分的删除键，按了没有弹窗」）
 *
 *   根因是**弹窗套弹窗**在 Compose 里的两个硬限制：
 *     ① 外观层的 content 画在卡片内部，而卡片有 `.clip(圆角)` + `heightIn(max=72%屏高)`，
 *        所以内层弹窗被**裁掉 + 挤在卡片那一小块**里；
 *     ② 更致命的是 OverlayStack：内层一 push，外层 `isTop` 变 false 就整棵 `return`，
 *        而内层恰好是外层的子节点 ⇒ **跟着一起不画了**。
 *        ⇒ 点了删除、状态也确实置了、内层也确实组合了，屏幕上什么都没有。
 *
 *   修法：内层不改架构，只换宿主 —— 检测到自己已在弹窗里，就把自己塞进独立窗口（Popup）
 *   全屏渲染，并且**不参与 OverlayStack**（不 push、不 pop、不改 OverlayOpen 计数）。
 *   这样外层照常画、内层浮在上面，关掉内层外层自然还在。
 */
val LocalInOverlay = androidx.compose.runtime.compositionLocalOf { false }

/**
 * 嵌套弹窗该用哪种宿主渲染。
 *
 * ★★★2026-11-05 第二版（用户：「灯箱点删除还是没显示确认框」+「照片角上的 × 显示了但严重偏移」）
 *
 *   第一版只有一种做法：只要「在弹窗里」就塞 Popup。两个后果：
 *     ① **偏移**：Popup 的尺寸我用了 `screenWidthDp/screenHeightDp`（含状态栏/导航栏），
 *        而 Popup 窗口本身只有应用可用区域 ⇒ 内容比窗口大，居中点被推下去一大截。
 *     ② **灯箱里完全不显示**：灯箱本身就是 Dialog（一个窗口），在 Dialog 的窗口里再开
 *        Popup，等于在一个弹窗窗口上再挂一个窗口 —— 层级/锚点都不稳，实测什么都不出来。
 *
 *   所以分两种宿主：
 *     · [OverlayHostMode.WINDOW]（默认）：内容在**弹窗卡片内部**（有圆角裁剪 + 72% 限高），
 *       必须换独立窗口才不被裁 —— 照片网格的 × 就是这种。
 *     · [OverlayHostMode.INLINE]：内容已经在**全屏容器**里（灯箱那个 Dialog 是全屏、
 *       不裁剪），直接就地画在最上层即可，不要再套窗口。
 *       由灯箱通过 `LocalOverlayHost` 声明。
 */
enum class OverlayHostMode {
    WINDOW,
    INLINE,
}

val LocalOverlayHost = androidx.compose.runtime.compositionLocalOf { OverlayHostMode.WINDOW }

@Composable
fun GlassOverlay(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    maxWidthFraction: Float = 0.90f,
    corner: Int = 24,
    maxHeightFraction: Float = 0.80f,
    bottomClose: Boolean = true,
    content: @Composable () -> Unit
) {
    val nested = LocalInOverlay.current
    val host = LocalOverlayHost.current

    if (nested) {
        if (host == OverlayHostMode.INLINE) {
            // 全屏容器里（灯箱）：就地画，不建窗口、不入栈。
            // 这里给厚一点的膜 —— 灯箱背后是纯黑 + 照片，没有主窗口那层背景模糊，
            // 0.58 的白膜压在黑底上会变成半透明的灰块，字反而不清楚。
            GlassOverlayImpl(
                hazeState = hazeState,
                onDismiss = onDismiss,
                maxWidthFraction = maxWidthFraction,
                corner = corner,
                maxHeightFraction = maxHeightFraction,
                bottomClose = bottomClose,
                participateInStack = false,
                opaqueVeil = true,
                content = content
            )
            return
        }
        // 弹窗卡片内部：必须换独立窗口，否则被卡片裁掉、还会被外层"只画栈顶"带走
        val win = androidx.compose.ui.platform.LocalWindowInfo.current
        val dens = androidx.compose.ui.platform.LocalDensity.current
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val cs = win.containerSize
        // ★用 window 的 containerSize（应用可用区域），不要用 screenWidthDp ——
        //   后者含状态栏/导航栏，内容会比 Popup 窗口大，居中被推下去一大截（就是那个"严重偏移"）。
        val wDp = if (cs.width > 0) with(dens) { cs.width.toDp() } else cfg.screenWidthDp.dp
        val hDp = if (cs.height > 0) with(dens) { cs.height.toDp() } else cfg.screenHeightDp.dp

        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopStart,
            properties = androidx.compose.ui.window.PopupProperties(
                focusable = false,
                clippingEnabled = false
            )
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalInOverlay provides true,
                LocalOverlayHost provides OverlayHostMode.WINDOW,
                com.xixi.hiking.nativeapp.ui.liquid.LocalInContentLayer provides false
            ) {
                // 必须显式给尺寸：Popup 的父约束是无限制的，fillMaxSize 会塌成 0
                Box(Modifier.width(wDp).height(hDp)) {
                    GlassOverlayImpl(
                        hazeState = hazeState,
                        onDismiss = onDismiss,
                        maxWidthFraction = maxWidthFraction,
                        corner = corner,
                        maxHeightFraction = maxHeightFraction,
                        bottomClose = bottomClose,
                        participateInStack = false,
                        content = content
                    )
                }
            }
        }
        return
    }
    GlassOverlayImpl(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = maxWidthFraction,
        corner = corner,
        maxHeightFraction = maxHeightFraction,
        bottomClose = bottomClose,
        participateInStack = true,
        content = content
    )
}

@Composable
private fun GlassOverlayImpl(
    /** 已无渲染用途（玻璃自己采样），保留是为了不动全部调用点 */
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState,
    onDismiss: () -> Unit,
    /**
     * ★已失效（2026-11-03）：弹窗宽度全站统一为 min(92% 屏宽, 400dp)。
     *   保留参数只为不改 23 个调用点 —— 新代码不用再传。
     */
    @Suppress("UNUSED_PARAMETER") maxWidthFraction: Float = 0.90f,
    /** 已失效：圆角固定 20（见上） */
    @Suppress("UNUSED_PARAMETER") corner: Int = 24,
    /** 已失效：高度上限统一 86% 屏高（短内容自己会缩，不影响） */
    @Suppress("UNUSED_PARAMETER") maxHeightFraction: Float = 0.80f,
    /**
     * 底部要不要自动挂「关闭」按钮。
     *
     * ★2026-11-03：用户要求弹窗收口统一成底部关闭按钮、右上角 ✕ 全部去掉。
     *   绝大多数弹窗直接用默认 true 就行；
     *   本身已有操作行的（确认框的 取消/确认、详情页的 编辑/删除、表单的 取消/保存）传 false，
     *   否则会出现「关闭 + 取消」两个同义按钮。
     */
    bottomClose: Boolean = true,
    /** 是否参与弹窗栈：嵌套弹窗传 false（否则会把外层顶掉，见 LocalInOverlay 注释） */
    participateInStack: Boolean = true,
    /** 用更厚的膜：背后不是模糊背景而是黑底/照片时（灯箱里的确认框） */
    opaqueVeil: Boolean = false,
    content: @Composable () -> Unit
) {
    val dismissSource = remember { MutableInteractionSource() }
    val blockSource = remember { MutableInteractionSource() }

    // ★限高按屏高算，不能给死 dp：矮屏（折叠屏外屏 320×780）上死 dp 会溢出去
    val cfg = LocalConfiguration.current
    val screenH = cfg.screenHeightDp.dp
    // ★★2026-11-03 弹窗尺寸/位置统一（用户：「归类弹窗大小和位置的适配，现在问题还是很多」）
    //   Web 的规矩：遮罩 padding 20、卡片 max-width 400px、宽度按用途分三档、
    //   高度上限按屏高算并给上下留白。这里照抄，另外把屏幕可用高度先扣掉安全区，
    //   免得矮屏（外折叠 320×780）上弹窗贴边、按钮被手势条压住。
    val isDark = LocalIsDark.current

    // ★★★2026-11-04【第四版 · 真高斯模糊】
    //   背景层现在会在弹窗打开时做真 GPU 模糊（见 MainActivity 的 bgBlur），
    //   所以弹窗**不再需要靠白膜压住背后的字** —— 背后已经是一团柔和的色块。
    //   膜从 0.96 直接降到 0.56（深色 0.62）：背后那层糊掉的颜色透上来，
    //   这才是 Web `background: rgba(255,255,255,0.15) + backdrop-filter: blur()` 的观感。
    //
    //   ★降级保护：拿不到 GPU 模糊的机器（API<31 / 安全模式 / 截图测试）仍然走 0.96 厚膜，
    //    否则背后每一行字都会清晰地穿进弹窗，那是没法读的。
    // ★★2026-11-04 只要本机有 GPU 能力，背景就一定有那层模糊
    //   （由 MainActivity 的 overlayCount 层负责，弹窗只画膜）。
    //   拿不到 GPU 模糊的机器（API<31 / 安全模式 / 截图测试）退回 0.96 厚膜。
    val bgBlurred = com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass.gpuEffectsUsable
    // 弹窗里已经有一层真模糊（见下面的 drawBackdrop），膜只负责"压住可读性"：
    //   0.58 / 0.64 —— 背后那团糊掉的颜色透上来，字仍然压得住。
    //   拿不到 GPU 模糊的机器（API<31 / 安全模式 / 截图测试）退回 0.96 厚膜，
    //   否则背后每一行字都会清晰地穿进弹窗。
    val veilAlpha = when {
        // 灯箱里的确认框：背后是黑底照片，没有背景模糊，必须用厚膜
        opaqueVeil && isDark -> 0.94f
        opaqueVeil -> 0.92f
        bgBlurred && isDark -> 0.64f
        bgBlurred -> 0.58f
        isDark -> 0.97f
        else -> 0.96f
    }

    val pageBackdrop = LocalContentBackdrop.current

    // ★★2026-11-03 一次只显示一个弹窗（用户：「不许两个弹窗盖弹窗」）
    //   后开的压到栈顶，先开的不绘制；后开的一关，先开的**自动回到栈顶重新出现**
    //   （旧的 top 单值版做不到"回来"，会让先开的永久隐身 —— 见 OverlayStack 注释）。
    // ★★2026-11-04 首帧就入栈 —— 原来 `remember { nextId() }` + DisposableEffect 里 push，
    //   导致**第一帧 onTop 一定是 false**（栈里还没自己），弹窗闪一帧空白再出来。
    //   现在在 remember 里同步入栈，第一帧就是栈顶。
    // 嵌套弹窗（在自己的 Popup 窗口里）**不参与栈**：
    //   它本来就是盖在外层之上的独立窗口，再入栈会把外层顶掉（那正是本次 bug 的成因）。
    val myId = remember {
        if (!participateInStack) "" else OverlayStack.nextId().also { OverlayStack.push(it) }
    }
    // 读 isTop 会订阅 SnapshotStateList ⇒ 栈一变就重组，不会卡在"隐身"状态
    val onTop = !participateInStack || OverlayStack.isTop(myId)
    androidx.compose.runtime.DisposableEffect(myId) {
        if (participateInStack) { OverlayOpen.count.intValue++ }
        // 栈位置已在 remember 里压好（见上），这里只负责退出时摘掉
        onDispose {
            if (participateInStack) {
                OverlayOpen.count.intValue--
                OverlayStack.pop(myId)
            }
        }
    }
    if (!onTop) return

    // ★★2026-11-04 入场动画：0.94× → 1× 轻微弹一下 + 淡入。
    //   一是质感，二是**明确的"打开了"反馈** —— 用户报「点了按钮没反应」，
    //   而实测点击链路是通的；弹窗之前是"啪"地直接出现、和白底页面几乎同色，
    //   这一下弹出能让人立刻知道点到了。
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    // ★★★2026-11-04 弹窗里的"真背景模糊"（第五次尝试，这次走位图路线）
    //
    //   弹窗刚组合出来的那一帧，`appear` 还是 0 ⇒ graphicsLayer 的 alpha 只有 0.02，
    //   此时调用 `view.draw(canvas)` 抓到的窗口位图**几乎是干净的背景**。
    //   抓完把这张图降采样到 1/24 再交给 Compose 拉伸铺满弹窗 ——
    //   "降采样再放大"本身就是一种廉价高斯，出来就是一片柔和色块（= 磨砂玻璃）。
    //   全程不用 RenderEffect / shader / 离屏，所以这台机器上不会崩。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // ★2026-11-05 入场重调（用户：「优化一下弹窗动画」）：
        //   0.94→1 的弹一下太"弹"，收尾会抖两下。改成更稳的 0.90→1，
        //   阻尼 0.72 / 刚度 460 —— 有升起的手感，但不回弹过头。
        appear.animateTo(
            1f,
            androidx.compose.animation.core.spring(
                dampingRatio = 0.72f,
                stiffness = 460f
            )
        )
    }

    // ★★★2026-11-05 退场动画（「一镜到底」的另一半：返回时收回）
    //
    //   以前所有关闭路径都是"啪"地消失 —— 展开有动画、收回没有，
    //   观感上是半截的。现在点空白 / 返回键 / 底部关闭都先播 130ms 的收拢，
    //   再真正关掉。
    //   ★内容里的「取消 / 保存」按钮仍走调用方自己的 onDismiss（它们本来就要立刻生效），
    //     不做拦截 —— 否则保存成功还要等 130ms，反而黏。
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var closing by remember { androidx.compose.runtime.mutableStateOf(false) }
    val closeWithAnim: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                runCatching {
                    appear.animateTo(
                        0.86f,
                        androidx.compose.animation.core.tween(
                            durationMillis = 130,
                            easing = androidx.compose.animation.core.FastOutLinearInEasing
                        )
                    )
                }
                onDismiss()
            }
        }
    }

    // 返回键必须关弹窗，不能退 App（注册顺序放这里不影响功能）
    BackHandler(enabled = true) { closeWithAnim() }

    // ★★★2026-11-02 去掉弹窗自己的 exported backdrop：
    //   它会在 draw 阶段「在离屏层内部再 record 一个离屏层」—— 这是嵌套离屏，
    //   叠加 blur 的 RenderEffect 后，真机 GPU 极易崩。
    //   表现正是用户报的：点带弹窗的选项 → 弹窗还没显示出来 → 直接闪退。
    //   代价：弹窗内的按钮玻璃改为采样页面背景（不再是弹窗自己的底），
    //   视觉差异很小（按钮本来就是薄的），但少一层离屏 = 少一个崩溃源。

    // ★★★2026-11-04【搬家】页面内弹窗 → 借一个独立窗口渲染
    //
    //   问题：记录页 / 计划页 / 概览页 / 设置页里有一批弹窗是直接写在页面 composable
    //   内部的，它们本身就在 [LiquidBackdropHost] 录制的子树里 ⇒
    //   ① 采样自己的纹理（每帧把自己再糊一遍，几帧后成糊块）
    //   ② 就算不采样，也被外面那层整页模糊盖住
    //   所以这些弹窗一直拿不到 Web 的 backdrop-filter。
    //
    //   搬家方式：**不改任何调用点、不动业务状态**，只在这里换一个宿主窗口 ——
    //   `Dialog` 是独立 Window，它的内容不参与宿主窗口的 RenderNode 录制，
    //   于是弹窗终于"站在采样源之外"，可以直接采样 contentBackdrop 做真模糊；
    //   而它自己的位置/尺寸/层级完全不变（全屏、居中、zIndex 照旧）。
    //
    //   安全阀：
    //     · 只在 gpuEffectsUsable 的机器上搬（拿不到 GPU 效果的机器维持原样）
    //     · dimAmount = 0 ⇒ 弹窗外面一丝都不压暗（用户定的规矩：背景不许变色）
    //     · dismissOnBackPress = false + 保留原来的 BackHandler ⇒ 返回键行为不变
    val inContentLayer = com.xixi.hiking.nativeapp.ui.liquid.LocalInContentLayer.current
    val body: @Composable () -> Unit = {
        OverlayBody(
            onDismiss = onDismiss,
            bottomClose = bottomClose,
            content = content,
            isDark = isDark,
            veilAlpha = veilAlpha,
            bgBlurred = bgBlurred,
            appear = appear.value,
            onRequestClose = closeWithAnim,
            screenH = screenH,
            pageBackdrop = pageBackdrop,
            dismissSource = dismissSource,
            blockSource = blockSource
        )
    }
    if (inContentLayer && com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass.gpuEffectsUsable) {
        // 用 Popup 而不是 Dialog：Dialog 会带一层 0.32 的黑色 dim（用户明确不许背景变色），
        // 而 Compose 的 DialogProperties 不开放 dimAmount；Popup 天生没有 dim，
        // 也不会抢占返回键（focusable = false ⇒ 返回键仍由主窗口的 BackHandler 处理）。
        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopStart,
            properties = androidx.compose.ui.window.PopupProperties(
                focusable = false,
                clippingEnabled = false
            )
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.xixi.hiking.nativeapp.ui.liquid.LocalInContentLayer provides false
            ) {
                // ★必须显式给全屏尺寸：Popup 的父约束可能是 UNSPECIFIED，
                //   里面的 fillMaxSize() 会塌成 0（弹窗就"看不见"了）。
                Box(
                    Modifier
                        .width(cfg.screenWidthDp.dp)
                        .height(cfg.screenHeightDp.dp)
                ) { body() }
            }
        }
    } else {
        body()
    }
}

@Composable
private fun OverlayBody(
    onDismiss: () -> Unit,
    bottomClose: Boolean,
    content: @Composable () -> Unit,
    isDark: Boolean,
    veilAlpha: Float,
    bgBlurred: Boolean,
    appear: Float,
    onRequestClose: () -> Unit,
    screenH: Dp,
    pageBackdrop: com.kyant.backdrop.Backdrop?,
    dismissSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    blockSource: androidx.compose.foundation.interaction.MutableInteractionSource
) {
    Box(
        modifier = Modifier
            // ★2026-11-04 弹窗永远盖在同层所有东西之上（底栏/顶栏/悬浮搜索条）——
            //   万一有谁被后画就会把弹窗压住，用户看到的就是「点了按钮没反应」。
            .zIndex(1000f)
            .fillMaxSize()
            // ★★2026-11-03 遮罩大幅减淡（用户：「打开弹窗后，背景会变色，不要变了」）
            //
            //   一路演变：0.30(Web) → 0.34/0.62 → 0.78(深色)。
            //   每次都是为了让"薄玻璃弹窗"压得住背景、字看得清 —— 但代价是
            //   一开弹窗整个页面**换了一个色**（浅色白底被黑遮罩压成灰），
            //   用户看到的就是"背景变色"，非常跳。
            //
            //   ★★2026-11-03 第八版（用户：「打开任何弹窗都不要有什么遮罩，只显示弹窗就好了」）——
            //   遮罩彻底去掉：不压暗、不变色。点击空白关闭仍然保留（那一层 Box 照样铺满全屏接手势）。
            // Web：.confirm-modal padding 20 + 底部安全区
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .clickable(
                interactionSource = dismissSource,
                indication = null,
                onClick = onRequestClose
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                // ★★★2026-11-03 弹窗尺寸**彻底归一**（用户第三次提：别大的大小的小）
                //
                //   Web 版其实只有**一套**规格：.confirm-modal-content
                //     max-width: 400px; width: 100%; border-radius: 20px; padding: 20px;
                //   不管确认框、编辑表单还是更新日志，宽度和圆角完全一样 ——
                //   只有"高度"随内容走（长文有 72vh 上限）。
                //
                //   所以这里也不再按 0.84/0.90/0.94 分三档：
                //     · 宽度：min(屏宽 × 0.92, 400dp) —— 小屏铺满边距，大屏不外扩
                //     · 圆角：20（固定）
                //     · 高度：内容自适应，上限 **72% 屏高**（= Web 的 72vh）
                //   传进来的 maxWidthFraction / corner / maxHeightFraction 全部不再生效，
                //   保留参数只是为了不改 23 个调用点。
                .fillMaxWidth(0.92f)
                .widthIn(max = 400.dp)
                .heightIn(max = screenH * 0.72f)
                // 入场：0.94 → 1 的轻微放大。
                // ★只动 scale，**不动 alpha** —— alpha<1 会让 Compose 给弹窗建一层离屏，
                //   而"弹窗尺寸的离屏层"在这台机器的 GPU 上历史上崩过三次（OOM/驱动）。
                //   缩放是合成期的变换，不需要离屏，安全。
                .graphicsLayer {
                    // ★从"最后按下的位置"展开/收回（一镜到底的观感）
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        OverlayOrigin.fx.coerceIn(0f, 1f),
                        OverlayOrigin.fy.coerceIn(0f, 1f)
                    )
                    val a = appear.coerceIn(0f, 1f)
                    scaleX = 0.90f + 0.10f * a
                    scaleY = 0.90f + 0.10f * a
                    // ★★★2026-11-04 第三项：入场改成「从下往上 12dp 滑入 + 淡入」
                    //   抄 Web 的 `scale(0.8) translateY(20px)` → `scale(1) translateY(0)`。
                    //   缩放保留（只缩放不动 alpha 的旧理由已不成立：alpha 本来就在动），
                    //   加一点上滑，弹窗才有"升起来"的方向感，而不是原地胀大。
                    translationY = (1f - a) * 16.dp.toPx()
                    alpha = a
                }
                // ★★★最终方案（2026-11-02）：弹窗用**自绘玻璃**，不走 backdrop。
                //
                //   实测过三轮：
                //     ① 真 backdrop + refract  → 崩
                //     ② 真 backdrop + refract=0 → 还是崩
                //     ③ 自绘 glassStatic        → 正常
                //   ⇒ 与折射无关，是**尺寸**：弹窗 0.88 屏宽 × 0.86 屏高，
                //   在 3x 屏上约 2850×2100 ≈ 600 万像素；backdrop 采样 + 模糊各要一张
                //   这个尺寸的离屏位图（单张 ≈24MB），叠加直接 OOM。
                //   底栏只有 60dp 高、位图小十几倍，所以它没事。
                //
                //   自绘版没有真实背景模糊，但用「半透明白膜 + 顶部漫反射 + 边缘高光 +
                //   内壁棱 + 轮廓暗线」把玻璃的观感补齐 —— 背景是清晰透出的，
                //   不糊，反而比之前那版"糊成一团"更透亮。
                // ★★★2026-11-03 弹窗材质统一（用户第三次要求「所有弹窗设计语言保持一致」）
                //
                //  取 Web 版 .confirm-modal-content 的配方：
                //    · 圆角 20、内边距 20
                //    · 浅色：白 0.15 底 + 白 0.5 描边 / 深色：#101A30 0.32 + 蓝 0.18 描边
                //    · 标题 18~20 / 900，正文 14 / #1F2937（深 #D1D5DB）
                //
                //  ★★2026-11-03 第四次调透明度（用户：「弹窗太过于实了」）：
                //   Web 能给 backdrop-filter: blur(2px)，原生弹窗层不能做真模糊（会崩）。
                //   我上一版用「把膜加到 0.55 / 0.80」来硬补可读性 —— 结果是弹窗变成一块白板，
                //   背后什么都透不出来，用户一眼就嫌"太实"。
                //   现在改成 **薄膜 + 略深的遮罩**：
                //     · 膜 0.34 / 0.50 —— 背景色和形状能透上来，玻璃感回来
                //     · 遮罩 0.34 / 0.62 —— 靠外面这层压暗背景，而不是靠弹窗自己变白
                //   这样文字（深墨/浅墨）压上去依然够读，同时不是一块死白。
                // ★★★2026-11-03 不用 glassStatic 画膜了 —— 它和上面的 drawBackdrop 打架：
                //   两者都是 drawWithContent，嵌套之后膜的绘制被吞掉，弹窗就变成全透明
                //   （截图实锤：背后每一行字都直接穿过来）。改成最朴素的 background + border，
                //   绘制顺序确定，不再互相干扰。
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                // ★★★2026-11-03 最终：**不做背景模糊，用高浓度膜模拟磨砂**。
                //
                //   试过三条路：
                //     ① 全页 Modifier.blur  → 整个屏幕都糊了，用户直接否
                //     ② glassStatic(0.15)   → 背景清晰，背后每行字都穿过来，没法读
                //     ③ drawBackdrop+blur   → 它的 drawWithContent 会把后面所有背景绘制吞掉，
                //                              弹窗变成全透明（截图实锤），此路不通
                //   弹窗尺寸的离屏 + 模糊在真机上还会 OOM/崩（历史上试过三轮）。
                //   所以只能退到"磨砂感"：0.72/0.86 的白膜 —— 背后内容只剩一层淡淡的影子，
                //   既不是一块死白，也不会串字。
                // ★★★2026-11-03 第七版：用 **haze** 做真·弹窗内背景模糊。
                //   前面三条路全废（全页 blur / 薄膜 / drawBackdrop 吞绘制），
                //   haze 是项目本来就带的库（dev.chrisbanes.haze:0.7.3），专治这个：
                //   `hazeSource` 标记页面内容、`hazeEffect` 在弹窗上采样 + 高斯模糊。
                //   膜只要 0.15/0.30 就够 —— 背后已经糊成一片色。

                // ★★★2026-11-04 用户报「四个按钮点了没反应」——实测点击是好的，
                //   真正的问题是**弹窗看着像没出现**：浅色页面本身就是白底，
                //   弹窗是「白 0.84 膜 + 0.26 淡靛蓝描边」，边界几乎融进背景，
                //   于是点完只当"页面抖了一下"。
                //   现在：膜拉到 0.94（背后只剩一层极淡的影，不会有字穿上来），
                //   描边拉到 0.42，再加一层真投影 —— 弹窗清清楚楚"浮"在页面上。
                // ★★★2026-11-04【永久禁用 Modifier.shadow —— 白条的真凶，已实测确认】
                //
                //   症状：弹窗卡片内部靠上的位置，有一条 880×32px 的**纯白实心横条**，
                //   边缘是硬的（说明不是被模糊过的背景），换模糊半径、加雾都去不掉。
                //   根因：`Modifier.shadow(elevation = 30.dp, clip = false)` 紧接 `.clip(圆角)`
                //   —— 阴影的 RenderNode 和裁剪层叠加时，阴影层的底色被漏了出来（白）。
                //   删掉投影那一行，白条立刻消失（用户实测确认）。
                //
                //   ⇒ 以后**不要**再用 Modifier.shadow 给弹窗做投影。
                //   要立体感就用「边框 + 顶部高光」（现在这套），
                //   或者以后在卡片背后自绘一层低透明度圆角矩形（那种画法不会出 artifact）。
                // .shadow(
                //     elevation = 30.dp,
                //     shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                //     clip = false,
                //     ambientColor = Color.Black.copy(alpha = 0.34f),
                //     spotColor = Color.Black.copy(alpha = 0.46f)
                // )
                // 底色/膜的绘制挪到内容里（见下面的 ①②③ 层）——
                // 因为要先铺"模糊背景位图"，再铺膜，最后才是内容。
                // ★★2026-11-04 边框改成**透明白框**（用户：「改成和概览页最大那个框一样的，
                //   透明白框有高光就可以」）
                //
                //   之前是靛蓝 0.42 —— 那是"弹窗没背景模糊、白边融进白底"时的补救措施；
                //   现在弹窗里有真模糊、外面有 30dp 投影，边界本身站得住，就不需要染色了。
                //   白框和概览页的 `.glass-panel` 同一个语言：一条几乎无线条的亮边，
                //   靠"亮 + 高光"而不是"深"来分界。
                //   浅色 0.55（Web `.confirm-modal-content` 就是 rgba(255,255,255,.5)），
                //   深色 0.30（太亮会变成一圈刺眼的白线）。
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.30f)
                    else Color.White.copy(alpha = 0.55f),
                    androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                )
                // 吃掉点击，避免穿透到遮罩触发关闭
                .clickable(interactionSource = blockSource, indication = null) { }
        ) {
            // ── 底：半透膜（模糊由 MainActivity 那层全屏背景层提供）──
            //
            // ★★★2026-11-04【最终版】弹窗不再自己采样背景。
            //
            //   试过三条路：① 自己 drawBackdrop 采样（顶层好用，页面内自采样糊成一团）
            //              ② 塞进 Popup 再采样（跨窗口画不出来，设置页一个都没有）
            //              ③ 整层 blur 包页面（弹窗一起被糊）
            //   最终选：**模糊在主窗口做一层**（MainActivity 的 overlayCount 那层），
            //   弹窗只是盖在上面的半透明卡片 —— 和 Web 的 DOM 结构一模一样：
            //   遮罩层负责 backdrop-filter，弹窗自身 background 只有 0.15。
            //
            //   所以这里只画膜，不碰 backdrop。膜 0.55 / 0.62：
            //   能透出背后那层糊掉的色块，字又压得住。
            Box(
                Modifier.matchParentSize().background(
                    if (isDark) Color(0xFF1B2740).copy(alpha = veilAlpha)
                    else Color.White.copy(alpha = veilAlpha)
                )
            )
            // ── ③ 内容 ──
            Box(Modifier.padding(20.dp)) {
            // ★★★2026-11-02 实测结论：弹窗里**不能**用 liquidGlassSurface，一条 30dp 的窄带也不行。
            //   试过两种尺寸（整块 0.88屏宽×0.86屏高、以及上下各 30dp 的窄带），
            //   全部崩；换成纯自绘 glassStatic 就正常。
            //   ⇒ 不是尺寸、不是内存，是 liquidGlassSurface 在**弹窗这个组合层级**里
            //     的 backdrop 采样/离屏路径有问题（底栏同样调用却没事，差别在父层级与对齐方式）。
            //   所以弹窗统一用自绘玻璃，靠「白膜 + 顶/底漫反射 + 边缘高光 + 内轮廓」
            //   把玻璃质感补齐；上下两条假的"折射亮边"由 glassStatic 内部自己画。
            if (bottomClose) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalOverlayClose provides onRequestClose,
                            LocalInOverlay provides true
                        ) { content() }
                    }
                    Spacer(Modifier.height(12.dp))
                    com.xixi.hiking.nativeapp.ui.DialogCloseBar(onDismiss = onRequestClose)
                }
            } else {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalOverlayClose provides onRequestClose,
                    LocalInOverlay provides true
                ) { content() }
            }
            }

            // ── ④ 顶部边光（玻璃的上沿反光）──
            //
            // ★2026-11-04：背景糊掉之后，弹窗上沿和背景之间缺少"一道亮线"，
            //   整块看起来像浮在雾里。真玻璃的边缘一定比中间亮（光沿侧壁入射），
            //   这里用 6% 高度的一条白→透明渐变模拟，浅色 0.55、深色 0.18。
            //   和白描边一起构成"透明框 + 高光"，与概览页 `.glass-panel` 同一套语言。
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (isDark) 0.18f else 0.55f),
                            0.06f to Color.Transparent
                        )
                    )
            )
        }
    }
}
