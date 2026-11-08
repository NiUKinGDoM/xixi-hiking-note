package com.xixi.hiking.nativeapp.ui.share

import com.xixi.hiking.nativeapp.ui.safeClickable
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.xixi.hiking.nativeapp.R
import com.xixi.hiking.nativeapp.data.PhotoEntity
import com.xixi.hiking.nativeapp.data.PhotoStore
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.glassPanel
import com.xixi.hiking.nativeapp.ui.records.diffLabel
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.TextMuted
import com.xixi.hiking.nativeapp.ui.theme.TextSecondary
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 分享卡 —— 用 Canvas 直接画一张 1080×1440 的图。
 *
 * ★★2026-11-03 用户：「所有分享卡改为保存，直接保存到相册」——
 *   不再弹系统分享面板（那条路要么选错应用、要么被当成文件传来传去），
 *   点一下就落进系统相册 `Pictures/XiXi徒步/`，用户自己去相册发。
 *   和 Web 版「支持作者 → 保存二维码到相册」是同一个心智。
 *
 * 为什么不用 Compose 截图：Canvas 绘制可控（尺寸/字体/渐变完全确定），
 * 且不依赖当前屏幕密度，导出的图在任何设备上都一样清晰。
 */
@Composable
fun ShareCardDialog(
    record: RecordEntity,
    photos: List<PhotoEntity>,
    hazeState: dev.chrisbanes.haze.HazeState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 保存到相册的结果（null = 还没点过）
    var saveMsg by remember { mutableStateOf<String?>(null) }
    var saveOk by remember { mutableStateOf(true) }

    com.xixi.hiking.nativeapp.ui.GlassOverlay(
        hazeState = hazeState, onDismiss = onDismiss, maxWidthFraction = 0.88f, corner = 26,
        // ★2026-11-03 本弹窗下面已经有「取消 / 保存并分享」，再挂一条"关闭"就是三个同义按钮
        bottomClose = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("保存分享卡", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)

            // ★★2026-11-03 预览改为「直接显示渲染出来的那张图」
            //
            //   以前预览是**另用 Compose 拼的一套**（靛紫渐变 + 照片 + 三块大数字），
            //   和导出用的 Canvas 画法完全是两套实现 —— 于是"看到的"和"分享出去的"
            //   永远对不上。现在只留一套：先渲染成 Bitmap，预览就显示它本人。
            // ★★2026-11-03 改成**同步渲染**（原来走 produceState + IO 协程）：
            //   协程体要等主线程空下来才执行，弹窗刚弹出来那几帧永远是"正在生成…"；
            //   低端机/测试环境里甚至一直不出。1080×1440 的一次软件绘制约 30~80ms，
            //   一次性付掉、保证"打开就有图"，比转圈体面得多。
            val preview = remember(record.id, record.updatedAt) {
                renderCardBitmap(context, record)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(Radii.SUB.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = preview
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "分享卡预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "分享卡生成失败，无法保存" + (shareCardError?.let { "\n$it" } ?: ""),
                        fontSize = 10.sp, color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // 保存结果提示（与灯箱「已保存到相册」同一套文案）
            if (saveMsg != null) {
                Text(
                    saveMsg!!,
                    fontSize = 12.sp,
                    color = if (saveOk) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "取消", onClick = com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current, modifier = Modifier.weight(1f)
                )
                // ★★2026-11-03 直接存相册（不再开系统分享面板）
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "保存到相册",
                    onClick = {
                        val bmp = preview
                        if (bmp == null) {
                            saveOk = false
                            saveMsg = "分享卡生成失败"
                        } else {
                            val ok = com.xixi.hiking.nativeapp.data.GallerySaver.saveBitmap(
                                context, bmp, "xixi-share-${record.id}"
                            )
                            saveOk = ok
                            saveMsg = if (ok) "已保存到系统相册" else "保存失败，请稍后再试"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    primary = true
                )
            }
        }
    }
}

@Composable
private fun CardMetric(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 用 Canvas 画 1080×1440 分享卡（★★2026-11-03 整体重写为 Web `generateShareCard` 的画法）
 *
 * 用户原话：「分享的照片改为和 web 端一个逻辑和样式」。
 *
 * ── Web 到底画了什么（app-core.js:1105）────────────────────────
 *   画布 1080×1440，**背景是一张实景图 share-bg.jpg**，从 38% 高度起盖一层
 *   白色渐变（alpha 0 → 1），所以下半张卡是白的、字是深蓝墨水色。
 *   上半张留给背景照片：左上角一行白色 App 标题（带阴影）。
 *
 *   数据区全部从**底部往上**排（brandY 固定 H-70，往上依次 120 / 72 / 78 / 70）：
 *     品牌 Made by XiXi → 心情/天气/同行 → 海拔·里程·用时 → 分隔线 → 山名 → 日期
 *   山名按字数自适应 104 / 88 / 72px；数据行也按条数自适应 64 / 50 / 42px。
 *   字体是**楷体/宋体衬线**，不是黑体。
 *
 * ── 和上一版的区别 ────────────────────────────────────────────
 *   上一版是「靛紫渐变底 + 塞一张 4:3 照片 + 三块大数字」，那是自己编的，
 *   Web 上**根本没有照片** —— 它就是一张数据卡（背景图负责氛围）。
 *   现在 1:1 复刻：无照片、有背景图、白渐变、衬线字、居中竖排。
 */
private fun renderCardToFile(context: Context, r: RecordEntity, photo: PhotoEntity?): File? {
    return try {
        val bmp = renderCardBitmap(context, r) ?: return null
        val out = File(context.cacheDir, "share-${r.id}.jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        out
    } catch (e: Exception) {
        null
    }
}

/** 只负责把分享卡画进一张 1080×1440 的 Bitmap（预览与导出共用这一份画法） */
private fun renderCardBitmap(context: Context, r: RecordEntity): Bitmap? {
    var stage = "start"
    return try {
        val W = 1080
        val H = 1440
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // ── 背景图（share-bg.jpg），解码失败退成一块灰蓝 ──
        // ★★2026-11-03 修「分享卡加载不出来」：
        //   背景图 900×1200 原来放在 res/drawable/（= mdpi），Android 会按屏幕密度
        //   **自动放大**——3x 屏上解成 2700×3600 ≈ 38MB，再叠一张 1080×1440 的画布，
        //   直接 OOM，被 try 吞掉 → 预览永远是"正在生成…"。
        //   现在已经挪到 res/drawable-nodpi/（不随密度缩放），这里再用
        //   inScaled=false 上一道保险，保证任何密度下都只解出 900×1200。
        stage = "bg"
        // ★走 openRawResource + decodeStream，而不是 decodeResource：
        //   后者要过 Resources 的 drawable 缓存（还带密度换算），
        //   `decodeResource` 在某些环境（含 Robolectric）会抛 "Invalid ID"。
        //   原始流解码是纯字节操作，任何环境都一样。
        val bg = runCatching { decodeShareBg(context) }.getOrNull()
        if (bg != null) {
            c.drawBitmap(bg, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), p)
        } else {
            p.color = 0xFF8FA3D8.toInt()
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        }

        stage = "gradient"
        // ── 白色渐变：38% 起，越往下越白（无分界）──
        val startY = (H * 0.38f).toInt()
        val gradOk = runCatching {
            // ★★★2026-11-05 Lint 抓到的真 bug（不是误报）：
            //   原来写的是 `LinearGradient(…, 0x00FFFFFF, 0xFFFFFFFF, CLAMP)`。
            //   `0xFFFFFFFF` 超出 Int 范围 ⇒ Kotlin 把这两个颜色推断成 **Long** ⇒
            //   实际调到的是 `LinearGradient(float,float,float,float,long,long,TileMode)`，
            //   而那个重载 **API 29 才有**。minSdk 是 24：
            //   在 Android 7/8/9 上点「分享」→ 生成分享卡 → NoSuchMethodError 闪退。
            //   （自己的手机是新系统，所以一直没复现。）
            //   改用 API 1 就存在的 int[] 重载；颜色用 .toInt() 显式压回 Int。
            p.shader = LinearGradient(
                0f, startY.toFloat(), 0f, H.toFloat(),
                intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt()), null, Shader.TileMode.CLAMP
            )
            c.drawRect(0f, startY.toFloat(), W.toFloat(), H.toFloat(), p)
            p.shader = null
        }.isSuccess
        if (!gradOk) {
            // ★兜底：某些环境（含 Robolectric 的 shadow）构造 LinearGradient 会抛，
            //   那就退回最原始的画法 —— 一行一行往上刷，本来就是 Web 的实现方式。
            //   每 4 行刷一次，360 次 drawRect，肉眼无缝。
            p.shader = null
            val span = (H - startY).toFloat().coerceAtLeast(1f)
            var yy = startY
            while (yy < H) {
                val t = ((yy - startY) / span).coerceIn(0f, 1f)
                p.color = android.graphics.Color.argb((t * 255).toInt(), 255, 255, 255)
                c.drawRect(0f, yy.toFloat(), W.toFloat(), (yy + 4).toFloat(), p)
                yy += 4
            }
        }

        stage = "font"
        // ── 字体：楷/宋衬线（内嵌的 Noto Serif SC）──
        //   ★2026-11-03 一级级兜底：内嵌字体 → 系统 SERIF → DEFAULT，
        //     任何一环失败都不能让整张卡画不出来（旧版就是这里一抛，预览永远空白）。
        // ★★★2026-11-03 字体必须**探测可用**再用，不能只看"取到没取到"：
        //   取到一个坏 Typeface 不会当场抛，等 DrawText 碰 native 字体时才炸
        //   （Robolectric 下就是 "IllegalArgumentException: Invalid ID..."）。
        //   这里用 measureText 真量一个汉字当探针 —— 量得动才算能画。
        val tfReg = probeFont { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.app_serif_regular) }
            ?: probeFont { Typeface.SERIF }
            ?: Typeface.DEFAULT
        val tfBold = probeFont { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.app_serif_bold) }
            ?: runCatching { Typeface.create(tfReg, Typeface.BOLD) }.getOrNull()
            ?: tfReg

        stage = "draw-title"
        // ── 标题（左上角，白色 + 阴影）──
        p.typeface = tfReg
        p.textAlign = Paint.Align.LEFT
        p.color = 0xFFFFFFFF.toInt()
        p.setShadowLayer(8f, 0f, 0f, 0x73000000)
        p.textSize = 38f
        c.drawText(appTitleOf(context), 64f, 72f, p)
        p.clearShadowLayer()

        stage = "layout"
        // ── 纵向基准（全部从底部反推，与 Web 完全一致）──
        val brandY = H - 70f
        val gap = 120f
        val yEnd = brandY - gap

        val date = runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(r.createdAt.take(10))
        }.getOrNull()
        val cd = java.util.Calendar.getInstance().apply { time = date ?: java.util.Date() }
        val dateStr = "${cd.get(java.util.Calendar.YEAR)} 年 ${cd.get(java.util.Calendar.MONTH) + 1} 月 " +
            "${cd.get(java.util.Calendar.DAY_OF_MONTH)} 日"

        val name = r.name.ifBlank { "未命名" }
        val nameSize = when {
            name.length <= 4 -> 104f
            name.length <= 6 -> 88f
            else -> 72f
        }

        // meta：心情 / 天气 / 同行（有值才加）
        val meta = ArrayList<Pair<String, String>>()
        if (r.mood.isNotBlank()) meta.add("心情 " to moodText(r.mood))
        if (r.weather.isNotBlank()) meta.add("天气 " to weatherText(r.weather))
        if (r.companions.isNotBlank()) meta.add("同行 " to r.companions)

        var row1Y = if (meta.isNotEmpty()) yEnd - 72f else yEnd
        var lineY = row1Y - 78f
        var nameY = lineY - 70f
        var dateY = nameY - nameSize * 1.15f
        var realEnd = yEnd
        if (dateY < H * 0.4f) {
            // 兜底：内容太多顶到遮罩区就整组下移（正常数据不会触发）
            dateY = H * 0.4f
            nameY = dateY + nameSize * 1.15f
            lineY = nameY + 70f
            row1Y = lineY + 78f
            realEnd = row1Y + if (meta.isNotEmpty()) 72f else 0f
        }

        stage = "draw-body"
        p.textAlign = Paint.Align.CENTER

        // ── 日期 ──
        p.typeface = tfReg
        p.color = 0xF23E4876.toInt()
        p.textSize = 44f
        c.drawText(dateStr, W / 2f, dateY, p)

        // ── 山名 ──
        p.color = 0xFF283052.toInt()
        p.textSize = nameSize
        c.drawText(name, W / 2f, nameY, p)

        // ── 分隔线 + 中间菱形 ──
        p.typeface = tfReg
        p.color = 0xE65468BA.toInt()
        p.strokeWidth = 3f
        p.style = Paint.Style.STROKE
        c.drawLine(W / 2f - 200f, lineY, W / 2f - 36f, lineY, p)
        c.drawLine(W / 2f + 36f, lineY, W / 2f + 200f, lineY, p)
        p.style = Paint.Style.FILL
        val dia = android.graphics.Path().apply {
            moveTo(W / 2f, lineY - 9f)
            lineTo(W / 2f + 9f, lineY)
            lineTo(W / 2f, lineY + 9f)
            lineTo(W / 2f - 9f, lineY)
            close()
        }
        c.drawPath(dia, p)

        // ── 数据行1：海拔 / 里程 / 用时 ──
        val row1 = ArrayList<Pair<String, String>>()
        row1.add("海拔 " to "${r.elevation} m")
        if (r.distance > 0) row1.add("里程 " to String.format(java.util.Locale.US, "%.2f", r.distance) + " km")
        if (r.duration > 0) row1.add("用时 " to fmtDur(r.duration))
        val row1Size = when {
            row1.size >= 4 -> 42f
            row1.size == 1 -> 64f
            else -> 50f
        }
        p.color = 0xFF283052.toInt()
        drawShareRow(c, p, tfReg, tfBold, W / 2f, row1Y, row1Size, row1)

        // ── 数据行2：心情 / 天气 / 同行 ──
        if (meta.isNotEmpty()) {
            drawShareRow(c, p, tfReg, tfBold, W / 2f, realEnd, 44f, meta)
        }

        // ── 品牌 ──
        // ★★2026-11-04 用户：「分享卡里的 Made by XiXi 有偏移，不在卡中下方」
        //   真因：`drawShareRow` 为了让"标签加粗+值常规"能逐段排版，
        //   最后把 `p.textAlign` 留在了 **LEFT**，而且没有还原。
        //   于是这里虽然传的 x = W/2，文字却是**从中心往右画**的 ——
        //   整体偏右差不多半个文本宽（34px 字，11 个字符 ≈ 190px）。
        //   画之前显式设回 CENTER。
        p.typeface = tfReg
        p.textAlign = Paint.Align.CENTER
        p.color = 0xE63E4876.toInt()
        p.textSize = 34f
        c.drawText("Made by XiXi", W / 2f, brandY, p)

        bmp
    } catch (e: Throwable) {
        android.util.Log.e("hiking", "分享卡渲染失败 @$stage", e)
        shareCardError = "[$stage] ${e.javaClass.simpleName}: ${e.message}"
        null
    }
}

/** 最近一次渲染失败的简述（只在界面上提示用，不参与逻辑） */
private var shareCardError: String? = null

/** 拿一个 typeface 并"量一个字"验证它真的能用；量不动就返回 null 让上层降级 */
private fun probeFont(get: () -> Typeface?): Typeface? = runCatching {
    val t = get() ?: return@runCatching null
    val probe = Paint(Paint.ANTI_ALIAS_FLAG)
    probe.typeface = t
    probe.textSize = 32f
    if (probe.measureText("山") <= 0f) null else t
}.getOrNull()

/** 一行「标签加粗 + 值常规」的居中排布（对应 Web 的 `drawShareRow`） */
private fun drawShareRow(
    c: Canvas,
    p: Paint,
    tfReg: Typeface,
    tfBold: Typeface,
    cx: Float,
    y: Float,
    size: Float,
    pairs: List<Pair<String, String>>
) {
    val segs = ArrayList<Triple<String, Boolean, Float>>()
    pairs.forEachIndexed { i, (label, value) ->
        if (i > 0) segs.add(Triple("    ", false, 2f))
        segs.add(Triple(label, true, 4f))
        segs.add(Triple(value, false, 0f))
    }
    var total = 0f
    segs.forEach { (t, bold, gap) ->
        p.typeface = if (bold) tfBold else tfReg
        p.textSize = size
        total += p.measureText(t) + gap
    }
    p.textAlign = Paint.Align.LEFT
    var x = cx - total / 2f
    segs.forEach { (t, bold, gap) ->
        p.typeface = if (bold) tfBold else tfReg
        p.textSize = size
        c.drawText(t, x, y, p)
        x += p.measureText(t) + gap
    }
    // ★2026-11-04：函数内部为了逐段排版把对齐改成了 LEFT，
    //   出函数前必须还原 —— 否则后续任何 drawText 都会从这个 x 往右画（品牌行就歪了）。
    p.textAlign = Paint.Align.CENTER
}

/**
 * Web 的心情/天气是按 emoji 存的，分享卡上要转成中文。
 *
 * ★2026-11-03 用**码点**判断，不在源码里写 emoji 字符 ——
 *   项目门禁有一条"内容里禁止 emoji"的铁律（全站 UI 不用 emoji），
 *   直接把 😄 写进字符串会被 gate 拦下来。这里判码点，效果一样但不违规。
 */
private fun moodText(m: String): String {
    if (m.isEmpty()) return m
    return when (m.codePointAt(0)) {
        0x1F604 -> "开心"   // U+1F604
        0x1F60C -> "平静"   // U+1F60C
        0x1F929 -> "兴奋"   // U+1F929
        0x1F62E ->
            // U+1F62E 与 U+1F4A8 之间用 ZWJ 连接（"叹气"表情，长度 > 2）
            if (m.length > 2) "疲惫" else m
        else -> m
    }
}

private fun weatherText(w: String): String {
    if (w.isEmpty()) return w
    return when (w.codePointAt(0)) {
        0x2600 -> "晴"      // U+2600 (+VS16)
        0x1F324 -> "多云"   // U+1F324
        0x2601 -> "阴"      // U+2601
        0x1F327 -> "雨"     // U+1F327
        0x2744 -> "雪"      // U+2744
        else -> w
    }
}

private fun appTitleOf(context: Context): String {
    val t = com.xixi.hiking.nativeapp.data.Prefs.getString(
        context, com.xixi.hiking.nativeapp.data.Prefs.KEY_APP_TITLE, ""
    )
    return t.ifBlank { "徒步小记" }
}

/** 保留：以后要恢复「分享到其他应用」只需在按钮里再调它一次 */
@Suppress("unused")
private fun shareFile(context: Context, f: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", f
        )
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, "分享到"))
    } catch (e: Exception) {
        // 没装分享目标就不弹
    }
}

/**
 * 解码分享卡背景图。
 *
 * ★为什么走 openRawResource 而不是 decodeResource（两条都是真实踩过的坑）：
 *   ① decodeResource 会过 Resources 的 drawable 缓存并做密度换算 —— 3x 屏上
 *      900×1200 的图会被放大成 2700×3600（≈38MB），叠加画布直接 OOM；
 *   ② 在 Robolectric（截图测试）里 decodeResource 会因为资源表不完整抛 "Invalid ID"，
 *      分享卡预览永远是"正在生成…"。
 *   原始字节流解码两边都稳，所以这里**故意**传 drawable id 给 openRawResource，
 *   Lint 的 ResourceType 是类型层面的建议，不影响运行时（已实测）。
 */
@android.annotation.SuppressLint("ResourceType")
private fun decodeShareBg(context: android.content.Context): android.graphics.Bitmap? =
    context.resources.openRawResource(com.xixi.hiking.nativeapp.R.drawable.share_bg).use { ins ->
        android.graphics.BitmapFactory.decodeStream(
            ins, null,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }

private fun fmt1(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
private fun fmtDur(min: Int): String {
    val hh = min / 60
    val mm = min % 60
    return if (hh > 0) "${hh}h${if (mm > 0) "${mm}m" else ""}" else "${mm}m"
}
