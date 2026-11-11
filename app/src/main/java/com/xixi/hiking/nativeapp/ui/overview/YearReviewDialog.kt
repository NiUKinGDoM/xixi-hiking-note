package com.xixi.hiking.nativeapp.ui.overview

import com.xixi.hiking.nativeapp.ui.safeClickable
import com.xixi.hiking.nativeapp.ui.theme.Radii
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.data.parseRecordDate
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import java.util.Calendar
import java.util.Locale
import com.xixi.hiking.nativeapp.ui.theme.Ink

/** 某一年的统计快照 */
data class YearStats(
    val year: Int,
    val has: Boolean,
    val n: Int = 0,
    val km: Double = 0.0,
    val min: Int = 0,
    val peaks: Int = 0,
    val avgKm: Double = 0.0,
    val mostOften: String = "—",
    val maxCnt: Int = 0,
    val maxEl: Int = 0,
    val maxElName: String = "",
    val lines: List<String> = emptyList()
)

private fun yearRecordsOf(records: List<RecordEntity>, year: Int) = records.filter { r ->
    val d = parseRecordDate(r.createdAt) ?: return@filter false
    val c = Calendar.getInstance().apply { time = d }
    c.get(Calendar.YEAR) == year
}

private fun ymd(raw: String): String {
    val d = parseRecordDate(raw) ?: return ""
    val c = Calendar.getInstance().apply { time = d }
    return "${c.get(Calendar.YEAR)}-" +
        (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0') + "-" +
        c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
}

/** ★1:1 对齐 Web 版 calcYearStats */
fun calcYearStats(records: List<RecordEntity>, year: Int): YearStats {
    val list = yearRecordsOf(records, year)
    if (list.isEmpty()) return YearStats(year = year, has = false)

    val km = list.sumOf { it.distance }
    val min = list.sumOf { it.duration }
    val nameCnt = list.groupingBy { it.name.trim() }.eachCount()
    val peaks = nameCnt.keys.count { it.isNotEmpty() }
    val maxEntry = nameCnt.maxByOrNull { it.value }
    val mostOften = if (maxEntry != null && maxEntry.value >= 2) maxEntry.key else "—"
    val el = list.maxByOrNull { it.elevation }

    val companionCounts = HashMap<String, Int>()
    list.forEach { r ->
        r.companions.split(Regex("[,，、\\s]+")).forEach { s ->
            val t = s.trim()
            if (t.isNotEmpty()) companionCounts[t] = (companionCounts[t] ?: 0) + 1
        }
    }
    val topCompanion = companionCounts.maxByOrNull { it.value }

    val sorted = list.sortedBy { it.createdAt }
    val early = sorted.firstOrNull()
    val late = sorted.lastOrNull()

    val lines = mutableListOf<String>()
    list.maxByOrNull { it.distance }?.takeIf { it.distance > 0 }?.let {
        lines.add("最远单程 · ${it.name} · ${String.format(Locale.getDefault(), "%.1f", it.distance)} km")
    }
    list.maxByOrNull { it.duration }?.takeIf { it.duration > 0 }?.let {
        lines.add("最长时间 · ${it.name} · ${fmtDuration(it.duration)}")
    }
    if ((el?.elevation ?: 0) > 0) lines.add("最高海拔 · ${el?.name ?: ""} · ${el?.elevation} m")
    if (maxEntry != null && maxEntry.value >= 2) lines.add("最常去 · ${maxEntry.key}（${maxEntry.value} 次）")
    if (topCompanion != null && topCompanion.value >= 2) {
        lines.add("最多同行 · ${topCompanion.key}（${topCompanion.value} 次）")
    }
    early?.let { lines.add("第一次出发 · ${ymd(it.createdAt)} · ${it.name}") }
    if (late != null && late !== early) lines.add("最后一次出发 · ${ymd(late.createdAt)} · ${late.name}")
    if (lines.isEmpty()) lines.add("这一年没有更多之最，去徒步吧！")

    return YearStats(
        year = year, has = true, n = list.size,
        km = km, min = min, peaks = peaks, avgKm = km / list.size,
        mostOften = mostOften, maxCnt = maxEntry?.value ?: 0,
        maxEl = el?.elevation ?: 0, maxElName = el?.name ?: "",
        lines = lines
    )
}

/**
 * ★年度回顾（1:1 对齐 Web 版 renderYearReview）：
 *   年份左右切换 + 6 指标 3 列网格 + 「年度之最」清单
 *   可切「和去年比」：去年值划线 + 今年值 + 增幅
 */
@Composable
fun YearReviewDialog(
    records: List<RecordEntity>,
    hazeState: HazeState,
    initialYear: Int,
    onDismiss: () -> Unit
) {
    var year by remember { mutableStateOf(initialYear) }
    var compare by remember { mutableStateOf(false) }
    val cur = remember(records, year) { calcYearStats(records, year) }
    val last = remember(records, year) { calcYearStats(records, year - 1) }
    // 去年没数据就自动退回单版（与 Web 版一致，避免一排 0）
    val cmp = compare && last.has

    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.92f,
        corner = 22,
        maxHeightFraction = 0.80f
    ) {
        // ★★2026-11-11 存图改成**独立绘制**（YearReviewImage），不再截界面。
        //   上一版用 GraphicsLayer 录屏，真机保存直接失败：toImageBitmap() 在 GPU 下
        //   返回 hardware bitmap，asAndroidBitmap() 之后再 Canvas 绘制会抛异常。
        //   而且截弹窗会带上圆角/半透明玻璃/裁切边 —— 它本来就不是分享图。
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val darkNow = LocalIsDark.current
        // ★强调色先在这里取出来：AccentIndigo 是 @Composable getter，
        //   而导出在协程里跑（非组合作用域），到那一步再读就编译不过。
        val accentInt = AccentIndigo.toArgb()
        var saving by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxWidth()) {
            // 标题 + 年份切换
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "年度回顾", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ChevronLeft, "上一年",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).safeClickable { year -= 1 }
                )
                Text(
                    "$year", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Icon(
                    Icons.Filled.ChevronRight, "下一年",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).safeClickable { year += 1 }
                )
                Spacer(Modifier.width(6.dp))
                // ★2026-11-11 「存成图片」挪到标题行右侧。
                //   原来放在内容末尾，被弹窗 80% 高度限制 + 底部关闭条挤掉，
                //   用户根本点不到。放在操作行里永远可见，也不占内容高度。
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentIndigo.copy(alpha = 0.10f))
                        .safeClickable {
                            if (saving || !cur.has) return@safeClickable
                            saving = true
                            scope.launch {
                                try {
                                    val bmp = YearReviewImage.render(
                                        cur, darkNow, accentInt
                                    )
                                    if (bmp == null) {
                                        android.widget.Toast.makeText(
                                            ctx, "生成图片失败", android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val ok = com.xixi.hiking.nativeapp.data.GallerySaver
                                            .saveBitmap(ctx, bmp, "年度回顾-$year")
                                        android.widget.Toast.makeText(
                                            ctx,
                                            if (ok) "已存到相册" else "保存失败，请检查相册权限",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (t: Throwable) {
                                    android.util.Log.w("hiking", "年度回顾存图失败", t)
                                    android.widget.Toast.makeText(
                                        ctx, "保存失败", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    saving = false
                                }
                            }
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Download, null,
                            tint = AccentIndigo,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (saving) "生成中" else "存图",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = AccentIndigo
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Spacer(Modifier.height(8.dp))

            if (!cur.has) {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$year 年还没留下徒步足迹",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "去「记录」页添加第一条吧",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                return@Column
            }

            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // 对比开关
                // ★原来未选中时是「灰底灰字」，和禁用态几乎一样，用户不知道能点。
                //   改成「未选中：描边白底；选中：实心强调色」——一眼看出这是个开关。
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.BUTTON.dp))
                        .background(
                            if (compare) AccentIndigo
                            else if (LocalIsDark.current) Color.White.copy(alpha = 0.07f)
                            else Color.White
                        )
                        .border(
                            1.dp,
                            if (compare) AccentIndigo
                            else AccentIndigo.copy(alpha = 0.35f),
                            RoundedCornerShape(Radii.BUTTON.dp)
                        )
                        .safeClickable { compare = !compare }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (compare) "和去年比 ✓" else "和去年比",
                        fontSize = 12.sp,
                        color = if (compare) com.xixi.hiking.nativeapp.ui.theme.OnAccent
                        else Ink,
                        fontWeight = if (compare) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                if (compare && !last.has) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${year - 1} 年没有记录，无法对比",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 6 指标 3 列 × 2 行
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        label = "徒步次数",
                        value = "${cur.n}",
                        unit = "次",
                        oldValue = if (cmp) "${last.n}" else null,
                        change = if (cmp) pctChange(cur.n.toDouble(), last.n.toDouble()) else null,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "总里程",
                        value = String.format(Locale.getDefault(), "%.1f", cur.km),
                        unit = "km",
                        oldValue = if (cmp) String.format(Locale.getDefault(), "%.1f", last.km) else null,
                        change = if (cmp) pctChange(cur.km, last.km) else null,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "总用时",
                        value = durNum(cur.min),
                        unit = durUnit(cur.min),
                        oldValue = if (cmp) durNum(last.min) + durUnit(last.min) else null,
                        change = if (cmp) pctChange(cur.min.toDouble(), last.min.toDouble()) else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        label = "打卡山峰",
                        value = "${cur.peaks}", unit = "座",
                        oldValue = if (cmp) "${last.peaks}" else null,
                        change = if (cmp) diffChange((cur.peaks - last.peaks).toDouble(), "") else null,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "平均里程",
                        value = String.format(Locale.getDefault(), "%.1f", cur.avgKm), unit = "km",
                        oldValue = if (cmp) String.format(Locale.getDefault(), "%.1f", last.avgKm) else null,
                        change = if (cmp) pctChange(cur.avgKm, last.avgKm) else null,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "最常去",
                        value = cur.mostOften, unit = "",
                        oldValue = null, change = null,
                        modifier = Modifier.weight(1f), small = true
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 年度之最
                Text(
                    "年度之最",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                cur.lines.forEach { line ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AccentIndigo.copy(alpha = 0.55f))
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            line, fontSize = 12.sp, lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    oldValue: String?,
    change: String?,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    val isDark = LocalIsDark.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDark) Color.White.copy(alpha = 0.06f)
                else Color.White.copy(alpha = 0.55f)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 9.dp)
    ) {
        Text(
            label, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        if (oldValue != null) {
            // 去年值划线（对比态）
            Text(
                oldValue,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                maxLines = 1
            )
            Spacer(Modifier.height(1.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = if (small) 12.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(1.dp))
                Text(
                    unit, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        if (change != null) {
            Text(
                change,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (change.startsWith("-")) Color(0xFF10B981) else if (change.startsWith("+")) Color(0xFFEF4444) else Color(0xFF94A3B8),
                maxLines = 1
            )
        }
    }
}

private fun durNum(min: Int): String =
    if (min >= 60) {
        val h = Math.round(min / 60.0 * 10) / 10.0
        if (h % 1.0 == 0.0) h.toInt().toString() else String.format(Locale.getDefault(), "%.1f", h)
    } else min.toString()

private fun durUnit(min: Int): String = if (min >= 60) "h" else "min"

private fun pctChange(cur: Double, last: Double): String {
    val d = cur - last
    if (Math.abs(d) < 1e-9) return "持平"
    if (last <= 0.0) return "新增"
    val pct = Math.round(Math.abs(d) / last * 100)
    return (if (d > 0) "+" else "-") + "$pct%"
}

private fun diffChange(d: Double, unit: String): String {
    if (Math.abs(d) < 1e-9) return "持平"
    return (if (d > 0) "+" else "-") + "${Math.round(Math.abs(d))}$unit"
}
