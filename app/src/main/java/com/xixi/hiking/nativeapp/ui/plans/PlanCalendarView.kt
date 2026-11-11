package com.xixi.hiking.nativeapp.ui.plans

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.PlannedTripEntity
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.glassPanel
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.DangerRed

/**
 * 计划日历（月视图）—— 对齐 Web 版 `renderPlannedCalendar`：
 *
 * - 有计划的日期右下角打点
 * - **今天**：1px 描边；**选中**：2px 描边 + 底色填充（选中优先）
 * - 日历下方是**明细区**：
 *     · 没点日期 → 显示**整月全部计划**（按日期分组，每组一个日期标记）
 *     · 点了某天 → 只显示**那天**，右上角「整月」可以退回
 *
 * ★ 之前原生版只有「点一下高亮一个框」，明细区根本没有 —— 日历点下去什么反应都没有，
 *   这是和 Web 版差距最大的一处。
 */
@Composable
fun PlanCalendarView(
    plans: List<PlannedTripEntity>,
    onOpen: (PlannedTripEntity) -> Unit,
    onComplete: (PlannedTripEntity) -> Unit = {},
    onDelete: (PlannedTripEntity) -> Unit = {}
) {
    val today = remember { Calendar.getInstance() }

    // ★★2026-11-03 用户：「计划页的日期页，也显示有任务的那一页，而不是最新页空荡荡的」
    //   原来默认停在**日历当月** —— 这个月没排计划时，日历是一片空白，看不出任何信息。
    //   现在默认落在**最近一条计划所在的月份**；没有任何计划才退回当月。
    val anchor: Calendar = remember(plans) {
        val latest = plans.maxByOrNull { it.plannedDate }?.plannedDate
        val parsed = latest?.let {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.take(10))
            }.getOrNull()
        }
        Calendar.getInstance().apply { if (parsed != null) time = parsed }
    }
    var year by remember { mutableStateOf(anchor.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(anchor.get(Calendar.MONTH)) }   // 0-based
    var selectedKey by remember { mutableStateOf<String?>(null) }

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayKey = remember { fmt.format(Date()) }
    val isDark = LocalIsDark.current
    val byDate = remember(plans) { plans.groupBy { it.plannedDate } }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // ── 月份切换 ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.75f))
                    .border(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                    .safeClickable {
                        if (month == 0) { month = 11; year-- } else month--
                        selectedKey = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ChevronLeft, "上个月", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${year}年${month + 1}月",
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.75f))
                    .border(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                    .safeClickable {
                        if (month == 11) { month = 0; year++ } else month++
                        selectedKey = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ChevronRight, "下个月", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
            }
        }

        // ★2026-11-03 去掉日历外层白底框：日历直接铺在页面上，格子可以宽一点
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            // ── 星期表头 ──────────────────────────────────────────
            Row(Modifier.fillMaxWidth()) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                    Text(
                        w, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // ── 日期网格 ──────────────────────────────────────────
            val first = remember(year, month) {
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, 1)
                }
            }
            val firstDow = first.get(Calendar.DAY_OF_WEEK) - 1        // 0 = 周日
            val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            val rows = (firstDow + daysInMonth + 6) / 7

            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val idx = r * 7 + c
                        val day = idx - firstDow + 1
                        // ★固定 42dp 高：原来用 aspectRatio(1f)，宽屏下每格变成 50dp 见方，
                        //   一个月就铺掉了大半屏，而且最后一行下方留出一大块空白。
                        Box(
                            modifier = Modifier.weight(1f).height(42.dp).padding(1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day in 1..daysInMonth) {
                                val key = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                                val dayPlans = byDate[key].orEmpty()
                                val has = dayPlans.isNotEmpty()
                                val isToday = key == todayKey
                                val isSel = key == selectedKey
                                val shape = RoundedCornerShape(10.dp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clip(shape)
                                        .background(
                                            if (isSel) AccentIndigo.copy(alpha = if (isDark) 0.24f else 0.16f)
                                            else Color.Transparent
                                        )
                                        .then(
                                            when {
                                                isSel -> Modifier.border(2.dp, AccentIndigo, shape)
                                                isToday -> Modifier.border(1.dp, AccentIndigo.copy(alpha = 0.75f), shape)
                                                else -> Modifier
                                            }
                                        )
                                        // ★2026-11-03 补震动 + 兜异常
                                        .safeClickable(enabled = has || isSel) {
                                            selectedKey = if (isSel) null else key
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "$day",
                                            fontSize = 13.sp,
                                            color = if (isSel || isToday) AccentIndigo else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                        // ★有计划的圆点：Web 版是 6px 实心圆（强调色），不是绿点
                                        if (has) {
                                            Spacer(Modifier.height(2.dp))
                                            Box(
                                                Modifier.size(5.dp).clip(CircleShape)
                                                    .background(AccentIndigo)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══ 明细区 ═════════════════════════════════════════════════
        val monthPrefix = String.format(Locale.US, "%04d-%02d-", year, month + 1)
        val monthPlans = plans.filter { it.plannedDate.startsWith(monthPrefix) }

        Spacer(Modifier.height(14.dp))

        if (selectedKey != null) {
            // ── 单日明细 ────────────────────────────────────────────
            val key = selectedKey!!
            val list = byDate[key].orEmpty()
            DayDetailHeader(
                title = cnDate(key),
                count = list.size,
                onBackToMonth = { selectedKey = null }
            )
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                EmptyHint("这天没有计划")
            } else {
                list.forEach { p ->
                    PlanDetailRow(p, onOpen = onOpen, onComplete = onComplete, onDelete = onDelete)
                    Spacer(Modifier.height(6.dp))
                }
            }
        } else {
            // ── 整月明细（按日期分组）────────────────────────────────
            if (monthPlans.isEmpty()) {
                EmptyHint("这个月还没有安排")
            } else {
                Text(
                    "本月计划 ${monthPlans.size} 条",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                monthPlans.groupBy { it.plannedDate }.toSortedMap().forEach { (key, list) ->
                    // 日期分组标记：图标 + 中文日期 + 星期 + 渐隐分隔线
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Event, null,
                            tint = Ink, modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            cnDate(key), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.weight(1f).height(1.dp)
                                .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFF94A3B8).copy(alpha = 0.22f))
                        )
                    }
                    list.forEach { p ->
                        PlanDetailRow(p, onOpen = onOpen, onComplete = onComplete, onDelete = onDelete)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

/** 单日明细的日期头：图标 + 中文日期 + 条数 + 「整月」返回 */
@Composable
private fun DayDetailHeader(title: String, count: Int, onBackToMonth: () -> Unit) {
    val isDark = LocalIsDark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.62f))
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.12f) else Color.White,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Event, null, tint = Ink, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.width(8.dp))
        Text(
            "计划 $count 条", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .liquidGlassChip(corner = Radii.INPUT)
                .safeClickable(onClick = onBackToMonth)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text("整月", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 明细里的一条计划：名称 + 状态徽章 + Lv/海拔 + 完成/删除 */
@Composable
private fun PlanDetailRow(
    plan: PlannedTripEntity,
    onOpen: (PlannedTripEntity) -> Unit,
    onComplete: (PlannedTripEntity) -> Unit,
    onDelete: (PlannedTripEntity) -> Unit
) {
    val isDark = LocalIsDark.current
    val d = daysUntil(plan.plannedDate)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.White)
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.10f) else Color.White,
                RoundedCornerShape(10.dp)
            )
            .safeClickable { onOpen(plan) }
            .padding(start = 11.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plan.name.ifBlank { "未命名" },
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                val badge = relBadge(d)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    val (txt, col) = badge
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(col.copy(alpha = 0.13f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(txt, fontSize = 10.sp, color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("难度 ${plan.difficulty} 级")
                    if (plan.elevation > 0) append(" · ${plan.elevation}m")
                    if (plan.location.isNotBlank()) append(" · ${plan.location}")
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))

        // 完成（绿）
        Box(
            modifier = Modifier
                .liquidGlassChip(corner = Radii.INPUT)
                .safeClickable { onComplete(plan) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // ★已到期（当天/过期）→ 「完成/延期」，和 Web 版一致：
            //   日子到了不等于走完了，先让用户选一下
            Text(
                if (d <= 0) "完成/延期" else "完成",
                fontSize = 12.sp,
                color = if (isDark) Color(0xFF6EE7A8) else Color(0xFF15803D),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(5.dp))
        // 删除（灰蓝，跟 Web 版一致 —— 不是红色，删除计划不是危险操作）
        Box(
            modifier = Modifier
                .liquidGlassChip(corner = Radii.INPUT)
                .safeClickable { onDelete(plan) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                "删除", fontSize = 12.sp,
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 2026-11-08 → 11月8日 周日 */
private fun cnDate(key: String): String {
    val parts = key.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: return key
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return key
    val d = parts.getOrNull(2)?.toIntOrNull() ?: return key
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, y); set(Calendar.MONTH, m - 1); set(Calendar.DAY_OF_MONTH, d)
    }
    val wd = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[cal.get(Calendar.DAY_OF_WEEK) - 1]
    return "${m}月${d}日 $wd"
}

/** 相对今天的状态徽章（Web 版 pl-overdue / pl-today / pl-tomorrow） */
private fun relBadge(d: Int): Pair<String, Color>? = when {
    d < 0 -> "已过期 ${-d} 天" to DangerRed
    d == 0 -> "今天" to Color(0xFF4F46E5)
    d == 1 -> "明天" to Color(0xFF0EA5E9)
    else -> null
}
