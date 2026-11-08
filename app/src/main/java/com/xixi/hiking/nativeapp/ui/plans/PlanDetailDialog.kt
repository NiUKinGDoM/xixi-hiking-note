package com.xixi.hiking.nativeapp.ui.plans

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.PlannedTripEntity
import com.xixi.hiking.nativeapp.ui.GlassButton
import com.xixi.hiking.nativeapp.ui.GlassConfirm
import com.xixi.hiking.nativeapp.ui.FieldLabel
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.GlassSegmented
import com.xixi.hiking.nativeapp.ui.GlassTextField
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.records.diffLabel
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import com.xixi.hiking.nativeapp.ui.GlassDateTimePicker
import com.xixi.hiking.nativeapp.ui.PickerField

/** 计划详情 / 编辑（全自绘控件，同弹窗两态） */
@Composable
fun PlanDetailDialog(
    initial: PlannedTripEntity?,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onSave: (PlannedTripEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** ★「完成了 ✓」—— 把计划转成一条徒步记录。Web 版的核心闭环，原生版之前完全没有。 */
    onComplete: (() -> Unit)? = null,
    /** 「延期」= 直接以编辑态打开（对齐 Web 版 showCompleteOrDelayModal → openPlannedDetailModal(id,'edit')） */
    startInEdit: Boolean = false
) {
    var editing by remember { mutableStateOf(initial == null || startInEdit) }
    var confirmDelete by remember { mutableStateOf(false) }

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    // ★★必须 remember：原来每次重组都新建一个 id（当前毫秒）——
    //   弹窗里改一个字段就换一个主键，保存时拿到的是「这帧的 id」，行为不可预测。
    val base = remember(initial) {
        initial ?: PlannedTripEntity(
            id = "p" + System.currentTimeMillis(),
            name = "", plannedDate = fmt.format(Date()), location = "", note = "",
            difficulty = 3,
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        )
    }

    var name by remember { mutableStateOf(base.name) }
    var date by remember { mutableStateOf(base.plannedDate) }
    var location by remember { mutableStateOf(base.location) }
    var note by remember { mutableStateOf(base.note) }
    var difficulty by remember { mutableStateOf(base.difficulty.coerceIn(1, 5)) }
    var elevation by remember { mutableStateOf(if (base.elevation > 0) base.elevation.toString() else "") }
    var confirmComplete by remember { mutableStateOf(false) }
    // ★★2026-11-02 计划日期也改成点选（原来是个裸文本框，得手打 YYYY-MM-DD）
    var showDatePicker by remember { mutableStateOf(false) }

    GlassOverlay(
        bottomClose = false,
        hazeState = hazeState, onDismiss = onDismiss,
        maxWidthFraction = 0.88f, corner = 26
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题行（统一样式：DialogTitle，18sp/Black）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    com.xixi.hiking.nativeapp.ui.DialogTitle(
                        if (editing) (if (initial == null) "新建计划" else "编辑计划") else "计划详情",
                        icon = Icons.Outlined.CalendarToday
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (editing) {
                // ★每个字段都要有标签：只靠 placeholder，填了值之后就完全看不出这格是什么
                FieldLabel("去哪（山峰 / 线路）")
                GlassTextField(name, { name = it }, "如：太白山拔仙台")

                FieldLabel("计划日期")
                PickerField(
                    value = displayPlanDate(date),
                    placeholder = "点击选择日期",
                    onClick = { showDatePicker = true }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("目标海拔 (m)")
                        GlassTextField(
                            elevation, { elevation = it.filter(Char::isDigit) },
                            "如 3767", numeric = true, center = true
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("地点（可空）")
                        GlassTextField(location, { location = it }, "如：眉县", center = true)
                    }
                }

                Column {
                    FieldLabel("难度  ${diffLabel(difficulty)}")
                    GlassSegmented(
                        options = listOf("1", "2", "3", "4", "5"),
                        selectedIndex = difficulty - 1,
                        onSelect = { difficulty = it + 1 },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                FieldLabel("备注（装备、路线、想做的事…）")
                GlassTextField(
                    note, { note = it }, "随手写两句",
                    singleLine = false, minLines = 3, maxLines = 5
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) {
                        GlassButton("取消", onClick = {
                            if (initial == null) onDismiss() else editing = false
                        })
                    }
                    Box(Modifier.weight(1f)) {
                        GlassButton(
                            "保存",
                            onClick = {
                                onSave(
                                    base.copy(
                                        // ★日期归一化：手输「11月5日」「2026-13-01」原来原样入库，
                                        //   daysUntil 解析失败一律返回 0 ⇒ 每条坏日期的计划都显示「就是今天」
                                        name = name.trim(), plannedDate = normalizePlanDate(date, base.plannedDate),
                                        location = location.trim(), note = note,
                                        difficulty = difficulty,
                                        elevation = elevation.toIntOrNull() ?: 0
                                    )
                                )
                            },
                            primary = true,
                            enabled = name.isNotBlank()
                        )
                    }
                }
            } else {
                Text(
                    base.name, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val d = daysUntil(base.plannedDate)
                Text(
                    when {
                        d > 0 -> "${base.plannedDate} · 还有 $d 天"
                        d == 0 -> "${base.plannedDate} · 就是今天"
                        else -> "${base.plannedDate} · 已过 ${-d} 天"
                    },
                    fontSize = 12.sp,
                    color = if (d == 0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (base.location.isNotBlank()) {
                    Text(
                        base.location, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "难度  ${diffLabel(base.difficulty)}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "目标海拔  " + (if (base.elevation > 0) "${base.elevation} m" else "—"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (base.note.isNotBlank()) {
                    // ★2026-11-03：这块原本是「0.06 灰底 + 一段文字」，没有标签，
                    //   看起来像一个填了一半的输入框。现在补「备注」小标签，底色换成玻璃。
                    Text(
                        "备注", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlassChip(corner = Radii.SUB, flat = true)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            base.note, fontSize = 13.sp, lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // ★主操作是「完成了 ✓」—— 计划存在的意义就是走完它转成记录。
                //   Web 版这里是绿色（走了 = 好事），不能跟删除混在一行当普通按钮。
                if (onComplete != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // ★绿玻璃绿字（对齐 Web 版「完成了 ✓」：rgba(22,163,74) 边 + #15803d 字）
                            .liquidGlassChip(corner = Radii.BUTTON, positive = true)
                            .safeClickable { confirmComplete = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val posTint = if (LocalIsDark.current) Color(0xFF6EE7A8) else Color(0xFF15803D)
                        Icon(
                            Icons.Filled.CheckCircle, null,
                            tint = posTint, modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "走完了，转成记录", fontSize = 14.sp,
                            color = posTint, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) { GlassButton("编辑计划", onClick = { editing = true }) }
                    if (onDelete != null) {
                        Box(Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    // ★破坏性操作统一危险语义（和记录详情、抹掉数据一致）
                                    .liquidGlassChip(corner = Radii.BUTTON, danger = true)
                                    .safeClickable { confirmDelete = true }
                                    .padding(vertical = 11.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "删除", fontSize = 15.sp,
                                    color = if (LocalIsDark.current) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmComplete) {
        GlassConfirm(
            hazeState = hazeState,
            title = "确认走完了？",
            message = "「${base.name}」会从计划里移走，并生成一条徒步记录（名称 / 难度 / 海拔已填好）。里程、照片可以之后补。",
            confirmText = "确认完成",
            positive = true,
            onConfirm = { confirmComplete = false; onComplete?.invoke() },
            onCancel = { confirmComplete = false }
        )
    }

    if (confirmDelete) {
        GlassConfirm(
            hazeState = hazeState,
            title = "删除这个计划？",
            message = "「${base.name}」将被永久删除。",
            confirmText = "删除",
            danger = true,
            onConfirm = { confirmDelete = false; onDelete?.invoke() },
            onCancel = { confirmDelete = false }
        )
    }

    // ── 日期选择器（与编辑弹窗同款日历，只选日期）──
    if (showDatePicker) {
        GlassDateTimePicker(
            initial = date,
            withTime = false,
            hazeState = hazeState,
            onDismiss = { showDatePicker = false },
            onConfirm = { iso ->
                date = iso.take(10)
                showDatePicker = false
            }
        )
    }
}

/**
 * 计划日期归一化 —— 统一成 `yyyy-MM-dd`，解析失败就保留原值。
 * ★isLenient=false 才会拒绝 13 月 / 2 月 30 日这类"写得进去但根本不存在"的日期。
 */
private fun normalizePlanDate(raw: String, fallback: String): String {
    val s = raw.trim().replace('/', '-')
    if (s.isBlank()) return fallback
    for (p in listOf("yyyy-MM-dd", "yyyy-M-d")) {
        try {
            val f = SimpleDateFormat(p, Locale.getDefault())
            f.isLenient = false
            val d = f.parse(s) ?: continue
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
        } catch (e: Exception) {
            // 换下一个格式
        }
    }
    return fallback
}

/** "2026-11-15" → 「2026年11月15日」；解析不了原样显示 */
private fun displayPlanDate(raw: String): String {
    val s0 = raw.trim().replace(' ', 'T').replace('/', '-')
    for (p in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")) {
        try {
            val f = SimpleDateFormat(p, Locale.getDefault())
            f.isLenient = false
            val d = f.parse(s0) ?: continue
            val c = java.util.Calendar.getInstance().apply { time = d }
            return "${c.get(java.util.Calendar.YEAR)}年${c.get(java.util.Calendar.MONTH) + 1}月" +
                "${c.get(java.util.Calendar.DAY_OF_MONTH)}日"
        } catch (e: Exception) {
        }
    }
    return raw
}

/**
 * ★「完成 / 延期」二选一（对齐 Web 版 showCompleteOrDelayModal）
 *
 * 什么时候出现：日历明细里点**已到期**（过期或当天）计划的按钮。
 * 为什么要有：计划到期不等于走完了。以前只有一个「完成」，
 * 日子到了但还没走的人只能去改日期；现在先问一句，两条路都摆在面前。
 *
 * 布局照 Web：标题 + 说明 + [完成][延期] 等宽一行 + [取消] 独占一行。
 */
@Composable
fun PlanDueDialog(
    planName: String,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onDelay: () -> Unit
) {
    GlassOverlay(
        bottomClose = false,
        hazeState = hazeState, onDismiss = onDismiss,
        maxWidthFraction = 0.86f, corner = 24
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EventRepeat, null, tint = Ink, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "完成 / 延期",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "「${planName.ifBlank { "未命名计划" }}」的日子到了。已经走完就点「完成」，会转入徒步记录；还没走就点「延期」，改个新日期。",
                fontSize = 13.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    // 完成 = 正向操作，绿染玻璃（Web 版这里是绿色系）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassChip(corner = Radii.BUTTON, positive = true)
                            .safeClickable(onClick = onComplete)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("完成", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(Modifier.weight(1f)) {
                    GlassButton("延期", onClick = onDelay)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassChip(corner = Radii.BUTTON, flat = true)
                    .safeClickable(onClick = onDismiss)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("取消", fontSize = 14.sp, color = Ink)
            }
        }
    }
}
