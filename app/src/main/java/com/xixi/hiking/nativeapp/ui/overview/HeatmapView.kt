package com.xixi.hiking.nativeapp.ui.overview

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.data.parseRecordDate
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.glassPanel
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import java.util.Calendar
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.liquid.glassStatic

/**
 * ★徒步足迹热力图（1:1 对齐 Web 版 renderHeatmap）：
 *   卡片头「🔥 徒步足迹」+ 年月切换（有数据的月份才可选）
 *   7 列日历（周一起始），有记录的那天 = 半透明红玻璃块 + 红细描边 + 深红字
 *   底部汇总：「本月徒步 N 次 · 累计爬升 Xm」（历史月写「2026年3月徒步 …」防歧义）
 *   点有记录的天 → 列出当天每一条记录
 */
private data class MonthBucket(
    val days: Map<Int, Int> = emptyMap(),
    val count: Int = 0,
    val climb: Int = 0
)

@Composable
fun HeatmapCard(
    records: List<RecordEntity>,
    hazeState: HazeState,
    onOpenRecord: (RecordEntity) -> Unit,
    onOpenReview: (Int) -> Unit = {},
    /**
     * ★★弹窗上抛（2026-11-02）：原来「选择年月」「某天记录」两个弹窗是在**卡片内部**渲染的，
     * 于是被外层滚动 Column 的约束限住 —— 表现就是"弹窗从卡片下面拉出来"，
     * 而不是浮在整页上。现在改成回调，由 OverviewScreen 在**根级 Box** 里渲染。
     */
    onRequestPicker: (PickerRequest) -> Unit = {},
    onRequestDay: (DayRequest) -> Unit = {}
) {
    val isDark = LocalIsDark.current
    val now = remember { Calendar.getInstance() }
    val todayY = now.get(Calendar.YEAR)
    val todayM = now.get(Calendar.MONTH) + 1

    var showPicker by remember { mutableStateOf(false) }
    var dayDetail by remember { mutableStateOf<Pair<Int, Int>?>(null) }   // (year*100+month, day)

    // ★一次遍历按年月分桶：切月不再全量扫 records（对齐 Web 版 hmMonthAgg 的思路）
    val buckets = remember(records) {
        val map = HashMap<Long, MonthBucket>()
        val days = HashMap<Long, HashMap<Int, Int>>()
        val counts = HashMap<Long, Int>()
        val climbs = HashMap<Long, Int>()
        records.forEach { r ->
            val d = parseRecordDate(r.createdAt) ?: return@forEach
            val c = Calendar.getInstance().apply { time = d }
            val key = c.get(Calendar.YEAR) * 100L + (c.get(Calendar.MONTH) + 1)
            val day = c.get(Calendar.DAY_OF_MONTH)
            days.getOrPut(key) { HashMap() }[day] = (days[key]?.get(day) ?: 0) + 1
            counts[key] = (counts[key] ?: 0) + 1
            climbs[key] = (climbs[key] ?: 0) + r.elevation
        }
        (days.keys + counts.keys).forEach { k ->
            map[k] = MonthBucket(days[k] ?: emptyMap(), counts[k] ?: 0, climbs[k] ?: 0)
        }
        map
    }

    // ★★2026-11-03 用户：「徒步足迹默认显示最新的那一天，别这个月没去过就显示这个月空白」
    //   原来默认取**日历当月** —— 如果这个月还没出门，卡片就是一整片空白格子，
    //   打开就"我啥也没干"。改成默认落在**最近一次有记录的月份**（都没记录才退回当月）。
    val latestYm = remember(buckets) { buckets.keys.maxOrNull() }
    // ★★★2026-11-04 用户：「热力图现在还是默认是没有东西的月份啊」
    //
    //   上一版写成 `remember { mutableStateOf(latestYm...) }` —— 只在那一次组合时求值。
    //   而 records 是异步从数据库来的：**首帧 buckets 必定为空**，于是永远退回"今天所在的月"，
    //   等数据到了也不会再改。表现就是"默认那个月什么也没有"。
    //   现在：先给今天兜底，等 buckets 一到位再**跳一次**到最近有记录的月份；
    //   用户手动选过年月（userPicked）之后就不再自动跳，免得抢用户的选择。
    var year by remember { mutableStateOf(todayY) }
    var month by remember { mutableStateOf(todayM) }
    var userPicked by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(latestYm, userPicked) {
        if (!userPicked && latestYm != null && latestYm > 0L) {
            year = (latestYm / 100).toInt()
            month = (latestYm % 100).toInt()
        }
    }
    val bucket = buckets[year * 100L + month] ?: MonthBucket()

    // 有记录的年（倒序）+ 今年，保证当前选中的年一定在列表里
    val years = remember(records, todayY) {
        val s = sortedSetOf<Int>(compareByDescending { it })
        s.add(todayY)
        buckets.keys.forEach { s.add((it / 100).toInt()) }
        s.add(year)
        s.toList()
    }
    // ★2026-11-05 按年给出"有记录的月份"：弹窗里先选年、再选月，
    //   所以每个年份都需要自己的一份，不能只算当前年（用户：「点 2025 年弹窗就关了」）。
    val monthsByYear = remember(buckets, years) {
        years.associateWith { y ->
            buckets.keys.filter { (it / 100).toInt() == y }.map { (it % 100).toInt() }.toSet()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(corner = Radii.CARD)
            .padding(14.dp)
    ) {
        // ── 卡片头 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ★Web 版这个图标是红色（text-red-400 #f87171），形状是 local_fire_department（= Whatshot）
            Icon(
                Icons.Filled.Whatshot, null,
                tint = if (isDark) Color(0xFFF87171) else Color(0xFFEF4444),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "徒步足迹",
                // 与「统计概览」同级：17sp（Web h2 是 text-2xl，手机端收到 17）
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            // 年月按钮
            Row(
                modifier = Modifier
                    .glassStatic(corner = 10, flat = true, veil = 0.30f)
                    .safeClickable {
                        onRequestPicker(
                            PickerRequest(
                                years = years,
                                monthsByYear = monthsByYear,
                                year = year,
                                month = month,
                                onPick = { y, m -> userPicked = true; year = y; month = m }
                            )
                        )
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${year}年${month}月",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Outlined.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            // ★年度回顾入口（对齐 Web 版热力图头的「回顾」按钮）
            Row(
                // ★和年月按钮同一套平面材质、同样的内边距和字号（2026-11-02 统一按钮）
                modifier = Modifier
                    .glassStatic(corner = 10, flat = true, veil = 0.30f)
                    .safeClickable { onOpenReview(year) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CalendarMonth, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "回顾", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 星期表头（周一起始）──
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                Text(
                    w, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // ── 日历网格 ──
        val daysInMonth = remember(year, month) {
            Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        val firstWeekdayMon0 = remember(year, month) {
            val c = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // 周日=1 → 0..6 变周一起始
        }
        val cells = firstWeekdayMon0 + daysInMonth
        val rows = (cells + 6) / 7

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 7) {
                        val idx = row * 7 + col
                        val day = idx - firstWeekdayMon0 + 1
                        if (day < 1 || day > daysInMonth) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val cnt = bucket.days[day] ?: 0
                            val isRed = cnt > 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    // ★★2026-11-02 改回 Web 的**方形格**（.hm-day aspect-ratio 1/1）：
                                    //   之前压成 38dp 高，格子是扁长方形，和 Web 版一眼就能看出不一样。
                                    .aspectRatio(1f)
                                    // Web .hm-day border-radius: 8px
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isRed && isDark -> Color(0xFFEF4444).copy(alpha = 0.16f)
                                            isRed -> Color(0xFFDC2626).copy(alpha = 0.22f)
                                            // ★普通日子必须有灰底 —— Web 版就是 rgba(148,163,184,0.15)。
                                            //   我上一版改成透明，结果整张卡只剩几个裸数字，完全不像热力图。
                                            // 0.15 叠在白卡上只有 #E8EBF3，缩略图/低亮度屏上基本看不见，
                                            // 会让人误以为「这个月只有一天」—— 提到 0.22 才像格子系统
                                            // ★Web 值：rgba(148,163,184,0.15)。
                                            //   0.10 在白卡上几乎看不见，整月会像"只有几个日期有格子"。
                                            isDark -> Color(0xFF94A3B8).copy(alpha = 0.12f)
                                            else -> Color(0xFF94A3B8).copy(alpha = 0.15f)
                                        }
                                    )
                                    .then(
                                        if (isRed) Modifier.border(
                                            1.dp,
                                            if (isDark) Color(0xFFF87171).copy(alpha = 0.40f)
                                            else Color(0xFFDC2626).copy(alpha = 0.45f),
                                            RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    // ★2026-11-03 换成 safeClickable：补震动 + 兜异常
                                    //   （点有记录的那天 → 开记录，是足迹页最核心的交互）
                                    .safeClickable(enabled = isRed) {
                                        onRequestDay(
                                            DayRequest(
                                                ym = year * 100 + month,
                                                day = day,
                                                records = records
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day",
                                    // Web .hm-day font-size: 13px / 600
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        isRed && isDark -> Color(0xFFFECACA)
                                        isRed -> Color(0xFFB91C1C)
                                        isDark -> Color(0xFFF3F4F6).copy(alpha = 0.65f)
                                        else -> Color(0xFF334155).copy(alpha = 0.55f)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(9.dp))

        // ── 汇总 ──
        val label = if (year == todayY && month == todayM) "本月" else "${year}年${month}月"
        Text(
            label + "徒步 ${bucket.count} 次" + if (bucket.climb > 0) " · 累计爬升 ${bucket.climb}m" else "",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

}

@Composable
fun DayRecordRow(r: RecordEntity, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f))
            .safeClickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(
            r.name.ifBlank { "未命名" },
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        val bits = buildList {
            add(fmt1(r.distance) + " km")
            add(fmtDuration(r.duration))
            add("${r.elevation} m")
            if (r.mood.isNotBlank()) add(r.mood)
            if (r.weather.isNotBlank()) add(r.weather)
            if (r.companions.isNotBlank()) add(r.companions)
        }
        Text(
            bits.joinToString(" · "),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun PickChip(
    text: String,
    on: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = LocalIsDark.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    on -> AccentIndigo
                    isDark -> Color.White.copy(alpha = 0.07f)
                    else -> Color.White.copy(alpha = 0.65f)
                }
            )
            .border(
                1.dp,
                when {
                    on -> AccentIndigo
                    isDark -> Color.White.copy(alpha = 0.12f)
                    else -> Color(0xFFE2E8F0)
                },
                RoundedCornerShape(10.dp)
            )
            .safeClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                on -> Color.White
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
        )
    }
}

internal fun fmt1(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", v)

internal fun fmtDuration(min: Int): String =
    if (min < 60) "${min}分" else {
        val h = min / 60
        val m = min % 60
        if (m == 0) "${h}小时" else "${h}小时${m}分"
    }

/** 年月选择请求：卡片里算好了候选年月，交给根级去渲染弹窗 */
data class PickerRequest(
    val years: List<Int>,
    /** 每个年份 → 该年有记录的月份（0..12）。选年之后月份格要跟着换。 */
    val monthsByYear: Map<Int, Set<Int>>,
    val year: Int,
    val month: Int,
    val onPick: (Int, Int) -> Unit
)

/** 某天记录请求 */
data class DayRequest(
    val ym: Int,
    val day: Int,
    val records: List<RecordEntity>
)
