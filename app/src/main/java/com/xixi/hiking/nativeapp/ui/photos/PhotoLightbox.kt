package com.xixi.hiking.nativeapp.ui.photos

import com.xixi.hiking.nativeapp.ui.safeClickable
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.xixi.hiking.nativeapp.data.PhotoEntity
import com.xixi.hiking.nativeapp.data.PhotoStore
import com.xixi.hiking.nativeapp.ui.GlassConfirm
import dev.chrisbanes.haze.HazeState

/**
 * ★★照片灯箱 —— v2（2026-11-04 重写）
 *
 * ── 这一版修的三件事 ──────────────────────────────
 *
 * ① **编辑态 / 预览态**（用户：「你没分清，灯箱也有编辑态和预览态，学一下 Web 版」）
 *
 *    Web `openPhotoLightbox(recordId, pid, editing)` 里，editing 决定两件事：
 *      · 主按钮文案：编辑态 = **添加**（选图加进这条记录）、预览态 = **保存**（存到相册）
 *      · 「删除」按钮：**只有编辑态才有**（预览态想看删照片，得先进编辑）
 *    以前原生只有一个形态（永远是「保存」+ 有 onDelete 就显示删除），
 *    于是「阅读记录时能删照片」而「编辑记录时反而看不了大图」—— 两边的语义正好反了。
 *
 * ② **控件离图片太远**（用户：「一个最上边，一个最下边，图片在中间」）
 *
 *    根因：图片区用 `weight(1f)` **吃满**剩余空间，图片 `ContentScale.Fit` 在这个大盒子里
 *    居中，盒子上下全是空白 ⇒ 标题被顶到屏幕顶、按钮沉到屏幕底，中间隔着一大片黑。
 *    （当初用 weight(1f) 是为了修「切图时控件上下跳」—— 那是个真问题，但解法错了。）
 *
 *    现在的解法：**图片盒子的尺寸 = 图片真正显示出来的尺寸**（按 PhotoEntity 的宽高比
 *    与可用空间算 contain），所以标题和按钮天然贴着图片上下沿；
 *    切图时盒子尺寸用 `animateDpAsState` 平滑过渡（220ms）——
 *    既贴身，又不会"啪"地跳。整组在屏幕里垂直居中（Web 是 `justify-content:center`）。
 *
 * ③ **放大不能满屏、有个方块卡着**
 *
 *    以前图片是 `fillMaxSize(0.94f)` 的一个大方框，`ContentScale.Fit` 在里面再留一圈空，
 *    放大时被放大的其实是那个"方框 + 空白"，于是永远有一圈看不见的边界卡着。
 *    现在盒子严丝合缝等于图片，`graphicsLayer` 缩放直接溢出到屏幕边缘，
 *    标题/按钮给 `zIndex`，放大时图片铺满、控件仍浮在上面（与 Web 的层级一致）。
 *
 * 另外补了 **1× 时左右滑动切换**（Web：touchend 位移 > 50px 翻页）。
 *
 * @param editing  编辑态（记录编辑弹窗里打开）
 * @param onAdd    编辑态主按钮「添加」的回调（选图入口在调用方，才能复用同一个 launcher）
 * @param onDelete 删除当前照片（**只在编辑态传**；预览态传 null 即不显示删除）
 */
@Composable
fun PhotoLightbox(
    photos: List<PhotoEntity>,
    startIndex: Int,
    onDismiss: () -> Unit,
    editing: Boolean = false,
    onAdd: (() -> Unit)? = null,
    onDelete: ((PhotoEntity) -> Unit)? = null,
    hazeState: HazeState? = null,
    title: String? = null
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current

    // ★索引自己管（不再用 HorizontalPager）：
    //   Pager 在这个全屏层里会和「缩放/拖动」抢手势，而且在 1× 时滑动的阻尼手感
    //   和 Web 的"甩一下就翻页"不一样。左右箭头 + 拖动切页都改这一份 idx。
    var idx by remember {
        mutableIntStateOf(startIndex.coerceIn(0, photos.lastIndex))
    }
    // 列表变化（比如编辑态刚添加/删了一张）时把索引夹回合法范围。
    // ★不能在组合期直接写 state（会在重组中再触发重组），放到 effect 里。
    androidx.compose.runtime.LaunchedEffect(photos.size) {
        if (idx > photos.lastIndex) idx = photos.lastIndex.coerceAtLeast(0)
    }

    // ★★2026-11-05 用户提的方案（比我原来的确认弹窗好）：
    //   「删除键就是确认键，第一次点删除，删除键变为确认？，再点一次才删除」
    //
    //   为什么比弹窗好：
    //     · 灯箱本身是 Dialog（独立窗口），在它上面再套一层弹窗要么被挡住、
    //       要么层级不稳（前两版就卡在这）
    //     · 两段式不需要第二个窗口，也不打断看图
    //     · 误触一下只是把按钮变成"确认删除？"，什么都没发生
    //
    //   关键是**必须能自己复原**，否则按钮一直停在"确认删除？"上，
    //   下次手滑点一下就直接删了 —— 那就等于没有确认。
    //   两条复原路径：3 秒没再点 / 翻到别的照片。
    var deleteArmed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(deleteArmed, idx) {
        if (deleteArmed) {
            kotlinx.coroutines.delay(3000)
            deleteArmed = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(idx) { deleteArmed = false }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val current = photos.getOrNull(idx)

    // ★★2026-11-04 照片显示得太小 —— 根因不是布局，是**比例拿错了**：
    //   以前只信 PhotoEntity 落库的 width/height，可导入/恢复的一部分照片这两个字段是 0，
    //   于是统一按 4:3 兜底 ⇒ 竖图被塞进横框，图片只占屏幕一小块。
    //   现在：数据库有值先用，同时等 Coil 加载完拿到**真实像素尺寸**立刻修正
    //   （onImageSize），两者都拿不到才用 3:4 估。
    var liveRatio by remember(current?.id) { mutableFloatStateOf(0f) }
    val dbW = current?.width ?: 0
    val dbH = current?.height ?: 0
    val ratio = when {
        liveRatio > 0.01f -> liveRatio
        dbW > 0 && dbH > 0 -> dbW.toFloat() / dbH.toFloat()
        else -> 0.75f
    }

    // overlayscan:ok 灯箱要全屏纯黑底 + 自己管返回键，GlassOverlay 的卡片形态不适用；
    //   它通过下面的 LocalInOverlay 接入弹窗体系（里面的确认框才会正常浮起）
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // ★★★2026-11-05 灯箱标成「已在弹窗体系里」。
        //
        //   灯箱用 Dialog（全屏黑底），它自己**不参与** OverlayStack；
        //   但它是在编辑弹窗的 content 里打开的 —— 组合上仍属于那棵子树。
        //   如果灯箱里的删除确认（GlassConfirm）去 push 弹窗栈，
        //   编辑弹窗就不再是栈顶 ⇒ 整棵 `return` ⇒ **灯箱和确认框一起消失**。
        //   这就是「灯箱里点删除，什么都不弹」的原因。
        //
        //   标成 in-overlay 之后，里面的确认框会走独立窗口（Popup）且不入栈：
        //   编辑弹窗照常画、灯箱照常在、确认框浮在最上面。
        androidx.compose.runtime.CompositionLocalProvider(
            com.xixi.hiking.nativeapp.ui.LocalInOverlay provides true,
            // ★灯箱本身就是一个全屏窗口（Dialog），里面的确认框**不要再套窗口** ——
            //   Dialog 里开 Popup 层级不稳，实测什么都不显示。
            //   声明 INLINE 让它就地画在最上层。
            com.xixi.hiking.nativeapp.ui.LocalOverlayHost
                provides com.xixi.hiking.nativeapp.ui.OverlayHostMode.INLINE
        ) {

        Box(
            Modifier
                .fillMaxSize()
                // Web：rgba(0,0,0,0.96)
                .background(Color.Black.copy(alpha = 0.96f))
                // 点空白关闭（子元素各自吃掉点击，只有真正的空白会走到这里）
                .safeClickable(onClick = onDismiss)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    // ★2026-11-04 留白收窄（用户：「照片限制的太小」）：
                    //   Web 是 16px 侧边 + 48/44 上下，但那是浏览器里 px；
                    //   手机上 16dp 边距 + 12dp 上下再叠安全区，图片能用的空间被啃掉一大圈。
                    //   现在侧边 8dp、上下 6dp —— 图片宽度从 379 提到 395（+4%），高度同步涨。
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val rowH = 36.dp          // 标题行（✕ 是 36dp 圆）
                val btnH = 44.dp          // 底部按钮行（圆形箭头 44dp）
                val gap = 12.dp           // Web 容器 gap:14px，这里略收一点换图片高度

                // 图片能用的最大空间（扣掉上下两行与两个 gap）
                val availH = (maxHeight - rowH - btnH - gap * 2).coerceAtLeast(80.dp)
                val availW = maxWidth

                // contain 计算：先按宽度铺满，超高再按高度收
                var imgW = availW
                var imgH = imgW / ratio
                if (imgH > availH) {
                    imgH = availH
                    imgW = imgH * ratio
                }

                // 切图时平滑过渡，不是"啪"一下跳（这是上一版 weight(1f) 想解决的问题）
                val animW by animateDpAsState(imgW, tween(220), label = "lbW")
                val animH by animateDpAsState(imgH, tween(220), label = "lbH")

                Column(
                    modifier = Modifier.fillMaxSize(),
                    // Web：justify-content:center —— 整组在屏幕里居中
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── 顶部：标题 + 页码 + 关闭（紧贴图片上沿）──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .height(rowH)
                            // ★放大时图片会溢出，标题要压在上面（Web：标题 z-index 6 > 图片 1）
                            .zIndex(2f)
                    ) {
                        Text(
                            title.orEmpty(),
                            // Web #lb-title：15px / 600 / 白 / text-shadow 0 1px 3px rgba(0,0,0,.5)
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.6f)
                        )
                        Text(
                            "${idx + 1} / ${photos.size}",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        // Web：#lb-close 36dp 正圆、白 0.15 底、无描边
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .safeClickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close, "关闭",
                                tint = Color.White, modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(gap))

                    // ── 图片 ──
                    // 盒子 = 图片显示尺寸（没有多余留白），放大时溢出到屏幕边缘
                    Box(
                        modifier = Modifier
                            .zIndex(1f)
                            .size(width = animW, height = animH),
                        contentAlignment = Alignment.Center
                    ) {
                        current?.let { ph ->
                            ZoomableImage(
                                key = ph.id,
                                fileName = ph.fileName,
                                context = context,
                                onImageSize = { w, h -> liveRatio = w / h },
                                onSwipe = { dir ->
                                    // Web：1× 时位移 > 50px 翻页（这里用 56dp，手感接近）
                                    idx = (idx + dir + photos.size) % photos.size
                                    toast = null
                                },
                                onZoomChange = { }
                            )
                        }
                    }

                    Spacer(Modifier.height(gap))

                    // ── 底部：‹ [保存/添加] [删除] ›（紧贴图片下沿）──
                    Row(
                        modifier = Modifier.zIndex(2f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircleButton(Icons.Filled.ChevronLeft, "上一张") {
                            idx = (idx - 1 + photos.size) % photos.size
                            toast = null
                        }
                        if (editing) {
                            // Web 编辑态主按钮 = 「添加」（lbAddPhoto）
                            LightboxPill("添加") { onAdd?.invoke() }
                        } else {
                            LightboxPill("保存") {
                                val ph = current ?: return@LightboxPill
                                val ok = saveToGallery(context, ph.fileName)
                                toast = if (ok) "已保存到相册" else "保存失败"
                            }
                        }
                        if (onDelete != null && editing) {
                            LightboxPill(
                                if (deleteArmed) "确认删除？" else "删除",
                                danger = true,
                                armed = deleteArmed
                            ) {
                                if (deleteArmed) {
                                    // 第二下：真删
                                    deleteArmed = false
                                    val ph = current ?: return@LightboxPill
                                    onDelete?.invoke(ph)
                                    if (photos.size <= 1) onDismiss()
                                } else {
                                    // 第一下：只改成确认态，什么都不删。
                                    // 不再弹「再点一次删除」的提示 —— 按钮自己已经变成
                                    // 「确认删除？」了，再说一句是重复。
                                    deleteArmed = true
                                }
                            }
                        }
                        CircleButton(Icons.Filled.ChevronRight, "下一张") {
                            idx = (idx + 1) % photos.size
                            toast = null
                        }
                    }
                }
            }

            // 操作结果提示：固定在底部（不参与 Column 布局，避免整组上下跳）
            toast?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        }}
}

/**
 * 可缩放图片：双指缩放 + 双击 1x/2.5x + 未放大时左右拖动切页。
 *
 * ★三个手势必须在**同一个** pointerInput 里判定：
 *   如果拆成 detectTransformGestures + detectHorizontalDragGestures 两个节点，
 *   前者一旦开始拖动就会 consume 事件，后者永远收不到 —— 那就滑不动了。
 */
@Composable
private fun ZoomableImage(
    key: String,
    fileName: String,
    context: android.content.Context,
    /** 加载完成后回报真实像素尺寸，用来修正宽高比（数据库里可能是 0） */
    onImageSize: (Float, Float) -> Unit = { _, _ -> },
    onSwipe: (Int) -> Unit,
    onZoomChange: (Float) -> Unit = {}
) {
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offset by remember(key) { mutableStateOf(Offset.Zero) }
    val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    var dragX by remember(key) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(key) {
                // ★防"滑一下翻三张"：翻页后 idx 变化 ⇒ key 变化 ⇒ 这个 pointerInput
                //   和下面的 remember 一起重建，dragX 自动归零、本次手势自然结束。
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 5f)
                    if (next > 1.01f) {
                        // 放大态：拖动看细节
                        scale = next
                        offset += pan
                        dragX = 0f
                        onZoomChange(next)
                    } else {
                        // 1× ：横向位移攒着，越过阈值就翻页
                        scale = 1f
                        offset = Offset.Zero
                        dragX += pan.x
                        if (dragX > swipeThreshold) {
                            dragX = 0f; onSwipe(-1)
                        } else if (dragX < -swipeThreshold) {
                            dragX = 0f; onSwipe(1)
                        }
                    }
                }
            }
            .pointerInput(key) {
                detectTapGestures(
                    // ★吃掉单击：Web 点照片**不会**关灯箱（只有点图片外的空白才关）
                    onTap = { },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                        onZoomChange(scale)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = PhotoStore.file(context, fileName),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit,
            // ★拿到真实像素尺寸后立刻回报，布局按真比例重算（动画过渡，不会硬跳）
            onSuccess = { s ->
                val sz = s.painter.intrinsicSize
                if (sz.width > 1f && sz.height > 1f) onImageSize(sz.width, sz.height)
            }
        )
        // 放大倍数指示（Web：#lb-zoom-ind）
        if (scale > 1.05f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    "×" + String.format(java.util.Locale.US, "%.1f", scale),
                    color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Web 版那颗 44dp 的圆形翻页按钮 */
@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .safeClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** 底部胶囊（保存 / 添加 / 删除），Web：padding 10×28、radius 22、14sp/600 */
@Composable
private fun LightboxPill(
    text: String,
    danger: Boolean = false,
    /** 「待确认」态：实心红底 —— 和普通危险按钮（淡红描边）明显不同，一眼看出"这一下真的会删" */
    armed: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                when {
                    armed -> Color(0xFFDC2626).copy(alpha = 0.92f)
                    danger -> Color(0xFF460F14).copy(alpha = 0.08f)
                    else -> Color.White.copy(alpha = 0.15f)
                }
            )
            .border(
                1.dp,
                when {
                    armed -> Color(0xFFFCA5A5).copy(alpha = 0.95f)
                    danger -> Color(0xFFF87171).copy(alpha = 0.90f)
                    else -> Color.White.copy(alpha = 0.25f)
                },
                RoundedCornerShape(22.dp)
            )
            .safeClickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Text(
            text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = if (armed) Color.White else if (danger) Color(0xFFFECACA) else Color.White
        )
    }
}

/** 分享单张照片（走系统分享面板） */
private fun shareOne(context: android.content.Context, photo: PhotoEntity) {
    try {
        val f = PhotoStore.file(context, photo.fileName)
        if (!f.exists()) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", f
        )
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, "分享照片到"))
    } catch (e: Throwable) {
        // 没装分享目标就静默
    }
}

/**
 * 保存到系统相册（Web 版 `lbPrimaryAction` 查看模式 = 「保存」）。
 * @return 成功?；失败返回 false，由调用方提示
 */
fun saveToGallery(context: android.content.Context, fileName: String): Boolean {
    return try {
        val src = PhotoStore.file(context, fileName)
        if (!src.exists()) return false
        val name = "xixi_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/XiXi徒步"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            // Android 10 以下：直接写公共 Pictures 目录再通知相册扫描
            @Suppress("DEPRECATION")
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "XiXi徒步"
            )
            if (!dir.exists()) dir.mkdirs()
            val dst = java.io.File(dir, name)
            src.inputStream().use { ins -> dst.outputStream().use { ins.copyTo(it) } }
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(dst.absolutePath), arrayOf("image/jpeg"), null
            )
            true
        }
    } catch (t: Throwable) {
        false
    }
}
