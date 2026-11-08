package com.xixi.hiking.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark

/**
 * ★★全局搜索条 —— 1:1 搬 Web 的 `.global-search-bar`（app.css:1987）
 *
 * 用户原话：「记录页的搜索按钮去掉，你看看 web 端是怎么实现搜索的，继续搬过来用」。
 *
 * ── Web 的做法 ──────────────────────────────────────────────
 *   它**不是**页面里的一行输入框，而是：
 *     · `position: fixed`，浮在底栏上方（bottom: 120px + safe-area）
 *     · 水平居中，宽 `min(100%, 260px)` —— 一条窄窄的玻璃胶囊
 *     · 底 `rgba(255,255,255,.10)` + 边 `rgba(255,255,255,.11)` + 圆角 12 + 内边距 8×12
 *     · 左边放大镜、右边清除 ✕（有词才出现）
 *     · 记录页（列表视图）和计划页（列表视图）常显；山册 / 计划日历**不提供搜索**
 *
 * ── 为什么不放内容流里 ────────────────────────────────────────
 *   放内容里就得占一行、把列表往下推，而且一滚就跟着滚走 —— 搜到一半想改词还得滚回去。
 *   浮在底栏上方，它是"随手可用"的，不参与排版。
 *
 * @param modifier 调用方负责定位（`align(BottomCenter)` + 底部让位）
 */
@Composable
fun GlobalSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索记录…"
) {
    val dark = LocalIsDark.current
    Row(
        modifier = modifier
            .widthIn(max = 260.dp)
            .fillMaxWidth()
            // 与 Web `.global-search-box` 同一套玻璃：white .10 / 边 .11 / 圆角 12
            .glassPanel(corner = 12)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Search, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Ink.copy(alpha = if (dark) 0.40f else 0.38f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                // Web input 是 16px（低于 16px 时部分输入法会强制放大页面）
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(AccentIndigo),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .safeClickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close, "清除搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
