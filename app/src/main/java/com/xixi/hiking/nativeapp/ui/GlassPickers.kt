package com.xixi.hiking.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.ui.liquid.glassStatic
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import com.xixi.hiking.nativeapp.ui.theme.Radii
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size

/**
 * ★★日期 / 日期时间选择器（2026-11-02）
 *
 * 为什么必须有：原来编辑记录时「日期」是个**纯文本框**（YYYY-MM-DD HH:mm），
 * 用户得手打，打错一个数（2026-13-45）就整条记录在热力图/年结里消失。
 * 编辑计划只有「YYYY-MM-DD」文本框，连个日历都点不出来 —— 用户原话「纯靠自己瞎弄」。
 *
 * 对齐 Web 版 `renderDtpGrid`：月历（周一起始、今天描边、选中靛蓝）+ 时/分加减。
 * 时间单位（时/分）用 −/+ 而不是输入框：小屏上不用弹键盘，点着调最快。
 */
@Composable
fun GlassDateTimePicker(
    /** 初始值："yyyy-MM-dd" 或 "yyyy-MM-dd'T'HH:mm:ss"（也吃空格分隔）*/
    initial: String,
    /** false = 只选日期（编辑计划）；true = 日期 + 时分（编辑记录）*/
    withTime: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    /** 回传："yyyy-MM-dd"（withTime=false）或 "yyyy-MM-dd'T'HH:mm:ss"（withTime=true）*/
    onConfirm: (String) -> Unit
) {
    val isDark = LocalIsDark.current
    val cal = remember(initial) { parseToCalendar(initial) }

    var year by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(cal.get(Calendar.MONTH) + 1) }
    var day by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(cal.get(Calendar.MINUTE)) }

    // 今天（用于描边：让人一眼知道"今天在哪"）
    val today = remember {
        Calendar.getInstance().let {
            Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
        }
    }

    val daysInMonth = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month - 1); set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val lead = remember(year, month) {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month - 1); set(Calendar.DAY_OF_MONTH, 1)
        }
        // 周日=1 → 周一起始的 0..6
        (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
    // ★2026-11-05 气味扫描：这里原本是组合期直接改 state
    //   （`if (day > daysInMonth) day = daysInMonth`）。能跑，但属于
    //   「组合期写 state」—— Compose 明确不建议：一旦夹不回去就是重组死循环。
    //   放 LaunchedEffect 里，只在年/月变化后纠正一次。
    androidx.compose.runtime.LaunchedEffect(year, month, daysInMonth) {
        if (day > daysInMonth) day = daysInMonth
    }

    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.92f,
        corner = 22
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ── 标题 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (withTime) "选择日期时间" else "选择日期",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── 月份切换 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepArrow(Icons.Outlined.ChevronLeft, "上个月") {
                    if (month == 1) { month = 12; year -= 1 } else month -= 1
                }
                Text(
                    "${year} 年 ${month} 月",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                StepArrow(Icons.Outlined.ChevronRight, "下个月") {
                    if (month == 12) { month = 1; year += 1 } else month += 1
                }
            }
            Spacer(Modifier.height(8.dp))

            // ── 星期 ──
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                    Text(
                        w, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // ── 日期网格 ──
            val rows = (lead + daysInMonth + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until 7) {
                            val d = r * 7 + c - lead + 1
                            if (d < 1 || d > daysInMonth) {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val sel = d == day
                                val isToday = year == today.first && month == today.second && d == today.third
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                sel -> Color(0xFF6366F1)
                                                isDark -> Color(0xFF94A3B8).copy(alpha = 0.12f)
                                                else -> Color(0xFF94A3B8).copy(alpha = 0.15f)
                                            }
                                        )
                                        .then(
                                            // 今天：一圈浅浅的靛蓝描边（选中态本身就是实心靛蓝，不必再描）
                                            if (isToday && !sel) Modifier.border(
                                                1.dp,
                                                Color(0xFF6366F1).copy(alpha = if (isDark) 0.7f else 0.5f),
                                                RoundedCornerShape(8.dp)
                                            ) else Modifier
                                        )
                                        .safeClickable {
                                            day = d
                                            // ★2026-11-05 用户：「选完月份再自动关弹窗」
                                            //   纯日期模式（编辑计划）选完那一天就直接回传关闭，
                                            //   不用再找「确定」——少一步，也避免"选完以为已经生效"。
                                            //   带时间的模式要选时分，仍保留「确定」。
                                            if (!withTime) {
                                                onConfirm(
                                                    String.format(Locale.US, "%04d-%02d-%02d", year, month, d)
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$d", fontSize = 12.5.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            sel -> Color.White
                                            isDark -> Color(0xFFF3F4F6).copy(alpha = 0.75f)
                                            else -> Color(0xFF334155).copy(alpha = 0.8f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 时 / 分 ──
            if (withTime) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("时")
                        Stepper(
                            value = hour,
                            onMinus = { hour = if (hour == 0) 23 else hour - 1 },
                            onPlus = { hour = if (hour == 23) 0 else hour + 1 }
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("分")
                        Stepper(
                            value = minute,
                            step = 5,
                            onMinus = { minute = if (minute < 5) 55 else minute - 5 },
                            onPlus = { minute = if (minute >= 55) 0 else minute + 5 }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) { GlassButton("取消", com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current) }
                Box(Modifier.weight(1f)) {
                    GlassButton(
                        "确定",
                        onClick = {
                            val out = if (withTime) {
                                String.format(
                                    Locale.US, "%04d-%02d-%02dT%02d:%02d:00",
                                    year, month, day, hour, minute
                                )
                            } else {
                                String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
                            }
                            onConfirm(out)
                        },
                        primary = true
                    )
                }
            }
        }
    }
}

/** 左右切换箭头（月 / 年） */
@Composable
private fun StepArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .glassStatic(corner = 8, flat = true, veil = 0.22f)
            .safeClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, label, tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** − / + 步进器（时、分）。中间是可以直接点的数值。 */
@Composable
private fun Stepper(
    value: Int,
    step: Int = 1,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassStatic(corner = Radii.INPUT, flat = true, veil = 0.22f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .safeClickable(onClick = onMinus)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("−", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            String.format(Locale.US, "%02d", value),
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = Ink
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .safeClickable(onClick = onPlus)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("＋", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  心情 / 天气选择器
// ══════════════════════════════════════════════════════════════════

/** 心情选项（对齐 Web 版 MOOD_OPTIONS：emoji + 文字，**存文字**） */
val MoodOptions: List<Pair<String, String>> = listOf(
    "😄" to "开心", "😌" to "平静", "🤩" to "兴奋", "😮‍💨" to "疲惫"
)

/** 天气选项（对齐 Web 版 WEATHER_OPTIONS；**存文字**，这样里程碑「天气收藏家」才认得） */
val WeatherOptions: List<Pair<String, String>> = listOf(
    "☀️" to "晴", "🌤️" to "多云", "☁️" to "阴", "🌧️" to "雨", "❄️" to "雪"
)

/**
 * 心情 / 天气选择器。
 *
 * ★存的是**文字**（"晴"），不是 emoji —— Web 版存的是 emoji，
 * 但原生版的「天气收藏家」里程碑是按文字匹配的（`晴天雨天`那套）。
 * 若这里存 emoji，成就永远解锁不了。emoji 只作为选项目前的视觉标识。
 */
@Composable
fun MoodWeatherPicker(
    isMood: Boolean,
    current: String,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val options = if (isMood) MoodOptions else WeatherOptions
    var picked by remember { mutableStateOf(current.trim()) }

    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.88f,
        corner = 22
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isMood) "选择心情" else "选择天气",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            // 两列网格：emoji 大、文字小，一眼能扫
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (emoji, label) ->
                            val on = picked == label
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .glassStatic(
                                        corner = 12,
                                        flat = true,
                                        veil = if (on) 0.62f else 0.25f
                                    )
                                    // ★选中态必须"一眼看出来"：白卡上只调 veil 根本看不出，
                                    //   叠一层淡靛蓝 + 靛蓝粗体字才是选中感
                                    .then(
                                        if (on) Modifier.background(
                                            Color(0xFF6366F1).copy(alpha = 0.14f),
                                            RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .safeClickable { picked = if (on) "" else label }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    label, fontSize = 13.5.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                    color = if (on) Color(0xFF4F46E5)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (on) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "✓", fontSize = 12.sp,
                                        color = Color(0xFF4F46E5),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        // 奇数个选项时补一个空位，保持两列对齐
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .glassStatic(corner = 10, flat = true, veil = 0.16f)
                    .safeClickable { picked = "" }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "清空", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) { GlassButton("取消", com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current) }
                Box(Modifier.weight(1f)) {
                    GlassButton("确定", onClick = { onConfirm(picked) }, primary = true)
                }
            }
        }
    }
}

/**
 * 只读的"点开选择"字段 —— 外观和输入框一致，点击弹选择器。
 * 用它替掉原来那几个裸 TextField（日期/心情/天气），用户就不用再手打格式了。
 */
@Composable
fun PickerField(
    value: String,
    placeholder: String,
    emoji: String? = null,
    center: Boolean = true,
    onClick: () -> Unit
) {
    val isDark = LocalIsDark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassStatic(corner = Radii.INPUT, flat = true, veil = 0.22f)
            .safeClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji != null && value.isNotBlank()) {
            Text(emoji, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            value.ifBlank { placeholder },
            fontSize = 13.5.sp,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            else Ink,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            androidx.compose.material.icons.Icons.Outlined.ChevronRight, null,
            tint = if (isDark) Color.White.copy(alpha = 0.35f) else Color(0xFF0F172A).copy(alpha = 0.25f),
            modifier = Modifier.size(13.dp)
        )
    }
}

/** 把各种写法的日期字符串解析成 Calendar（解析失败退回今天） */
private fun parseToCalendar(raw: String): Calendar {
    val s = raw.trim().replace(' ', 'T').replace('/', '-')
    for (p in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")) {
        try {
            val f = SimpleDateFormat(p, Locale.getDefault())
            f.isLenient = false
            f.parse(s)?.let { return Calendar.getInstance().apply { time = it } }
        } catch (e: Exception) {
            // 换下一种格式
        }
    }
    return Calendar.getInstance().apply { time = Date() }
}

/** 心情/天气值 → 对应 emoji（回显用；值本身存文字） */
fun emojiOf(value: String, isMood: Boolean): String? {
    val list = if (isMood) MoodOptions else WeatherOptions
    return list.firstOrNull { it.second == value.trim() }?.first
}
