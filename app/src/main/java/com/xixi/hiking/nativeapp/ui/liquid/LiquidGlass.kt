package com.xixi.hiking.nativeapp.ui.liquid

// ══════════════════════════════════════════════════════════════════
//  ★★★ 铁律（用户 2026-11-02 亲自定，写在文件最顶上）
//
//  **液态玻璃不许瞎改，也不许删掉。**
//
//  这条是拿四轮返工换来的。之前为了修一个"方块"，
//  我把底栏的折射整个删了改成自绘 —— 用户的原话是「我的折射呢？？？？」。
//  正确做法永远是：**保住玻璃本身，去改造成问题的那个附加物**
//  （离屏层、阴影、裁剪方式、缩放链），绝不用降低玻璃效果来换。
// ══════════════════════════════════════════════════════════════════

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark

/**
 * ★★液态玻璃（Liquid Glass）—— 2026-11-02 换用 Kyant0/backdrop 库
 *
 * ── 为什么要换 ──────────────────────────────────────────────
 * 之前这套是手搓的：内容层用 GraphicsLayer 离屏录制，再拿 AGSL RuntimeShader 采样，
 * 坐标对齐、RenderEffect 链、三重降级全得自己算。能跑，但有两个死在根上的问题：
 *   ① 玻璃只能有一层 —— 浮层采样内容层，内容层里的卡片再采样就是递归（采样到自己）
 *   ② 每加一处玻璃都要重新调一遍折射/色散/高光参数，越调越像"磨砂塑料"
 *
 * backdrop 库（4.4k star，Compose 圈液态玻璃的事实标准）把这些做成了基础设施：
 *   · 背景 = 一个 [Backdrop] 对象（图层录制 / 纯 Canvas 绘制 / 组合）
 *   · 玻璃 = `Modifier.drawBackdrop(backdrop) { blur(); lens() }`
 *   · **可以嵌套**：玻璃自己能"导出"一份 backdrop 给里面的子级玻璃采样
 *
 * ── 三层 backdrop（这是整个视觉的地基，别乱动）──────────────
 *
 *   bgBackdrop      = CanvasBackdrop，纯背景（渐变 + 光斑）
 *                     → 页面里的卡片 / 按钮采样它。安全：不会折射到别的卡片。
 *
 *   contentBackdrop = LayerBackdrop，背景 + 全部页面内容
 *                     → 底栏、弹窗采样它。这是真·backdrop：滚到玻璃下面的记录
 *                       会被真实弯折 + 色散。
 *
 *   dialogBackdrop  = LayerBackdrop，弹窗自己导出的合成结果
 *                     → 弹窗**内部**的按钮采样它。所以按钮不会把弹窗背后的
 *                       18 行列表折进来（那才是最丑的效果）。
 *
 * ── 能力降级（库内置，不用管）────────────────────────────────
 *   · lens() 折射需要 API 33+（AGSL），低版本库内部直接 return
 *   · blur() 需要 API 31+，低版本同样 return
 *   · 都不可用时玻璃只剩 Highlight + 染色 —— 仍然是玻璃，只是没有像素级弯折
 *   · 应用侧不用写任何 if：传进去的 backdrop 为空时自动走 [emptyBackdrop]
 */
object LiquidGlass {

    /**
     * ★总开关（设置页可关）。
     * 关闭后所有玻璃走 [emptyBackdrop]：只剩染色 + 高光，不采样任何背景 —— 零风险模式。
     */
    var enabled by mutableStateOf(true)

    /** 启动时调一次：强制开启（老版本偏好里可能存过 false） */
    fun init(ctx: android.content.Context) {
        enabled = true
        try {
            com.xixi.hiking.nativeapp.data.Prefs.setBool(ctx, KEY_PREF, true)
        } catch (ignored: Throwable) {
        }
    }

    fun setEnabled(ctx: android.content.Context, v: Boolean) {
        enabled = v
        com.xixi.hiking.nativeapp.data.Prefs.setBool(ctx, KEY_PREF, v)
    }

    private const val KEY_PREF = "liquid_glass_on"

    /** 能不能做真实折射（AGSL 需要 API 33）。只用于设置页显示当前档位。 */
    val shaderSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 33

    /** 当前档位文案 —— 直接写在设置页副标题里，以后排障看这个就知道走到了哪一档 */
    fun tierLabel(): String = when {
        !enabled -> "已关闭（纯色卡片）"
        Build.VERSION.SDK_INT >= 33 -> "真实折射 + 磨砂"
        Build.VERSION.SDK_INT >= 31 -> "磨砂模糊"
        else -> "厚玻璃"
    }

    /** 玻璃当前应该采样哪个 backdrop。关掉开关或没给 backdrop 时用空 backdrop。 */
    fun resolve(backdrop: Backdrop?): Backdrop =
        if (!enabled || backdrop == null) emptyBackdrop() else backdrop

    /**
     * ★★折射 shader 在本机编不过时，永久跳过 lens（只留 blur）。
     *
     * 为什么需要这个：`lens()` 内部是 `RuntimeShader(AGSL)`，AGSL 是**设备上的编译器**
     * 现编的。同一个 shader 字符串，在不同 GPU 驱动 / 不同 Android 版本上成功率不一样：
     *   · Robolectric（JVM 上的 SkSL 编译器很老）→ cannot swizzle value of type 'shader'
     *   · 少数 OEM 的驱动 → 类似报错
     * 不兜住的话，一进页面就抛在 draw 阶段 ⇒ 整屏白。
     *
     * 用普通 @Volatile 而不是 state：它在绘制阶段写，写成 state 会「重组→重绘→再写」死循环。
     * 第一次失败后永久跳过，避免每帧都抛异常（每帧抛一次比不折射还慢）。
     */
    @Volatile
    private var lensBroken = false

    fun lensUsable(): Boolean = !lensBroken && Build.VERSION.SDK_INT >= 33

    fun markLensBroken() {
        lensBroken = true
    }

    /**
     * ★本机能不能跑「GPU 效果链」（RuntimeShader / RenderEffect）。
     *
     * 两个门槛：
     *   ① API 31+ —— RenderEffect 模糊从这里开始
     *   ② **不是软件渲染** —— RuntimeShader 要求硬件加速画布。
     *      Robolectric（JVM 上的截图测试）就是软件 Canvas，一画就抛
     *      「Software rendering doesn't support RuntimeShader」。
     *
     * 不满足时整块玻璃退回「画出来的厚玻璃」（渐变 + 描边 + 高光）：
     * 视觉降级但完全不碰 GPU，任何环境都不会崩。
     */
    /**
     * ★★安全模式开关（2026-11-02）：连续启动崩溃后由 CrashGuard 打开，
     * 全站玻璃退回静态绘制 —— 没有好看的效果了，但绝对不会再闪退。
     */
    @Volatile
    var forceStatic: Boolean = false

    val gpuEffectsUsable: Boolean
        get() = !forceStatic && !isRobolectricEnv && Build.VERSION.SDK_INT >= 31

    /** 截图测试跑在 Robolectric 上（软件画布，没有真实 GPU） */
    private val isRobolectricEnv: Boolean by lazy {
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
            Build.PRODUCT.contains("robolectric", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish")
    }
}

/**
 * 就近的 backdrop —— 玻璃从这里取"我要折射谁"。
 *
 * 层级约定：
 *   页面（内容层）里 → bgBackdrop（纯背景）
 *   弹窗内部       → 弹窗自己导出的 dialogBackdrop
 * 由 [com.xixi.hiking.nativeapp.ui.GlassOverlay] 在弹窗内部覆盖。
 */
val LocalLiquidBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * 整页内容层的 backdrop —— **只有浮层（底栏 / 弹窗）用它**。
 *
 * 为什么和 [LocalLiquidBackdrop] 分开：页面里的卡片必须采样纯背景
 * （采样内容层＝采样到自己＝递归），而浮层就活在内容层之外，正是要折射内容。
 * 一个 local 装不下两种语义，所以两个。
 */
val LocalContentBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * ★★「我现在是不是活在内容层里面」—— 2026-11-04 新增，专治弹窗自采样。
 *
 * 为什么必须知道这件事：
 *   [LiquidBackdropHost] 会把**子树**录成 contentBackdrop。
 *   而项目里有十几个弹窗是挂在页面 composable 内部的
 *   （RecordListScreen / PlansScreen / OverviewScreen / SettingsScreen …），
 *   它们本身就是那棵子树的节点 ⇒ 一旦弹窗用 contentBackdrop 做背景模糊，
 *   就是在"采样包含自己的那张纹理"：第一帧还行，之后每帧都把我自己再糊一遍，
 *   几帧后整块变成一团越来越糊的色块 —— 用户看到的就是「模糊层盖住了屏幕」。
 *
 * 所以：host 内部提供 true，弹窗遇到 true 就退回不采样（实色膜），
 * 只有画在 host **之外**的顶层弹窗才享受真模糊。
 */
val LocalInContentLayer = compositionLocalOf { false }

/**
 * 内容层宿主：把 `content` 录进 [state] 这张图层，浮层玻璃才能采样到它。
 *
 * ⚠️ 玻璃浮层必须是它的**兄弟**（画在之后），不能是子节点 ——
 *    子节点会被一起录进去，形成自我折射。
 */
@Composable
fun LiquidBackdropHost(
    state: LayerBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.layerBackdrop(state)) {
        CompositionLocalProvider(LocalInContentLayer provides true) { content() }
    }
}

/** 稳定的空 shape provider，避免每次重组都造新 lambda 把绘制缓存打掉 */
private val DefaultShadowProvider: () -> Shadow? = { Shadow.Default }
private val NoShadowProvider: () -> Shadow? = { null }
/**
 * ★边缘反射（2026-11-02 用户要求「反射再多一点」）
 *
 * `Highlight` 就是库给"镜面反光"的入口：沿轮廓描一圈带模糊的白，按角度做上下游衰减。
 *   · width      描边宽度 —— 越宽，那圈反光越"厚"，越像玻璃有棱
 *   · alpha      整体强度
 *   · intensity  光斑亮度（style 里）
 *   · falloff    衰减指数 —— 越小，光越往两侧铺开（不像一个点）
 *
 * 之前用的是 `Highlight.Default`（width 0.5dp、intensity 0.5），在浅色底上几乎看不见。
 * 现在整体加粗一倍、调亮 30%，让反光成为玻璃的主要"存在感"来源。
 */
private val DefaultHighlightProvider: () -> Highlight? = {
    // ★2026-11-02 收薄：用户说「边边一层白色圈，太厚了，浅浅一层就好」。
    //   1.2dp → 0.7dp，intensity 0.62 → 0.48，只保留"贴着轮廓的一道细亮线"。
    Highlight(
        width = 0.7.dp,
        alpha = 1f,
        style = com.kyant.backdrop.highlight.HighlightStyle.Default(
            intensity = 0.48f,
            angle = 45f,
            falloff = 0.95f
        )
    )
}
private val NoHighlightProvider: () -> Highlight? = { Highlight.Plain }

/**
 * ★液态玻璃表面（浮层材质：底栏 / 弹窗）。
 *
 * @param backdrop 采样源。传 null 或 [LiquidGlass.enabled] 关掉时自动降级。
 * @param corner   圆角 dp
 * @param refract  **折射高度（dp）**。越大越像厚玻璃；8~12 是 iOS 26 的量级，
 *                 超过 16 边缘会开始像鱼眼。
 * @param frost    **白雾浓度（0~1）**，唯一控制"背后能不能看穿"的旋钮。
 *                 弹窗正文压在上面 → 0.8 以上；底栏 0.55~0.65；装饰件 0.3。
 *                 ★中心必须保持清透：可读性靠 blur 把背景糊成色块，不靠刷白漆。
 *                 （上一版做成 0.86 的奶白，方向完全反了）
 * @param pressed  按下时整体提亮
 * @param tint     玻璃自身的颜色浸润
 * @param topHighlight 顶部镜面高光。列表里一长条玻璃可以关掉，免得像塑料膜
 * @param exported 不为空时，这块玻璃会把"自己下面的合成结果"导出到它，
 *                 供内部子级玻璃采样（弹窗里的按钮就用这条路）
 */
fun Modifier.liquidGlassSurface(
    backdrop: Backdrop?,
    corner: Int,
    refract: Float = 10f,
    pressed: Boolean = false,
    tint: Color = Color(0xFFBFD4FF),
    tintAlpha: Float = 0.10f,
    topHighlight: Boolean = true,
    frost: Float = 0.62f,
    /** 实心膜厚度（0~1）。★内容容器（卡片）必须给到 0.5 以上，否则正文压在玻璃上读不清 */
    veilAlpha: Float = -1f,
    /**
     * ★★深度效果（2026-11-02 用户：「让底栏全部折射就可以，不用分区域了」）
     *
     * 传到 AGSL 的 `depthEffect`：折射不再只发生在"边缘一圈"，
     * 而是按整块的厚度做体积弯折 —— 中间也会跟着一起弯。
     * 底栏这种细长条必须要这个，否则看起来只有上下两条边在动，中间是一片平的。
     */
    depth: Boolean = false,
    /**
     * 投影。★★2026-11-02 追加：底栏/胶囊一律传 **null**。
     *
     * `Shadow.Default` 的半径是 **24dp** —— 比底栏本身（60dp 高）的一半还大。
     * 阴影向四周扩散 24dp，在细长条上上下两片直接叠在一起，
     * 在浅色页面上糊出来就是一大块**方形**灰影，而且会跟着重绘闪。
     * 这是"方块"最可能的真凶（之前几轮我一直在动裁剪和离屏，没碰过阴影）。
     */
    showShadow: Boolean = true,
    exported: LayerBackdrop? = null,
    /** 危险操作（红底红边），对齐 Web 版 .check-go-btn */
    danger: Boolean = false,
    /**
     * 边缘高光（那圈白边）的宽度，单位 dp。
     *
     * ★2026-11-06 用户：「把底栏和底栏按钮的白色边框调细一点」。
     * 默认 0.7dp 是给弹窗/卡片的（大面积玻璃需要一点棱才立得住）；
     * 底栏和胶囊这种细长条在 0.7dp 下白边会显得比玻璃本身还抢眼，
     * 传 0.42f 收成一道发丝线。传 0f 等于关掉边缘高光。
     */
    edgeWidth: Float = 0.7f
): Modifier = composed {
    val isDark = LocalIsDark.current

    // frost 给的是模糊，veil 给的是"这层玻璃有多不透明"——两者分开：
    // 卡片需要又糊又白（正文压在上面），浮层需要糊但不白（要看得见背后的内容）。
    val veil = if (veilAlpha >= 0f) veilAlpha else (0.04f + frost * 0.22f).coerceIn(0.04f, 0.30f)

    // ★本机跑不了 GPU 效果链（软件渲染 / API<31）→ 直接画一块厚玻璃，不碰 RuntimeShader。
    //   必须早退，不能"试一试然后 catch"：RuntimeShader 的报错发生在 Canvas 绘制里，
    //   抛出来时半块玻璃已经画到屏幕上了。
    if (!LiquidGlass.gpuEffectsUsable) {
        return@composed this.staticGlassSurface(corner, isDark, veil, tint, tintAlpha, pressed)
    }

    val bd = LiquidGlass.resolve(backdrop)
    val shape: Shape = remember(corner) { RoundedCornerShape(corner.dp) }
    val shapeProvider = remember(shape) { { shape } }

    // 效果链用 remember 锁住：DrawBackdropElement.equals 比的是 lambda 引用，
    // 每次重组新建 lambda ⇒ 每次都判不等 ⇒ 绘制缓存全废（滚动会掉帧）。
    val effects: BackdropEffectScope.() -> Unit = remember(refract, frost, depth) {
        {
            // ★先 lens 再 blur：
            //   lens 会把 padding 设成折射高度（采样时要多取一圈），
            //   blur 之后只在这个外扩范围内生效，边缘不会糊出锯齿。
            // ★lens 必须包 try：AGSL 是设备现编的，编不过就永久降级成纯模糊（见 lensBroken）
            if (refract > 0f && LiquidGlass.lensUsable()) {
                try {
                    val h = refract.dp.toPx()
                    // depth 模式：折射带至少要盖住一半高度，整块才会一起弯。
                    // 底栏 60dp 高、refract 传 26dp 就是"从上到下都在折射"。
                    val band = if (depth) {
                        maxOf(h, size.height * 0.5f)
                    } else h
                    lens(
                        refractionHeight = band,
                        // ★折射量从 1.8 提到 2.8（2026-11-02「反射再多一点」）：
                        //   height 决定"边缘多宽在弯"，amount 决定"弯多狠"。
                        //   只加 height 会变成一圈模糊的过渡带；加 amount 才是真的把景往边上拽。
                        // ★系数随高度联动：refract 提到 22dp 后，2.8 倍位移≈62dp，
                        //   画面会被扯成万花筒；降到 1.5 倍≈33dp，弯得明显但不失真。
                        refractionAmount = band * (if (depth) 1.1f else 1.5f),
                        depthEffect = depth,
                        chromaticAberration = true
                    )
                } catch (t: Throwable) {
                    LiquidGlass.markLensBroken()
                }
            }
            // ★2026-11-02 第三次调：基数从 3dp 降到 1dp。
            //   用户连着三轮说"太模糊"—— 底栏是薄玻璃，模糊超过 ~2dp 就成雾了。
            //   现在 frost 只负责"加一点毛感"，真正的玻璃质感交给折射和边缘。
            // ★blur 也必须包 try：真机上 RenderEffect 的创建同样可能抛
            //   （个别 OEM 的驱动/SDK 组合），在 draw 阶段抛出来就是闪退。
            try {
                // ★半径封顶 10dp：弹窗 frost 0.8 原本算到 11.4dp，
                //   大半径 RenderEffect 在真机上很吃 GPU，也是弹窗崩溃的嫌疑之一
                val r = (1f + frost * 13f).coerceAtMost(10f)
                blur(radius = r.dp.toPx())
            } catch (t: Throwable) {
                // 没有模糊就只是少了点毛感，玻璃本体还在
            }
        }
    }

    val highlightProvider = remember(topHighlight, edgeWidth) {
        when {
            !topHighlight -> NoHighlightProvider
            // 默认宽度直接用共享实例，避免每次重组造新 lambda 打掉绘制缓存
            edgeWidth == 0.7f -> DefaultHighlightProvider
            else -> {
                // ★宽度变了要新造一个：Highlight 是不可变 data class，
                //   remember 的 key 里带上 edgeWidth 才不会读到旧值。
                //   intensity/falloff 跟默认保持一致 —— 只改粗细，不改亮度。
                val provider: () -> Highlight? = {
                    Highlight(
                        width = edgeWidth.dp,
                        alpha = 1f,
                        style = com.kyant.backdrop.highlight.HighlightStyle.Default(
                            intensity = 0.48f,
                            angle = 45f,
                            falloff = 0.95f
                        )
                    )
                }
                provider
            }
        }
    }

    this
        // ★同 chip：缩放层常驻，只改参数，避免链长度变化引起离屏层残影
        .graphicsLayer {
            val s = if (pressed) 0.985f else 1f
            scaleX = s
            scaleY = s
        }
        // ★★★这里**不要**再套 Modifier.clip(shape)（2026-11-02 第六次修闪退，这次是弹窗）
        //   弹窗的玻璃本来就已经有两层离屏：drawBackdrop 自己的 placeWithLayer(Offscreen)
        //   + exportedBackdrop 的 recordLayer。我再加 .clip() 就是**第三层**，
        //   叠上 frost 0.8 时 11dp 的大半径 blur RenderEffect，真机 GPU 直接崩。
        //   表现就是：点任何带弹窗的选项 → 弹窗还没显示出来 → 闪退。
        //   库自己的 placeWithLayer 已经按 shape 裁过了，这层 clip 从头到尾是多余的。
        .drawBackdrop(
        backdrop = bd,
        shape = shapeProvider,
        effects = effects,
        highlight = highlightProvider,
        shadow = if (showShadow) DefaultShadowProvider else NoShadowProvider,
        // ★innerShadow 也关掉：它走「独立 GraphicsLayer + 自绘 outline」，
        //   是又一层离屏。厚度感交给 onDrawSurface 里自绘的内壁反光。
        innerShadow = null,
        exportedBackdrop = exported,
        layerBlock = null,
        onDrawSurface = {
            // ★玻璃自身的膜：浅色压白，深色压深蓝黑。
            //   iOS 26 的浮层玻璃中心接近清透，存在感来自折射和边缘，不是白雾；
            //   但内容容器（卡片）反过来 —— 正文直接读，必须靠这层膜顶住。
            drawRect(
                color = if (isDark) Color(0xFF0B1220).copy(alpha = veil)
                else Color.White.copy(alpha = veil)
            )
            // 强调色浸润：薄薄一层，让玻璃带一点冷调
            if (tintAlpha > 0f) {
                drawRect(color = tint.copy(alpha = tintAlpha * 0.55f))
            }
            // 按下的整体提亮（剩下的交给 layerBlock 的轻微缩放）
            if (pressed) {
                drawRect(color = Color.White.copy(alpha = if (isDark) 0.05f else 0.10f))
            }

            // ══ 内壁反光（2026-11-02「反射再多一点」）══════════════
            //   真实玻璃除了外沿的镜面高光，还有一圈**从边缘往内衰减**的亮度：
            //   光在玻璃内部反复反射，贴着侧壁最亮。只靠库的 Highlight（贴外沿一圈）
            //   会显得单薄，这一层补上"厚度"。
            //   用 5dp 宽的描边做出"外亮 → 中空 → 底部微亮"的三段渐变，
            //   光从上方来，所以顶部最亮、底部留一点地面反射。
            val cr = androidx.compose.ui.geometry.CornerRadius(corner.dp.toPx(), corner.dp.toPx())
            // ★顶部要比底部亮一档（用户问「上顶边为什么没有反射」）：
            //   光从上方来，玻璃的上沿是**整条漫反射带**，下沿只有地面反光。
            //   之前顶 0.20 / 底 0.13 差得太小，加上 Highlight 的加法混合把顶部冲白，
            //   看起来就像"只有下边有光"。现在顶部拉到 0.30，差距才看得出来。
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = if (isDark) 0.18f else 0.30f),
                    0.22f to Color.White.copy(alpha = if (isDark) 0.05f else 0.09f),
                    0.72f to Color.White.copy(alpha = if (isDark) 0.015f else 0.025f),
                    1.00f to Color.White.copy(alpha = if (isDark) 0.07f else 0.11f)
                ),
                cornerRadius = cr,
                // ★1.5dp：一条边就够，粗了就成白环
                style = Stroke(width = 1.5.dp.toPx())
            )
            // 最贴边的一条细亮线：玻璃的"切口"。1dp + 顶亮底弱，够定形就行
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = if (isDark) 0.30f else 0.52f),
                    0.5f to Color.White.copy(alpha = if (isDark) 0.05f else 0.09f),
                    1.0f to Color.White.copy(alpha = if (isDark) 0.12f else 0.20f)
                ),
                cornerRadius = cr,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    )
}

/**
 * ★★自绘玻璃条（**底栏专用，无离屏层**）—— 2026-11-02
 *
 * 为什么底栏也放弃 backdrop 库：
 *   指示块改自绘之后方块**还在**，这就锁定了唯一剩下的离屏层 —— 底栏本体自己。
 *   `drawBackdrop` 每条玻璃都要 createGraphicsLayer + 离屏录制；底栏是常驻节点，
 *   tab 切换/按下提亮都会让这层重录，旧位图清不干净就是那个方块。
 *   底栏是整屏最显眼的一块，宁可不要真实折射，也不能带方块。
 *
 * 代价与取舍：底栏不折射背后的内容了。但用户要的是"反射"，不是"折射" ——
 *   反射（高光、亮带、边缘光）完全可以自己画，而且画得比 shader 更可控。
 *   **弹窗仍然走真折射**（它不移动、不常驻重绘，且需要真模糊压住正文）。
 *
 * 光的三层（从上到下）：
 *   ① 顶部漫反射带 —— 光从上方来，上沿整条是亮的
 *   ② 中间镜面高光 —— 一条横向柔和亮带，这是"玻璃有弧面"的暗示（用户第 3 点要的）
 *   ③ 底部地面反光 —— 很淡，托住下沿
 */
fun Modifier.glassBar(
    corner: Int = 26,
    pressed: Boolean = false
): Modifier = composed {
    val isDark = LocalDark()
    val shape = remember(corner) { RoundedCornerShape(corner.dp) }
    this
        // 缩放层常驻（链结构不变）
        .graphicsLayer {
            val s = if (pressed) 0.985f else 1f
            scaleX = s
            scaleY = s
        }
        .clip(shape)
        .drawWithContent {
            val cr = androidx.compose.ui.geometry.CornerRadius(corner.dp.toPx(), corner.dp.toPx())

            // ① 本体：近乎清水，只有一点点膜
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = if (isDark) 0.09f else 0.13f),
                    0.50f to Color.White.copy(alpha = if (isDark) 0.04f else 0.06f),
                    1.00f to Color.White.copy(alpha = if (isDark) 0.06f else 0.10f)
                ),
                cornerRadius = cr
            )

            // ② 顶部漫反射带：占上部 45%，从 0.24 衰减到透明
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = if (isDark) 0.20f else 0.34f),
                    0.16f to Color.White.copy(alpha = if (isDark) 0.08f else 0.14f),
                    0.45f to Color.Transparent
                ),
                cornerRadius = cr
            )

            // ③ ★中间镜面高光（用户：「底栏中间怎么没有反射？加上」）
            //   一条横向的柔和亮带，位置在上部 1/4 处 —— 真实玻璃条的弧面反光就落在那儿。
            //   用很窄的渐变（0.18→0.30）做，宽了就成一条白杠。
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.17f to Color.Transparent,
                    0.235f to Color.White.copy(alpha = if (isDark) 0.10f else 0.17f),
                    0.30f to Color.Transparent
                ),
                cornerRadius = cr
            )

            // ④ 底部地面反光
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.76f to Color.Transparent,
                    1.00f to Color.White.copy(alpha = if (isDark) 0.07f else 0.13f)
                ),
                cornerRadius = cr
            )

            // ⑤ 外沿 1dp 细线（顶亮底弱）
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = if (isDark) 0.26f else 0.46f),
                    0.50f to Color.White.copy(alpha = if (isDark) 0.06f else 0.10f),
                    1.00f to Color.White.copy(alpha = if (isDark) 0.12f else 0.22f)
                ),
                cornerRadius = cr,
                style = Stroke(width = 1.dp.toPx())
            )
            // ⑤b 轮廓暗线：同 glassPill，浅背景上必须有才能看出"这是一条浮起来的玻璃"
            drawRoundRect(
                color = Color.Black.copy(alpha = if (isDark) 0.20f else 0.055f),
                cornerRadius = cr,
                style = Stroke(width = 1.dp.toPx())
            )

            drawContent()
        }
}

/** 读当前深浅色（glassBar 在 composed 里用，抽出来免得每次都写全名） */
@Composable
private fun LocalDark(): Boolean = LocalIsDark.current

/**
 * ★★自绘玻璃胶囊（**不走 backdrop 库，没有任何离屏层**）—— 2026-11-02
 *
 * 为什么单独做这一个：底栏的选中指示块是**会移动**的元素（长按可拖动）。
 * 之前它用 `drawBackdrop`，每次绘制都要 createGraphicsLayer + 离屏录制：
 *   · 移动时旧位置的离屏层来不及清 → 屏幕上留下"上一格 + 当前格"两个方块
 *   · 白边来自库内部的 Highlight shader，宽度/亮度在外部调不到位 → 一直"太厚"
 * 换成纯 Canvas 绘制后，两个问题都不存在：没有离屏层就没有残影，
 * 白边是几 dp 就是我写死的几 dp。
 *
 * ⚠️ 代价：这一块没有真实的背景折射。但它尺寸只有 ~48×60dp，而且就在底栏玻璃里，
 *    缺折射几乎看不出来 —— 换来"绝对不出方块"是划算的。
 *    底栏本体、弹窗这些**不移动**的大块仍然走真折射。
 *
 * @param pressed 按下凸显：放大 4% + 上浮 1.5dp + 整体提亮
 */
fun Modifier.glassPill(
    pressed: Boolean = false,
    /**
     * 按压进度 0..1（不是布尔 —— 官方示例用的是连续进度，
     * 这样放大是"逐渐鼓起来"而不是突然变大一档）
     */
    pressProgress: Float = if (pressed) 1f else 0f,
    /**
     * 拖动速度（px/s）。用来做 squash & stretch：
     * 快滑时横向拉长、纵向压扁，松手再弹回 —— 这就是"灵动感"的来源。
     * 抄自 Kyant0/AndroidLiquidGlass 的 LiquidBottomTabs。
     */
    velocity: Float = 0f
): Modifier = composed {
    val isDark = LocalIsDark.current
    this
        // 缩放层常驻（只改参数，链结构不变 —— 这是不出残影的前提）
        .graphicsLayer {
            // ★★官方配方（LiquidBottomTabs）：
            //   按下放大到 1.39 倍（不是 1.04 —— 那样太拘谨，看不出"被捏起来"）
            val base = 1f + 0.39f * pressProgress
            //   速度形变：横向随速度拉伸、纵向反向压扁，松手回弹
            val v = velocity / 10f
            scaleX = base / (1f - (v * 0.75f).coerceIn(-0.2f, 0.2f))
            scaleY = base * (1f - (v * 0.25f).coerceIn(-0.2f, 0.2f))
            translationY = -1.5.dp.toPx() * pressProgress
        }
        // ★★★这里**绝对不能**用 Modifier.clip(shape)（2026-11-02 方块真凶）
        //   `Modifier.clip` 底层就是 `graphicsLayer { clip = true; shape = ... }` ——
        //   它会创建一个**矩形边界的离屏层**。父节点（底栏）本身也有离屏玻璃，
        //   两个离屏层叠在一起时，这个矩形里没被画到的空白会被合成出一层灰底，
        //   表现就是"椭圆按钮背后有一块灰色方块"。
        //   改成绘制阶段的 clipPath：纯 Canvas 指令，**零离屏层**。
        .drawWithContent {
            val w = size.width
            val h = size.height
            // ★尺寸保护：布局还没完成时（宽/高 ≤ 0）直接跳过，别去构造 Path ——
            //   RoundRect/Path 在极端尺寸下会抛，而 draw 阶段抛异常 = 直接闪退。
            if (w > 0.5f && h > 0.5f) {
                val cr = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
                val outline = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, cr)
                    )
                }
                // ★★只裁"玻璃本身"，**不裁 drawContent()**（2026-11-02 修闪退）：
                //   把 drawContent() 塞进 clipPath 的 lambda 里，等于在裁剪块内部
                //   触发内容绘制 —— ContentDrawScope 对"何时能调 drawContent"是有
                //   状态机约束的，真机的绘制线程上会抛异常直接闪退。
                //   胶囊里的图标/文字本来就在中间，根本不需要裁。
                clipPath(outline) {
                    drawPillGlass(isDark, pressed)
                }
            }
            // 内容永远在裁剪之外绘制
            this@drawWithContent.drawContent()
        }
}

/** 胶囊玻璃的实际画法（被 [glassPill] 的 clipPath 包着，不可能溢出） */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPillGlass(
    isDark: Boolean,
    pressed: Boolean
) {
    val h = size.height
    val cr = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)

    // ① 本体：★比上一版提亮一档（用户：底栏透是好事，但里头那颗胶囊快看不见了）
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (isDark) 0.16f else 0.24f),
            0.5f to Color.White.copy(alpha = if (isDark) 0.07f else 0.12f),
            1f to Color.White.copy(alpha = if (isDark) 0.10f else 0.18f)
        ),
        cornerRadius = cr
    )

    // ② ★★顶部反射（用户问「上顶边为什么没有反射」）
    //   这是一条**填充**的亮带，不是描边 —— 描边只有 1px 的线，
    //   而真实的玻璃上沿是一条有宽度的漫反射带（光从上方来，顶面最亮）。
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = if (isDark) 0.30f else 0.60f),
            0.14f to Color.White.copy(alpha = if (isDark) 0.09f else 0.18f),
            0.40f to Color.Transparent
        ),
        cornerRadius = cr
    )

    // ③ 外沿：只有 1dp 的一道线，顶亮底弱 —— 这就是"浅浅一层"
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = if (isDark) 0.36f else 0.70f),
            0.50f to Color.White.copy(alpha = if (isDark) 0.10f else 0.20f),
            1.00f to Color.White.copy(alpha = if (isDark) 0.14f else 0.28f)
        ),
        cornerRadius = cr,
        style = Stroke(width = 1.dp.toPx())
    )

    // ③b ★轮廓暗线：白玻璃压在**白背景**上，光靠白线是没有边界的
    drawRoundRect(
        color = Color.Black.copy(alpha = if (isDark) 0.22f else 0.07f),
        cornerRadius = cr,
        style = Stroke(width = 1.dp.toPx())
    )

    // ④ 底部内反光：地面反射，很淡的一条
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.72f to Color.Transparent,
            1.00f to Color.White.copy(alpha = if (isDark) 0.12f else 0.24f)
        ),
        cornerRadius = cr
    )

    // ⑤ 按下提亮
    if (pressed) {
        drawRect(color = Color.White.copy(alpha = if (isDark) 0.06f else 0.12f))
    }

    // ⑥ ★按压高光（对标官方的 InteractiveHighlight）：
    //    按下时在胶囊中上部打一圈径向柔光，像手指压上去把玻璃"点亮"了一块。
    //    用径向渐变而不是纯色提亮 —— 纯色会让整块变匀，没有"局部被照亮"的感觉。
    if (pressed) {
        val glowAlpha = if (isDark) 0.22f else 0.34f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = glowAlpha),
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.35f),
                radius = size.height * 1.1f
            ),
            radius = size.height * 1.1f,
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.35f)
        )
    }
}

/**
 * ★★轻量玻璃（纯 Canvas 自绘，**零 GPU shader**）—— 2026-11-02
 *
 * 为什么需要第三个玻璃实现：
 *   设置页同屏有 **10 个玻璃按钮**（6 个网格 + 4 个关于），每个 `liquidGlassChip`
 *   都要跑一次 `drawBackdrop` + `lens`（AGSL runtime shader）+ 一层离屏。
 *   底栏、弹窗各自只有 1 个，撑得住；10 个同时渲染就是 10 个 shader 实例 +
 *   10 层离屏，真机 GPU 直接崩 —— 这正是「设置页一点就闪退」的真因。
 *
 * 这一版只画渐变 + 棱 + 高光：**没有任何 shader、没有离屏层**，
 * 观感和静态档的玻璃几乎一样（本来就是同一套画法），成本接近画一个矩形。
 *
 * 大卡片（glassPanel）仍然用真 backdrop —— 一层还是撑得住的，
 * 而且那是设置页唯一的"真玻璃"质感来源。
 */
fun Modifier.glassStatic(
    corner: Int = 12,
    pressed: Boolean = false,
    /** 白膜厚度：弹窗要压正文，必须给够（0.4 以上）；按钮给 0 即可 */
    veil: Float = 0f,
    /**
     * ★平面模式（2026-11-02 用户：「弹窗先弄成平面的吧，现在这个虚假立体感不好看」）
     *
     * true 时只画一层填充 + 一条极细的边，**去掉**顶部漫反射带、内壁棱、轮廓暗线、按下提亮。
     * 之前那些层次叠在一起，在弹窗这么大的面积上会显得"鼓起来一块"，很假。
     * 平面模式就是干净的一张半透明卡片 —— 该有的边界有，不该有的光影全不要。
     */
    flat: Boolean = false,
    /**
     * 危险操作（2026-11-02，对齐 Web 版 `.check-go-btn`）：
     * 极淡红底 + 0.9 红边框 + 红字 —— 这是 Web 版区分"重要性"的唯一手段，
     * 用在「抹掉所有足迹」「删除」「清空」这类不可恢复的操作上。
     */
    danger: Boolean = false,
    /**
     * 危险 + 实心：对齐 Web `.confirm-btn-delete`
     * （rgba(239,68,68,0.85) 实心红 + 白字）。用于「确认删除」这类**最终**破坏性确认。
     * 普通行内删除仍用 danger（淡红底 + 红边 + 红字 = Web `.check-go-btn`）。
     */
    solidDanger: Boolean = false,
    /**
     * 正向操作（完成 / 恢复 / 确认）：淡绿底 + 绿边。
     * 对应 Web 版计划详情里「完成了 ✓」那套绿色系（rgba(22,163,74)）。
     */
    positive: Boolean = false
): Modifier = composed {
    val isDark = LocalIsDark.current
    this
        .graphicsLayer {
            val s = if (pressed) 0.97f else 1f
            scaleX = s
            scaleY = s
        }
        .drawWithContent {
            val w = size.width
            val h = size.height
            if (w > 0.5f && h > 0.5f) {
                val cr = androidx.compose.ui.geometry.CornerRadius(corner.dp.toPx(), corner.dp.toPx())
                val outline = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, cr))
                }
                clipPath(outline) {
                    if (flat) {
                        // ── 平面模式：一层填充而已 ──────────────────
                        // 不透明度给得比玻璃高（0.82/0.88）：用户要"背景再模糊一点"，
                        // 但自绘没法真模糊，只能用更实的底色把背后的字压下去，
                        // 观感上等同于"糊住了"。
                        if (solidDanger) {
                            // 实心红：Web `.confirm-btn-delete`
                            //   background: rgba(239,68,68,0.85); border: rgba(239,68,68,0.95); color: #fff
                            drawRoundRect(
                                color = if (isDark) Color(0xFFDC2626).copy(alpha = 0.90f)
                                else Color(0xFFEF4444).copy(alpha = 0.85f),
                                cornerRadius = cr
                            )
                        } else if (danger) {
                            // 行内危险：极淡红底（Web `.check-go-btn` = rgba(254,226,226,0.08) + 0.9 红边）
                            drawRoundRect(
                                color = if (isDark) Color(0xFF460F14).copy(alpha = 0.20f)
                                else Color(0xFFFEE2E2).copy(alpha = 0.45f),
                                cornerRadius = cr
                            )
                        } else if (positive) {
                            // 正向：Web 「完成了 ✓」= rgba(22,163,74,0.16) 底 + rgba(22,163,74,0.5) 边
                            drawRoundRect(
                                color = if (isDark) Color(0xFF052E16).copy(alpha = 0.30f)
                                else Color(0xFF16A34A).copy(alpha = 0.16f),
                                cornerRadius = cr
                            )
                        } else {
                            // ★★★2026-11-03 重大修正（用户三次说「按钮/弹窗没按 Web 来、没有通透感」）
                            //
                            //   真因就在这两行：原来是 **base 0.86 + veil×0.14** ——
                            //   也就是说不管 veil 传多少，底色永远是 **86%~91% 的白**，
                            //   一块实心白板。Web 的 `.glass-btn` 是 rgba(255,255,255,0.10)，
                            //   `.confirm-modal-content` 才 0.15 —— 差了一个数量级。
                            //
                            //   现在 **veil 直接当不透明度用**：传 0.10 就是 10% 白，
                            //   背景的光斑、卡片、文字都能透上来，这才叫玻璃。
                            //   （浅色模式下页面底色本来就接近白，0.10~0.16 的薄膜压得住文字；
                            //     深色模式同理，0.18 的深蓝膜也够）
                            val fill = veil.coerceIn(0f, 0.95f)
                            drawRoundRect(
                                color = if (isDark) Color(0xFF182234).copy(alpha = fill)
                                else Color.White.copy(alpha = fill),
                                cornerRadius = cr
                            )
                        }
                        // 边线：危险操作用 0.9 的红边（Web 版就是这个浓度），普通用中性边
                        //
                        // ★★2026-11-04 用户：「所有的按钮，都加上点描边，现在描边太浅了」
                        //   原来普通按钮是 `0x0F172A @ 0.07`（7% 墨色）—— 在浅色页面/浅色弹窗上
                        //   基本等于没有边，一整排按钮看起来就是几团白雾（尤其「关闭」「取消」这种
                        //   纯中性按钮，跟背景糊在一起）。
                        //   现在给到 0.20（浅）/ 0.22（深），仍是 1dp 细线、不抢戏，但边界清清楚楚。
                        drawRoundRect(
                            color = when {
                                solidDanger -> Color(0xFFEF4444).copy(alpha = 0.95f)
                                danger && isDark -> Color(0xFFF87171).copy(alpha = 0.90f)
                                danger -> Color(0xFFDC2626).copy(alpha = 0.90f)
                                positive && isDark -> Color(0xFF4ADE80).copy(alpha = 0.55f)
                                positive -> Color(0xFF16A34A).copy(alpha = 0.50f)
                                // ★2026-11-04 第二次加深（用户：「边框颜色可以再深一点」）
                                isDark -> Color.White.copy(alpha = 0.34f)
                                else -> Color(0xFF0F172A).copy(alpha = 0.30f)
                            },
                            cornerRadius = cr,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        if (pressed) {
                            drawRect(color = Color.White.copy(alpha = 0.10f))
                        }
                        return@clipPath
                    }

                    // 本体：上亮下暗
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (isDark) 0.10f else 0.22f),
                            0.5f to Color.White.copy(alpha = if (isDark) 0.04f else 0.09f),
                            1f to Color.White.copy(alpha = if (isDark) 0.06f else 0.14f)
                        ),
                        cornerRadius = cr
                    )
                    // 顶部漫反射带
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (isDark) 0.20f else 0.42f),
                            0.35f to Color.Transparent
                        ),
                        cornerRadius = cr
                    )
                    // 外沿 1dp
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (isDark) 0.24f else 0.55f),
                            1f to Color.White.copy(alpha = if (isDark) 0.10f else 0.24f)
                        ),
                        cornerRadius = cr,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    // 轮廓暗线：白玻璃压白背景要靠它定形
                    drawRoundRect(
                        color = Color.Black.copy(alpha = if (isDark) 0.16f else 0.08f),
                        cornerRadius = cr,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    // 白膜：弹窗必须靠它顶住正文（背后内容会清晰透出来，没有模糊兜底）
                    if (veil > 0f) {
                        drawRoundRect(
                            color = if (isDark) Color(0xFF0B1220).copy(alpha = veil)
                            else Color.White.copy(alpha = veil),
                            cornerRadius = cr
                        )
                    }
                    // 按下提亮
                    if (pressed) {
                        drawRect(color = Color.White.copy(alpha = if (isDark) 0.06f else 0.12f))
                    }
                }
            }
            this@drawWithContent.drawContent()
        }
}

/**
 * 静态厚玻璃 —— 本机跑不了 GPU 效果链时的兜底（软件渲染 / API 30 及以下）。
 *
 * 没有模糊可用时，半透白是顶不住的：背后的字会变成「淡淡的但清清楚楚的影子」，
 * 比全透还难看（字形完整、对比度低）。所以直接做到近实心（≥0.92），
 * 玻璃感全部交给棱、高光、色散去表达 —— 宁可「厚玻璃」，也不要「糊字」。
 */
private fun Modifier.staticGlassSurface(
    corner: Int,
    dark: Boolean,
    veil: Float,
    tint: Color,
    tintAlpha: Float,
    pressed: Boolean
): Modifier {
    val shape = RoundedCornerShape(corner.dp)
    return this
        .shadow(
            elevation = 10.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.30f else 0.05f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.40f else 0.09f)
        )
        .clip(shape)
        .drawWithContent {
            drawGlassFallback(
                cornerPx = corner.dp.toPx(),
                dark = dark,
                // ★0.96 起：实测 0.92 时背后列表的字还是「淡淡的但清清楚楚」，
                //   比全透更难看（字形完整、对比度低）。没有模糊就只能压到接近实心。
                alpha = veil.coerceIn(0.96f, 0.98f)
            )
            if (tintAlpha > 0f) {
                drawRect(color = tint.copy(alpha = tintAlpha * 0.5f))
            }
            if (pressed) {
                drawRect(color = Color.White.copy(alpha = if (dark) 0.06f else 0.12f))
            }
            drawContent()
        }
}

// ★默认高光已统一在上面那份「加强版」（width 1.2dp / intensity 0.62），这里不再重复声明。


/**
 * ★按钮 / 小件液态玻璃。
 *
 * 用 [LocalLiquidBackdrop] 就近取样：
 *   · 页面里的按钮 → 采样 bgBackdrop（纯背景），折射出背景的颜色，不串内容
 *   · 弹窗里的按钮 → 采样弹窗导出的 dialogBackdrop（见 GlassOverlay）
 *
 * 尺寸小，所以折射高度和模糊半径都按控件高度缩放 —— 6dp 的棱放在 40dp 的按钮上
 * 会直接把按钮糊死（这个坑在自研版踩过）。
 *
 * @param tint    强调色浅染（主操作传 AccentIndigo，危险操作传红）
 * @param strong  主操作：染色更重、棱更亮
 * @param flat    不要投影。列表行里的小圆点按钮用 —— 十几个投影叠在一起会显脏
 * @param pill    **胶囊形**（圆角 = 高度的 50%，两端是正半圆）。
 *                底栏选中态必须用这个：底栏本身 26dp 圆角在 ~58dp 高的条上已经是胶囊端，
 *                选中块如果是 12dp 圆角矩形，形状跟容器对不上，看着就是「一块方的」。
 */
fun Modifier.liquidGlassChip(
    corner: Int = com.xixi.hiking.nativeapp.ui.theme.Radii.BUTTON,
    tint: Color? = null,
    strong: Boolean = false,
    pressed: Boolean = false,
    enabled: Boolean = true,
    flat: Boolean = false,
    pill: Boolean = false,
    /** 危险操作：极淡红底 + 0.9 红边（对齐 Web 版 .check-go-btn） */
    danger: Boolean = false,
    /** 危险 + 实心红 + 白字（Web .confirm-btn-delete，用于最终确认） */
    solidDanger: Boolean = false,
    /** 正向操作：淡绿底 + 绿边（对齐 Web 版计划详情「完成了 ✓」） */
    positive: Boolean = false
): Modifier = composed {

    // ══════════════════════════════════════════════════════════════
    //  ★★★全站按钮平面化（2026-11-02 用户：「所有的按钮也换为平面，
    //     所有设计语言统一为平面」）
    //
    //  原来的玻璃按钮是 backdrop 采样 + 折射 + 多层光影；现在整站统一成
    //  **一张实的圆角面 + 一条细边**，按下时只做轻微缩放和亮度变化。
    //
    //  ★底栏那颗胶囊不走这里（它是 glassPill），所以底栏的液态玻璃不受影响。
    //  ★原有参数全部保留（调用点一行都不用改），只是内部不再走 GPU 玻璃。
    // ══════════════════════════════════════════════════════════════
    // ★★★2026-11-03 全部改成 Web 量级（原来 0.30/0.55 配上旧的 base 0.86，出来是白板）
    //   Web `.glass-btn` = rgba(255,255,255,0.10)，hover 0.16，按下略提亮。
    val flatVeil = when {
        !enabled -> 0.06f
        pressed -> 0.20f
        strong -> 0.16f
        else -> 0.10f
    }
    return@composed this.glassStatic(
        corner = if (pill) 999 else corner,
        pressed = pressed,
        veil = flatVeil,
        flat = true,
        danger = danger,
        solidDanger = solidDanger,
        positive = positive
    )
    val isDark = LocalIsDark.current

    // 同 surface：本机没有 GPU 效果链就直接画静态玻璃，别碰 RuntimeShader
    if (!LiquidGlass.gpuEffectsUsable) {
        return@composed this.chipStaticGlass(corner, isDark, tint, strong, pressed, enabled, flat, pill)
    }

    val bd = LiquidGlass.resolve(LocalLiquidBackdrop.current)
    val shape: Shape = remember(corner, pill) {
        if (pill) RoundedCornerShape(percent = 50) else RoundedCornerShape(corner.dp)
    }
    val shapeProvider = remember(shape) { { shape } }

    val effects: BackdropEffectScope.() -> Unit = remember(strong) {
        {
            // 按钮矮：折射高度压到 4~6dp，再多就成鱼眼了
            if (LiquidGlass.lensUsable()) {
                try {
                    val h = (if (strong) 6f else 5f).dp.toPx()
                    lens(
                        refractionHeight = h,
                        // 同 surface：把"弯多狠"提上去，按钮边缘才有玻璃珠的效果
                        refractionAmount = h * 2.4f,
                        chromaticAberration = true
                    )
                } catch (t: Throwable) {
                    LiquidGlass.markLensBroken()
                }
            }
            // ★模糊压得很轻：按钮/胶囊是小面积，糊重了会变成一团雾贴在上面，
            //   反而看不出是玻璃（用户原话「现在还是太模糊了」）
            try {
                blur(radius = (if (strong) 4f else 3f).dp.toPx())
            } catch (t: Throwable) {
            }
        }
    }

    val highlightProvider = remember(strong, pressed) {
        // 按下时高光拉满：玻璃「凸显」的一半靠这个（另一半是 scale + 提亮）
        if (strong || pressed) strongHighlightProvider else DefaultHighlightProvider
    }
    val shadowProvider: () -> Shadow? = remember(strong, flat, isDark) {
        val s: Shadow? = when {
            flat -> null
            strong -> Shadow(
                radius = 6.dp,
                color = Color.Black.copy(alpha = if (isDark) 0.30f else 0.14f)
            )
            else -> Shadow(
                radius = 3.dp,
                color = Color.Black.copy(alpha = if (isDark) 0.24f else 0.08f)
            )
        }
        // ★永远返回一个 provider（flat 时它内部返回 null）。
        //   不能整个返回 null：remember 的 T 是非空的，返回 null 会让类型推断成 Nothing?
        { s }
    }

    this
        // ★★★方块的真正根因（2026-11-02 用户第三次反馈，这次治了根）：
        //   按下缩放原来是通过 drawBackdrop 的 `layerBlock` 传的 —— 那个参数为 null 时
        //   根本不挂 graphicsLayer，于是 Modifier 链在「按下多一层 / 松手少一层」之间来回变。
        //   链一变，节点就要重建渲染层，旧位置那块**矩形离屏层**没被及时清掉 ⇒
        //   屏幕上留下"当前按钮 + 上一个按钮"两个方块。
        //   修法：这一层 graphicsLayer **常驻**（永远在链上），按下时只改它的 scale 参数。
        //   链结构从此恒定，不再重建 → 方块消失。
        .graphicsLayer {
            val s = if (pressed) 1.06f else 1f
            scaleX = s
            scaleY = s
            translationY = if (pressed) -2.dp.toPx() else 0f
        }
        // ★同 surface：库不按 shape 裁，onDrawSurface 的 drawRect 是画满矩形的。
        //   不裁就露方角（切换时尤其明显）。
        .clip(shape)
        .drawBackdrop(
        backdrop = bd,
        shape = shapeProvider,
        effects = effects,
        highlight = highlightProvider,
        shadow = shadowProvider,
        // ★内壁阴影关掉（2026-11-02 修方块）：
        //   InnerShadow 走的是「独立 GraphicsLayer + 自绘 outline」，又多加一层离屏。
        //   "第二层玻璃"的厚度感改由 onDrawSurface 里内缩那一圈棱自己画 ——
        //   那一笔在 shape 裁剪之内，绝对不会露方角。
        innerShadow = null,
        // ★缩放已经挪到上面那层常驻 graphicsLayer 了，这里不再传 layerBlock
        //   （传了就又会出现"链长度变化"的问题）。
        layerBlock = null,
        onDrawSurface = {
            // ★本体要"薄"：玻璃的厚度靠边缘折射 + 棱 + 内壁，不靠一块白。
            //   用户连着两轮说"还不够透明"，所以这里一路压到 0.10 —— 几乎是清水。
            val a = when {
                pressed -> 0.18f   // 凸显：稍微亮一点就够了，亮太多就成白片
                strong -> 0.10f
                else -> 0.08f
            } * (if (enabled) 1f else 0.55f)
            // 本体：上亮下暗 = 有厚度
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to (if (isDark) Color(0xFF3A4A66) else Color.White).copy(alpha = a + 0.06f),
                    0.5f to (if (isDark) Color(0xFF3A4A66) else Color.White).copy(alpha = a),
                    1f to (if (isDark) Color(0xFF223047) else Color(0xFFDDE6F5)).copy(alpha = a + 0.04f)
                )
            )
            // 强调色浸润（选中态不传 tint = 纯中性玻璃色）
            if (tint != null) {
                val ta = (if (strong) 0.14f else 0.08f) *
                    (if (pressed) 1.35f else 1f) *
                    (if (enabled) 1f else 0.5f)
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to tint.copy(alpha = ta * 0.5f),
                        1f to tint.copy(alpha = ta)
                    )
                )
            }

            // ★★轮廓暗线（2026-11-02）：白玻璃压在**白色弹窗**上会整块隐身 ——
            //   只剩一圈极淡的白边，看起来像"没有按钮"（本地备份弹窗里的
            //   「现在备份一份」就是这个毛病）。
            //   加一道 1dp 的淡暗轮廓把形状"扣"出来，白描边才像高光。
            val outlineR = androidx.compose.ui.geometry.CornerRadius(
                if (pill) size.height / 2f else corner.dp.toPx(),
                if (pill) size.height / 2f else corner.dp.toPx()
            )
            drawRoundRect(
                color = Color.Black.copy(
                    // 深色主题下 0.24 会把轮廓压成一条亮白线（暗底上的暗线反而显眼），收到 0.16
                    alpha = (if (isDark) 0.16f else 0.09f) * (if (enabled) 1f else 0.5f)
                ),
                cornerRadius = outlineR,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )

            // ══ 第二层玻璃 ═══════════════════════════════════════
            //   内缩 2dp 再画一个小一圈的胶囊棱（亮） + 更里面一道极淡的反光。
            //   这两笔就是"玻璃叠玻璃"的观感来源：看得到里面还有一层轮廓。
            val h = size.height
            val inset = (2f * density).coerceAtMost(h * 0.16f)
            if (inset > 0f && size.width > inset * 2.5f) {
                val w = size.width - inset * 2f
                val ih = h - inset * 2f
                // 内层形状跟外层一致：胶囊 or 圆角
                val ir = if (pill) ih / 2f else (corner.dp.toPx() - inset).coerceAtLeast(0f)
                val irc = androidx.compose.ui.geometry.CornerRadius(ir, ir)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                val isz = androidx.compose.ui.geometry.Size(w, ih)
                // ① 内层棱：上沿更亮，下沿保持一点（玻璃有朝向）
                //    ★亮度收到 0.34：原来 0.62 会和外面两道反光叠成三圈白边
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (isDark) 0.14f else 0.34f),
                        0.45f to Color.White.copy(alpha = if (isDark) 0.04f else 0.09f),
                        1f to Color.White.copy(alpha = if (isDark) 0.06f else 0.15f)
                    ),
                    topLeft = tl,
                    size = isz,
                    cornerRadius = irc,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f * density)
                )
                // ② 内层膜的极淡反光：越往里越透明，做出"厚度"
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (isDark) 0.05f else 0.14f),
                        0.5f to Color.Transparent
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(w, ih * 0.55f),
                    cornerRadius = irc
                )
            }

            // ══ 外沿的内壁反光（2026-11-02「反射再多一点」）══════════
            //   按钮小，所以只做一圈 2.5dp 的渐变描边：顶部最亮、底部留一点地面反射。
            //   这一笔 + 库的 Highlight（贴最外沿）叠起来，边缘才有"厚玻璃棱"的双层反光。
            val or = androidx.compose.ui.geometry.CornerRadius(
                if (pill) size.height / 2f else corner.dp.toPx(),
                if (pill) size.height / 2f else corner.dp.toPx()
            )
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = if (isDark) 0.14f else 0.26f),
                    0.35f to Color.White.copy(alpha = if (isDark) 0.03f else 0.06f),
                    1.00f to Color.White.copy(alpha = if (isDark) 0.08f else 0.14f)
                ),
                cornerRadius = or,
                // ★1.5dp：小按钮上再厚就成白圈了
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density)
            )
        }
    )
}

private val strongHighlightProvider: () -> Highlight? = {
    // 主操作略强一档，但同样收薄（1.6dp/0.88 那版白圈太显眼）
    Highlight(
        width = 1.0.dp,
        alpha = 1f,
        style = com.kyant.backdrop.highlight.HighlightStyle.Default(
            intensity = 0.66f,
            angle = 45f,
            falloff = 0.9f
        )
    )
}

/** 按钮的静态兜底：棱宽按控件高度缩放（40dp 的按钮用 6dp 的棱会糊死） */
private fun Modifier.chipStaticGlass(
    corner: Int,
    dark: Boolean,
    tint: Color?,
    strong: Boolean,
    pressed: Boolean,
    enabled: Boolean,
    flat: Boolean,
    pill: Boolean
): Modifier {
    val shape = if (pill) RoundedCornerShape(percent = 50) else RoundedCornerShape(corner.dp)
    val base = this
        // 凸显：和 GPU 档同一套手动（放大 + 上浮），缩放必须在 clip 之前
        .then(
            if (pressed) Modifier.graphicsLayer {
                scaleX = 1.06f; scaleY = 1.06f; translationY = -2.dp.toPx()
            } else Modifier
        )
        .then(
            if (flat) Modifier else Modifier.shadow(
                elevation = if (strong) 6.dp else 3.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.30f else 0.05f),
                spotColor = (tint ?: Color.Black).copy(alpha = if (dark) 0.30f else 0.14f)
            )
        )
        .clip(shape)
    return base.drawWithContent {
        val h = size.height
        // 棱宽按高度缩放：40dp 高的按钮棱约 1.2/2.4/4dp
        val u = (h * 0.030f).coerceIn(0.8f, 2.2f) * density
        val a0 = when {
            pressed -> 0.20f
            strong -> 0.12f
            else -> 0.10f
        } * (if (enabled) 1f else 0.6f)
        val topCol = if (dark) Color(0xFF3A4A66) else Color.White
        val botCol = if (dark) Color(0xFF223047) else Color(0xFFDDE6F5)
        // 胶囊时圆角按高度一半算，否则描边会超出实际轮廓
        val rPx = if (pill) h / 2f else corner.dp.toPx()
        val r = androidx.compose.ui.geometry.CornerRadius(rPx, rPx)

        // 本体：上亮下暗 = 有厚度
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to topCol.copy(alpha = (a0 + 0.20f).coerceAtMost(0.92f)),
                0.45f to topCol.copy(alpha = a0 + 0.04f),
                1f to botCol.copy(alpha = (a0 + 0.14f).coerceAtMost(0.92f))
            ),
            cornerRadius = r
        )
        // 染色
        if (tint != null) {
            val ta = (if (strong) 0.42f else 0.13f) * (if (enabled) 1f else 0.5f)
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to tint.copy(alpha = ta * 0.55f),
                    1f to tint.copy(alpha = ta)
                ),
                cornerRadius = r
            )
        }
        // 厚壁 + 内侧暗边（★静态档同样收薄：0.92 的白圈在浅背景上像贴了胶带）
        drawRoundRect(
            color = Color.White.copy(alpha = (if (dark) 0.30f else 0.52f) * (if (enabled) 1f else 0.5f)),
            cornerRadius = r,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = u)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = (if (dark) 0.08f else 0.18f) * (if (enabled) 1f else 0.4f)),
            cornerRadius = r,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 2.4f)
        )
        drawRoundRect(
            color = if (dark) Color.Black.copy(alpha = 0.26f) else Color(0xFF2B4A6B).copy(alpha = 0.10f),
            cornerRadius = r,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 4.2f)
        )
        // 定形线：白玻璃压白底，没有这条会像一团雾
        drawRoundRect(
            color = if (dark) Color.White.copy(alpha = 0.16f)
            else Color(0xFF8A97B8).copy(alpha = if (strong) 0.26f else 0.20f),
            cornerRadius = r,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f * density)
        )
        // 顶部高光（★0.96 → 0.58：原来顶部一道纯白，配上厚壁就是"包了一圈白边"）
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.30f else 0.58f),
                0.10f to Color.White.copy(alpha = if (dark) 0.09f else 0.20f),
                0.34f to Color.Transparent
            ),
            cornerRadius = r,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = u * 1.1f)
        )

        // ══ 第二层玻璃（和 GPU 档同一套手法）══════════════════
        //   内缩 2dp 再画一个小一圈的胶囊棱。低版本真机只有这一档，
        //   少了这两笔就只是"一块半透明"，不是"玻璃叠玻璃"。
        val inset = (2f * density).coerceAtMost(h * 0.16f)
        if (inset > 0f && size.width > inset * 2.5f) {
            val iw = size.width - inset * 2f
            val ih = h - inset * 2f
            val irPx = if (pill) ih / 2f else (rPx - inset).coerceAtLeast(0f)
            val irc = androidx.compose.ui.geometry.CornerRadius(irPx, irPx)
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.22f else 0.62f),
                    0.45f to Color.White.copy(alpha = if (dark) 0.06f else 0.16f),
                    1f to Color.White.copy(alpha = if (dark) 0.10f else 0.26f)
                ),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(iw, ih),
                cornerRadius = irc,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f * density)
            )
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.05f else 0.14f),
                    0.5f to Color.Transparent
                ),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(iw, ih * 0.55f),
                cornerRadius = irc
            )
        }
        drawContent()
    }
}

/**
 * 纯绘制玻璃（无 backdrop 采样）—— 给「画在图片/地图上」或预览环境用的兜底。
 *
 * 保留这个是因为有些位置底图本身就是图片（比如分享卡 Canvas 直绘），
 * 那里没有 Compose 图层可采样，只能用画出来的玻璃。
 */
fun DrawScope.drawGlassFallback(
    cornerPx: Float,
    dark: Boolean,
    alpha: Float = 0.92f
) {
    val r = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
    val top = if (dark) Color(0xFF0E1520) else Color.White
    val bot = if (dark) Color(0xFF1B2537) else Color(0xFFEFF4FC)
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to top.copy(alpha = alpha),
            1f to bot.copy(alpha = alpha)
        ),
        cornerRadius = r
    )
    drawRoundRect(
        color = Color.White.copy(alpha = if (dark) 0.16f else 0.85f),
        cornerRadius = r,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * density)
    )
    drawRoundRect(
        color = if (dark) Color.Black.copy(alpha = 0.22f)
        else Color(0xFF2B4A6B).copy(alpha = 0.10f),
        cornerRadius = r,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f * density)
    )
}
