package com.xixi.hiking.nativeapp.ui.settings

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.PhotoMaintenance
import com.xixi.hiking.nativeapp.ui.GlassButton
import com.xixi.hiking.nativeapp.ui.GlassConfirm
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xixi.hiking.nativeapp.ui.theme.DangerRed

/**
 * 照片占用详情 —— ★★2026-11-04 按 Web `openPhotoUsageDetailModal` 重做。
 *
 * 用户原话：「照片占用做成和 web 版一样的」。
 *
 * ── Web 到底是什么样 ────────────────────────────────────────
 *   ① **一张统计卡**：左边一个大数字（28px/800 + 小一号单位），右边「共 N 张」；
 *      下面一条 7px 圆角容量条 —— 常态绿色渐变，超过 300 MB 变红色渐变，
 *      宽度 = 已用 / 300 MB。
 *   ② **超限才出现的警示条**：浅红底 + 红字，说明超了建议值。
 *   ③ **两个按钮**：左边「知道了」关闭，右边「优化」（超限时文案变「去清理」）。
 *
 * ── 和上一版的区别 ──────────────────────────────────────────
 *   上一版是我自己编的三宫格（照片/占用/磁盘文件）+ 一段压缩说明 + 两张"问题卡"，
 *   Web 上根本没有这些 —— 它只要一眼看清"占了多少、离 300MB 还有多远"。
 *   现在整块按 Web 重排；孤儿文件清理收进「优化」按钮（只删不属于任何记录的缓存，
 *   记录里的照片一张不动）——这也是 Web 的语义。
 */
@Composable
fun PhotoUsageDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val isDark = LocalIsDark.current
    var usage by remember { mutableStateOf<PhotoMaintenance.Usage?>(null) }
    var scanning by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }
    var tip by remember { mutableStateOf<String?>(null) }
    var askClean by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(tick) {
        scanning = true
        usage = try {
            // ★5 秒超时：照片几百张、数据库忙的时候扫得慢，但不能让弹窗一直转圈
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                withContext(Dispatchers.IO) { PhotoMaintenance.scan(ctx) }
            }
        } catch (e: Throwable) {
            null
        }
        scanning = false
    }

    val u = usage
    val bytes = u?.bytes ?: 0L
    val over = bytes >= PHOTO_LIMIT_BYTES
    val (numText, unitText) = sizeParts(bytes)

    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.90f,
        corner = 20,
        bottomClose = false
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ── 标题（Web: photo_library 图标 + 照片占用）──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PhotoLibrary, null,
                    tint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "照片占用", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))

            if (u == null) {
                // 统计是异步的：给个固定高度，别让弹窗从一条缝"长"出来
                Box(
                    Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (scanning) "统计中…" else "照片统计不可用",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // ── ① 统计卡：大数字 + 共 N 张 + 容量条 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isDark) Color(0xFF334155).copy(alpha = 0.55f)
                            else Color(0xFF94A3B8).copy(alpha = 0.12f)
                        )
                        .border(
                            1.dp,
                            if (isDark) Color(0xFF94A3B8).copy(alpha = 0.20f)
                            else Color(0xFF64748B).copy(alpha = 0.22f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                            Text(
                                numText,
                                fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF1F2937)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                unitText,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFF1F5F9).copy(alpha = 0.75f)
                                else Color(0xFF1F2937).copy(alpha = 0.75f),
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            "共 ${u.dbCount} 张",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF8B9AB0) else Color(0xFF334155)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // 容量条：7dp 高、圆角 99，底色轨道 + 渐变前景
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (isDark) Color(0xFF94A3B8).copy(alpha = 0.22f)
                                else Color(0xFF64748B).copy(alpha = 0.18f)
                            )
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    (bytes.toFloat() / PHOTO_LIMIT_BYTES.toFloat()).coerceIn(0f, 1f)
                                )
                                .height(7.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (over) Brush.horizontalGradient(
                                        listOf(Color(0xFFF87171), DangerRed)
                                    ) else Brush.horizontalGradient(
                                        listOf(Color(0xFF34D399), Color(0xFF10B981))
                                    )
                                )
                        )
                    }
                }

                // ── ② 超限警示（只在超过 300MB 时出现）──
                if (over) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isDark) DangerRed.copy(alpha = 0.16f)
                                else DangerRed.copy(alpha = 0.08f)
                            )
                            .border(
                                1.dp,
                                if (isDark) Color(0xFFF87171).copy(alpha = 0.45f)
                                else DangerRed.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.WarningAmber, null,
                            tint = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                            modifier = Modifier.padding(top = 1.dp).size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "照片已占 $numText$unitText，超过建议的 300 MB —— " +
                                "删几张，或点「去清理」清理没用的缓存照片",
                            fontSize = 12.sp, lineHeight = 19.sp,
                            color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                        )
                    }
                }

                // 清理结果 / 提示
                tip?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) {
                    GlassButton("知道了", onClick = onDismiss)
                }
                Box(Modifier.weight(1f)) {
                    GlassButton(
                        if (over) "去清理" else "优化",
                        primary = true,
                        onClick = {
                            val cur = usage
                            if (cur == null) {
                                tip = "还没统计完，稍等一下"
                            } else if (cur.orphans.isEmpty()) {
                                // Web 的语义：没有可清理的缓存就直说，不吓唬人
                                tip = "没有可清理的缓存照片，记录里的照片一张都不会动"
                            } else {
                                askClean = true
                            }
                        }
                    )
                }
            }
        }
    }

    // 清理确认：只删「不属于任何记录」的缓存照片
    if (askClean) {
        val n = usage?.orphans?.size ?: 0
        val mb = usage?.orphanMb ?: "0.0"
        GlassConfirm(
            hazeState = hazeState,
            title = "清理缓存照片",
            message = "发现 $n 张不属于任何记录的缓存照片，共 $mb MB。\n" +
                "只删这些游离文件 —— 记录里保存的照片一张都不会动",
            confirmText = "清理",
            danger = true,
            onCancel = { askClean = false },
            onConfirm = {
                askClean = false
                val orphans = usage?.orphans.orEmpty()
                tip = "清理中…"
                scope.launch {
                    val freed = PhotoMaintenance.cleanOrphans(orphans)
                    tip = "已释放 ${freed / 1024} KB"
                    tick++      // 重扫，数字和容量条一起更新
                }
            }
        )
    }
}

/** 照片库建议上限（对齐 Web 的 PHOTO_LIMIT_BYTES = 300 MB） */
private const val PHOTO_LIMIT_BYTES = 300L * 1024 * 1024

/**
 * 字节 → 「数值 + 单位」两段（Web `photoSizeParts` 同款）：
 * 不足 1 GB 用 MB（1 位小数），超过用 GB（2 位）。
 */
private fun sizeParts(bytes: Long): Pair<String, String> {
    val mb = bytes / 1048576.0
    return if (mb >= 1024) {
        String.format(java.util.Locale.US, "%.2f", mb / 1024) to "GB"
    } else {
        String.format(java.util.Locale.US, "%.1f", mb) to "MB"
    }
}
