package com.xixi.hiking.nativeapp.ui.overview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale

/**
 * 年度回顾分享图（独立绘制，**不截界面**）。
 *
 * ★★★2026-11-11 为什么要重写：
 *   上一版用 `rememberGraphicsLayer()` 录屏截取弹窗内容，真机上**保存直接失败**。
 *   两个原因：
 *     ① `GraphicsLayer.toImageBitmap()` 在 GPU 渲染下返回的是 **Hardware Bitmap**，
 *        而 `asAndroidBitmap()` 拿到它之后再做 `Canvas.drawBitmap` / 压缩就会抛异常
 *        （hardware bitmap 不能直接读像素）。这是 Compose 层的隐性坑。
 *     ② 弹窗有 `maxHeightFraction = 0.80f` + 底部关闭条，截出来的图还带着圆角、
 *        半透明玻璃和裁切边缘 —— 它本来就不该拿来分享。
 *
 *   ⇒ 分享图应该**独立设计**：不依赖界面尺寸、不受弹窗裁切影响、纯软件位图。
 *     版式沿用弹窗的语言（同一套配色/圆角/指标排布），但放大到 1080 宽的竖图。
 */
object YearReviewImage {

    private const val W = 1080
    private const val H = 1620
    private const val PAD = 72f

    /**
     * @param accent 强调色（由调用方传入）。
     *   ★不能在这里直接读 `AccentIndigo` —— 它是 `@Composable` getter，
     *   而绘制发生在普通函数里（不是组合作用域），直接引用编译不过。
     */
    fun render(cur: YearStats, dark: Boolean, accent: Int): Bitmap? {
        return try {
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawBackground(c, dark)
            drawHeader(c, cur, dark)
            drawMetrics(c, cur, dark)
            drawHighlights(c, cur, dark, accent)
            drawFooter(c, dark)
            bmp
        } catch (t: Throwable) {
            android.util.Log.w("hiking", "年度回顾出图失败", t)
            null
        }
    }

    // ── 背景：和 App 同色系的竖向渐变 ──
    private fun drawBackground(c: Canvas, dark: Boolean) {
        val top = if (dark) 0xFF0B1120.toInt() else 0xFFF7F8FD.toInt()
        val bottom = if (dark) 0xFF16203A.toInt() else 0xFFEFF2FB.toInt()
        val p = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
    }

    private fun drawHeader(c: Canvas, cur: YearStats, dark: Boolean) {
        val ink = if (dark) 0xFFE8EDF7.toInt() else 0xFF0F172A.toInt()
        val muted = if (dark) 0xFF8FA0BC.toInt() else 0xFF64748B.toInt()

        // 品牌小字
        c.drawText(
            "徒步小记", PAD, 120f,
            text(34f, muted, medium = true)
        )
        // 年份（大号）
        c.drawText(
            "${cur.year}", PAD, 268f,
            text(132f, ink, bold = true)
        )
        // 副标题
        c.drawText(
            "年度回顾", PAD + 8f, 340f,
            text(44f, muted)
        )
        // 一条细分割线
        val line = Paint().apply {
            color = if (dark) 0x1FFFFFFF else 0x14000000
            strokeWidth = 2f
        }
        c.drawLine(PAD, 400f, W - PAD, 400f, line)
    }

    private fun drawMetrics(c: Canvas, cur: YearStats, dark: Boolean) {
        val cardBg = if (dark) 0x14FFFFFF else 0xFFFFFFFF.toInt()
        val ink = if (dark) 0xFFE8EDF7.toInt() else 0xFF0F172A.toInt()
        val muted = if (dark) 0xFF8FA0BC.toInt() else 0xFF64748B.toInt()

        val items = listOf(
            "徒步次数" to "${cur.n} 次",
            "总里程" to String.format(Locale.getDefault(), "%.1f km", cur.km),
            "总用时" to durText(cur.min),
            "打卡山峰" to "${cur.peaks} 座",
            "平均里程" to String.format(Locale.getDefault(), "%.1f km", cur.avgKm),
            "最高海拔" to if (cur.maxEl > 0) "${cur.maxEl} m" else "—"
        )

        val gap = 24f
        val cellW = (W - PAD * 2 - gap) / 2f
        val cellH = 168f
        var top = 452f

        items.forEachIndexed { i, (label, value) ->
            val col = i % 2
            val row = i / 2
            val left = PAD + col * (cellW + gap)
            val t = top + row * (cellH + gap)

            // 卡片底（轻微白/黑，圆角 28）
            c.drawRoundRect(
                RectF(left, t, left + cellW, t + cellH), 28f, 28f,
                Paint().apply { color = cardBg; isAntiAlias = true }
            )
            // 标签
            c.drawText(label, left + 32f, t + 58f, text(30f, muted))
            // 数值
            c.drawText(value, left + 32f, t + 124f, text(56f, ink, bold = true))
        }
    }

    private fun drawHighlights(c: Canvas, cur: YearStats, dark: Boolean, accent: Int) {
        if (cur.lines.isEmpty()) return
        val ink = if (dark) 0xFFE8EDF7.toInt() else 0xFF0F172A.toInt()
        val muted = if (dark) 0xFF8FA0BC.toInt() else 0xFF475569.toInt()

        var y = 1180f
        c.drawText("这一年", PAD, y, text(40f, ink, bold = true))
        y += 56f

        cur.lines.take(4).forEach { line ->
            // 小圆点
            c.drawCircle(
                PAD + 8f, y - 12f, 6f,
                Paint().apply { color = accent; isAntiAlias = true }
            )
            c.drawText(line, PAD + 36f, y, text(32f, muted))
            y += 52f
        }
    }

    private fun drawFooter(c: Canvas, dark: Boolean) {
        val muted = if (dark) 0x66FFFFFF else 0x55000000
        c.drawText(
            "徒步小记 · 记录每一次出发",
            PAD, H - 72f, text(30f, muted)
        )
    }

    private fun text(size: Float, color: Int, bold: Boolean = false, medium: Boolean = false): Paint =
        Paint().apply {
            isAntiAlias = true
            textSize = size
            this.color = color
            typeface = when {
                bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                medium -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                else -> Typeface.DEFAULT
            }
        }

    /** 分钟 → "12h30m" / "48m"（和弹窗里同一套写法，保持观感一致） */
    private fun durText(min: Int): String {
        if (min <= 0) return "—"
        val h = min / 60
        val m = min % 60
        return when {
            h <= 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h${m}m"
        }
    }
}
