package com.xixi.hiking.nativeapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import kotlinx.coroutines.delay

/**
 * ★★轻提示（toast）—— 1:1 对齐 Web 版 `_createToast`
 *
 * 之前原生版的「toast」其实是一个 GlassConfirm 弹窗（标题「提示」+ 好/关闭），
 * 每次都要求用户点一下才算完 —— 那不是 toast，是"又一个弹窗"。
 * 用户问「现在都没有 toast 弹窗了？」说的就是这个。
 *
 * Web 的规格（app-core.js / TOAST_CFG）：
 *   · 固定底部 140px、水平居中、胶囊形（padding 12×20）
 *   · 图标 + 文案（14px / 500），成功 check_circle 绿、失败 error 红、普通 info 蓝
 *   · 1.0~1.4s 自动消失，点一下也能关；不挡任何操作
 * 这里照着做：底部浮层 + 自动消失 + 点击即关，不铺遮罩、不拦事件。
 */
object AppToast {
    /** 当前要显示的文案；null 表示不显示 */
    var message by mutableStateOf<String?>(null)
    /** success / error / info —— 决定图标与颜色 */
    var kind by mutableStateOf("success")

    fun show(text: String, type: String? = null) {
        if (text.isBlank()) return
        message = text
        kind = type ?: when {
            text.startsWith("❌") || text.contains("失败") || text.contains("出错") ||
                text.contains("不对") || text.contains("无法") || text.contains("没给权限") -> "error"
            text.startsWith("✅") || text.contains("已") || text.contains("成功") -> "success"
            else -> "info"
        }
    }
}

@Composable
fun AppToastHost() {
    val isDark = LocalIsDark.current
    val msg = AppToast.message

    // 自动消失：成功/失败 1.2s，普通说明多留一会儿（和 Web 的 1000/1400ms 同量级）
    LaunchedEffect(msg) {
        if (msg != null) {
            delay(if (AppToast.kind == "info") 1600L else 1200L)
            AppToast.message = null
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = msg != null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 }
        ) {
            val text = msg ?: ""
            // ★★2026-11-04 用户：「toast 你学一下 web 端的，现在背景透明的」
            //
            //   真因：这里原来用 `liquidGlassChip(flat = true)` ——
            //   而 glassStatic 的平面分支现在 `fill = veil`（veil 只有 0.10 左右），
            //   于是 toast 就是一块几乎全透明的玻璃，字浮在页面上，内容一花就看不清。
            //
            //   Web 的 `.toast-glass` 是一套**语义色**配方（app-modal.css:752）：
            //     success  rgba(220,252,231,.40) 底 + rgba(34,197,94,.55) 1px 边 + #166534 字
            //     error    rgba(254,226,226,.40) + rgba(220,38,38,.55) + #b91c1c
            //     info     rgba(226,232,240,.40) + rgba(100,116,139,.55) + #334155
            //     深色各有一组（见下），加 0 8px 28px rgba(0,0,0,.14) 的投影
            //   这里逐项照搬，**动画一行没动**（下面的 AnimatedVisibility 还是原来那套）。
            val bg = when (AppToast.kind) {
                "error" -> if (isDark) Color(0xFF460F14).copy(alpha = 0.40f)
                else Color(0xFFFEE2E2).copy(alpha = 0.40f)
                "success" -> if (isDark) Color(0xFF0A3C28).copy(alpha = 0.40f)
                else Color(0xFFDCFCE7).copy(alpha = 0.40f)
                else -> if (isDark) Color(0xFF283244).copy(alpha = 0.40f)
                else Color(0xFFE2E8F0).copy(alpha = 0.40f)
            }
            val stroke = when (AppToast.kind) {
                "error" -> (if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)).copy(alpha = 0.55f)
                "success" -> (if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)).copy(alpha = 0.55f)
                else -> (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)).copy(alpha = 0.55f)
            }
            val ink = when (AppToast.kind) {
                "error" -> if (isDark) Color(0xFFFECACA) else Color(0xFFB91C1C)
                "success" -> if (isDark) Color(0xFFBBF7D0) else Color(0xFF166534)
                else -> if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
            }
            // 图标比文字淡一档（Web: .toast-glass.info .material-icons 是 #64748b）
            val iconTint = when (AppToast.kind) {
                "error" -> ink
                "success" -> ink
                else -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            }
            val icon = when (AppToast.kind) {
                "error" -> Icons.Filled.Error
                "success" -> Icons.Filled.CheckCircle
                else -> Icons.Filled.Info
            }
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    // Web：bottom 140px（底栏之上），左右各留 24dp 安全边
                    .navigationBarsPadding()
                    .padding(bottom = 140.dp, start = 24.dp, end = 24.dp)
                    // Web：max-width: min(69vw, 360px)
                    .widthIn(max = 360.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.14f),
                        spotColor = Color.Black.copy(alpha = 0.14f)
                    )
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, stroke, shape)
                    .safeClickable { AppToast.message = null }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    fontSize = 14.sp,
                    // Web: font-weight: 600
                    fontWeight = FontWeight.SemiBold,
                    // 不换行到第三行：长文案（导入结果）最多两行
                    maxLines = 3,
                    color = ink
                )
            }
        }
    }
}
