package com.xixi.hiking.nativeapp.ui.plans

import com.xixi.hiking.nativeapp.ui.SafeTap
import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.PlannedTripEntity
import com.xixi.hiking.nativeapp.data.Prefs
import com.xixi.hiking.nativeapp.ui.GlassConfirm
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.glassPanel
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.xixi.hiking.nativeapp.ui.theme.Ink
import androidx.compose.material.icons.filled.EventNote

// ★2026-11-04 每页条数改成用户可调（设置 → 杂项 → 每页条数），这里是默认值
private const val DEFAULT_PLAN_PAGE_SIZE = 10

/**
 * ★计划页 —— 1:1 对齐现有 App（www/index.html#tab-plans）
 *
 *   工具行：⛰ 计划 + 「共 N 条」+ [✓ 批量] + [＋] + [列表 ⇄ 日历]
 *   视图说明随模式切换
 *   日历视图：月历，有计划的日子打绿点，点日期看当天安排
 *   列表视图：三列表格（名称 / 计划时间 / 操作）+ 分页 + 批量条
 *   引导卡：零计划 + 没关过才显示
 */
@Composable
fun PlansScreen(
    plans: List<PlannedTripEntity>,
    hazeState: HazeState,
    onOpen: (PlannedTripEntity) -> Unit,
    onNew: () -> Unit,
    onDeleteMany: (List<PlannedTripEntity>) -> Unit = {},
    /** 「完成」—— 计划转记录（日历明细里的按钮会调） */
    onComplete: (PlannedTripEntity) -> Unit = {},
    /** 单条删除（日历明细里的删除按钮） */
    onDeleteOne: (PlannedTripEntity) -> Unit = {},
    /** 顶栏是浮层，页面要给内容让出这么高 */
    topInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val ctx = LocalContext.current
    // ★2026-11-03：年份分组头要用深浅两套色（Web .year-group-head 有 dark 覆盖）
    val isDark = LocalIsDark.current
    // ★★2026-11-03 用户：「默认都是列表状态，也保留记忆功能，上次关闭前是哪一页，下次点开就是哪一页」
    //   读不到（首次安装）就是 true = 列表视图。之后每次切视图都写回 Prefs。
    var calendarView by remember {
        mutableStateOf(!Prefs.getBool(ctx, Prefs.KEY_PLANS_VIEW, true))
    }
    var showGuide by remember {
        mutableStateOf(plans.isEmpty() && !Prefs.getBool(ctx, Prefs.KEY_GUIDE_PLANS, false))
    }
    // ★2026-11-05 气味扫描：原写法在组合期直接置 false（组合期写 state）。
    //   改成 LaunchedEffect：数据到位后的下一帧收起，视觉上完全一样，但没有重组隐患。
    androidx.compose.runtime.LaunchedEffect(plans.isNotEmpty()) {
        if (plans.isNotEmpty() && showGuide) showGuide = false
    }

    val batchMode = remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBatch by remember { mutableStateOf(false) }
    // ★★2026-11-03 单条删除的二次确认（用户：「删除要有二次确认，你忘了吗」）
    //   行内垃圾桶原来是点了就删；批量删除有确认、单条却没有，是重构时漏掉的闸。
    var pendingDeletePlan by remember { mutableStateOf<PlannedTripEntity?>(null) }
    var page by remember { mutableStateOf(0) }
    // 每页条数（与记录页共用同一个偏好）
    val planPageSize = remember {
        com.xixi.hiking.nativeapp.data.Prefs
            .getInt(ctx, com.xixi.hiking.nativeapp.data.Prefs.KEY_PAGE_SIZE, DEFAULT_PLAN_PAGE_SIZE)
            .coerceIn(5, 50)
    }
    // ★2026-11-03 对齐 Web：名称 / 计划时间 两列各自可真排序（上一版两列翻的是同一个开关）
    // ★2026-11-03 全局搜索（Web 计划页也有这条 `.global-search-bar`）
    var query by remember { mutableStateOf("") }
    var sortField by remember { mutableStateOf("date") }
    var sortDesc by remember { mutableStateOf(false) }   // 计划按日期升序看更有意义
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    // ★2026-11-03 搜索框显隐开关（Web：默认隐藏，一滚动就滑出）
    val listScroll = rememberScrollState()
    // ★2026-11-03 再点当前 tab → 平滑回顶 + 分页回第 1 页
    androidx.compose.runtime.LaunchedEffect(com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue) {
        if (com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue > 0) {
            listScroll.animateScrollTo(0)
            page = 0
        }
    }

    // Web 计划页搜索：按名称匹配（记录页还匹配备注/同行人，计划没有那些字段）
    val visiblePlans = plans.filter {
        query.isBlank() || it.name.contains(query, true) || it.location.contains(query, true)
    }
    val sorted = visiblePlans.let { l ->
        when (sortField) {
            "name" -> l.sortedWith(
                if (sortDesc) compareByDescending<PlannedTripEntity> { it.name.lowercase() }
                else compareBy { it.name.lowercase() }
            )
            else -> l.sortedWith(
                if (sortDesc) compareByDescending<PlannedTripEntity> { it.plannedDate }
                else compareBy { it.plannedDate }
            )
        }
    }
    val totalPages = maxOf(1, (sorted.size + planPageSize - 1) / planPageSize)
    val safePage = page.coerceIn(0, totalPages - 1)
    val pageItems = sorted.drop(safePage * planPageSize).take(planPageSize)
    val grouped = pageItems.groupBy { it.plannedDate.take(4) }.toSortedMap(compareByDescending { it })

    // ★★2026-11-02 重做结构（和记录页同一套，对齐 Web 版「整页一起滚」）：
    //   旧结构是「工具行钉死 + 中间一块独立滚动区 + 外面垫 96dp」，
    //   那 96dp 就是底部那片死区 —— 内容滚到底硬断在那儿，看着像压了一块遮罩。
    //   现在整页一个 verticalScroll，尾巴留 120dp 给浮起的底栏。
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(listScroll)
    ) {
        // ★顶栏让位放在滚动内容里：内容才会滚到顶栏玻璃底下被羽化
        Spacer(Modifier.height(topInset + 2.dp))

        // ══════════════════════════════════════════════════════════
        //  ★★2026-11-03 结构对齐 Web（index.html#tab-plans）
        //
        //  Web 是「整页一张 glass-panel，p-6，里面 panel-hdr + caption + 表格」。
        //  原生之前是「工具行 / 说明 / 表格」三层平铺，没有任何容器 —— 所以怎么调
        //  都跟 Web 差一口气：缺的不是间距，是那张把内容兜起来的底板。
        //
        //  数值走 Web 手机端那条 media query（app.css:2088）：
        //    · 页面边距 .container padding:6px  → 6dp
        //    · 面板内边距 .p-6 padding:0.75rem → 12dp
        //    · 面板圆角 .glass-panel radius:20px → 20
        //  桌面端的 24px 不适用 —— 手机上 Web 自己也会压到 12px。
        // ══════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .glassPanel(corner = 20)
                .padding(12.dp)
        ) {
        // ══ 标题行（Web .panel-hdr：h2 text-2xl + 右侧按钮组 gap-2）══
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.EventNote, null,
                tint = AccentIndigo,
                // Web .material-icons 在手机端被压到 18px
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "计划",
                // Web text-2xl 手机端 1.1rem = 17.6px（桌面 24px 在手机上不生效）
                fontSize = 17.5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(10.dp))
            // Web：`总计: N条计划`（.total-badge：12px / 0.62 不透明度）
            Text(
                if (query.isBlank()) "总计: ${plans.size}条计划"
                else "匹配 ${visiblePlans.size} / 共 ${plans.size} 条",
                fontSize = 12.sp,
                color = Ink.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))

            // ★日历视图不显示批量/添加 —— Web 版 applyPlansView 里就是 display:none。
            //   日历里根本没有「行」可以勾选，摆着两个失效按钮只会让人点了没反应。
            if (!calendarView) {
                RoundIconButton(
                    icon = if (batchMode.value) Icons.Outlined.Close else Icons.Outlined.DoneAll,
                    active = batchMode.value,
                    contentDesc = if (batchMode.value) "退出批量" else "批量管理"
                ) {
                    batchMode.value = !batchMode.value
                    if (!batchMode.value) selected = emptySet()
                }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(Icons.Outlined.Add, contentDesc = "添加计划", onClick = onNew)
                Spacer(Modifier.width(8.dp))
            }

            // 列表 ⇄ 日历
            Row(
                modifier = Modifier
                    // 玻璃胶囊（不要半透明白块 + 描边那种"假玻璃"）
                    .liquidGlassChip(corner = Radii.INPUT)
                    .safeClickable {
                        calendarView = !calendarView
                        // 记忆：下次进这个 tab 还是这个视图
                        Prefs.setBool(ctx, Prefs.KEY_PLANS_VIEW, !calendarView)
                        batchMode.value = false
                        selected = emptySet()
                        page = 0
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (calendarView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.CalendarMonth,
                    null, tint = Ink, modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (calendarView) "列表" else "日历",
                    fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Web .view-caption：12px / 居中 / #52606f（深 #a3b1c6），margin -8px 0 14px
        Text(
            if (calendarView) "哪天有行程一眼看清"
            else "全部计划按时间排列",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = if (LocalIsDark.current) Color(0xFFA3B1C6) else Color(0xFF52606F),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // ★2026-11-03 10 → 4dp：说明行到表头原本隔着 22dp（4+8+10），太空
        Spacer(Modifier.height(4.dp))

            if (calendarView) {
                Column(Modifier.fillMaxWidth()) {
                    if (showGuide) {
                        PlansGuideCard(onClose = {
                            showGuide = false
                            Prefs.setBool(ctx, Prefs.KEY_GUIDE_PLANS, true)
                        }, onAdd = onNew)
                        Spacer(Modifier.height(10.dp))
                    }
                    // ★日历自带明细区（整月 / 单日）—— 不再另铺「接下来」，
                    //   同一条计划在一屏里出现两次只会更乱。
                    PlanCalendarView(
                        plans = plans,
                        onOpen = onOpen,
                        onComplete = onComplete,
                        // ★★2026-11-05 修：这里原来直接传 onDeleteOne ⇒
                        //   日历视图里的垃圾桶**点了就删**，而列表视图是走确认的。
                        //   同一个删除动作，换个视图就没有闸 —— 用户问「都加了吗」时查出来的。
                        onDelete = { pendingDeletePlan = it }
                    )

                    if (plans.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "还没有计划，点右上角 ＋ 安排下一趟",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    if (showGuide) {
                        PlansGuideCard(onClose = {
                            showGuide = false
                            Prefs.setBool(ctx, Prefs.KEY_GUIDE_PLANS, true)
                        }, onAdd = onNew)
                        Spacer(Modifier.height(10.dp))
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // ★2026-11-03：glass-panel 已经给了 12dp 内边距，这里不再重复缩进
                            .padding(vertical = 4.dp)
                    ) {
                        // 表头
                        // ★★2026-11-03 对齐修正（用户：「每个标题和下边的内容都对不上」）：
                        //   原来是 标题图标 x=0、表头/行 x=8dp、日期居中 —— 三条基准线互相错开。
                        //   现在表格区不再自己加水平内边距（面板的 12dp 就是唯一基准），
                        //   于是 标题图标 / 表头「名称」/ 行名称 三者左缘完全重合。
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).safeClickable {
                                    sortField = "name"; sortDesc = !sortDesc; page = 0
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "名称", fontSize = 12.sp,
                                    // Web th 是 font-semibold
                                    fontWeight = FontWeight.SemiBold,
                                    // ★2026-11-03 对齐 Web：.sort-header 文字不变色，
                                    //   只有 .sort-icon.active 变 #4f46e5 —— 之前整列文字都染靛蓝
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                SortArrow(
                                    active = sortField == "name", desc = sortDesc,
                                    on = AccentIndigo
                                )
                            }
                            // ★表头在这 106dp 里也居中 —— 和下面的日期单元格同轴，
                            //   否则「计划时间」四个字贴在列左缘、日期居中，看着就是错位的。
                            Box(
                                modifier = Modifier.width(106.dp).safeClickable {
                                    sortField = "date"; sortDesc = !sortDesc; page = 0
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                // ★★2026-11-03 同记录页：文字必须**精确落在列中轴**，
                                //   箭头贴列右缘。原来「文字+箭头」整组居中，
                                //   文字就被箭头挤偏了半个箭头宽（实测差 8dp），
                                //   和下面的日期永远差一条轴线 —— 就是"歪"的来源。
                                // 光学居中：整组居中后文字会偏左半个箭头组宽 ⇒ 往右挪 8.5dp 让文字落回列轴
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.offset(x = 8.5.dp)
                                ) {
                                    Text(
                                        "计划时间", fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    SortArrow(
                                        active = sortField == "date", desc = sortDesc,
                                        on = AccentIndigo
                                    )
                                }
                            }
                            // 操作列宽 = 行内两枚 30dp 按钮 + 4dp 间距
                            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "操作", fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (sorted.isEmpty()) {
                            // ★在 verticalScroll 里不能用 fillMaxSize（高度约束无限）
                            // Web #plannedEmptyState：大图标 + 主文案 + 副文案
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.Explore, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "暂无计划徒步行，点击上方按钮添加",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "规划你的下一次登山目标",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        } else {
                            // ★LazyColumn → Column：外层已经是整页滚动，嵌套滚动会让约束变成无限高。
                            //   每页只有 10 条，不需要惰性加载。
                            Column {
                                grouped.forEach { (year, list) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // ★★2026-11-03 同记录页：去掉前置 16dp 图标。
                                        //   「图标 + 2026年」会让年份文字比表头「名称」和每行计划名
                                        //   右缩 23dp，一条列表里出现两条左基准线 —— 看着就是"乱"。
                                        Text(
                                            "${year}年",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDark) Color(0xFF818CF8) else Color(0xFF667EEA)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "${list.size} 次",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color.White.copy(alpha = 0.50f)
                                            else Color(0xFF64748B).copy(alpha = 0.85f)
                                        )
                                    }
                                    Box(
                                        Modifier.fillMaxWidth().height(1.dp)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
                                    )
                                    list.forEach { p ->
                                        PlanRow(
                                            plan = p,
                                            today = today,
                                            batchMode = batchMode.value,
                                            checked = selected.contains(p.id),
                                            onComplete = { onComplete(p) },
                                            onDelete = { pendingDeletePlan = p },
                                            onOpen = {
                                                if (batchMode.value) {
                                                    selected = if (selected.contains(p.id)) selected - p.id else selected + p.id
                                                } else onOpen(p)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (batchMode.value) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已选 ${selected.size} 条",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            PlanPill("删除选中", danger = true) {
                                if (selected.isNotEmpty()) confirmBatch = true
                            }
                            Spacer(Modifier.width(8.dp))
                            PlanPill("取消") {
                                batchMode.value = false
                                selected = emptySet()
                            }
                        }
                    } else if (sorted.size > planPageSize) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ★Web：到边界直接**隐藏**按钮（不是变灰）——
                            //   置灰的下箭头看着像"能点但坏了"，隐藏才干净
                            if (safePage > 0) {
                                PagerBtn(prev = true, enabled = true) { page = safePage - 1 }
                            }
                            Text(
                                "${safePage * planPageSize + 1}-${minOf((safePage + 1) * planPageSize, sorted.size)} / 共${sorted.size}条",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            if (safePage < totalPages - 1) {
                                PagerBtn(prev = false, enabled = true) { page = safePage + 1 }
                            }
                        }
                    }
                }
            }
        }   // ← 闭合 glass-panel（对齐 Web 那张把整页兜起来的底板）

        // ★尾巴留在滚动内容里：最后一条 / 分页条能推到底栏之上。
        //   滚动容器本身一路铺到屏幕底 —— 底部不会再出现那片"遮罩"。
        Spacer(Modifier.height(120.dp))
    }

    // ★★2026-11-03 全局搜索条（Web 计划页同款；日历视图不提供搜索）
    if (!calendarView) {
        // ★2026-11-03 默认没有、滚动才滑出（同记录页）
        androidx.compose.animation.AnimatedVisibility(
            visible = query.isNotBlank() || listScroll.value > 8,
            enter = androidx.compose.animation.fadeIn(
                androidx.compose.animation.core.tween(200)
            ) + androidx.compose.animation.slideInVertically(
                androidx.compose.animation.core.tween(260)
            ) { it / 2 },
            exit = androidx.compose.animation.fadeOut(
                androidx.compose.animation.core.tween(150)
            ) + androidx.compose.animation.slideOutVertically(
                androidx.compose.animation.core.tween(220)
            ) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
        ) {
            com.xixi.hiking.nativeapp.ui.GlobalSearchBar(
                value = query,
                onValueChange = { query = it; page = 0 },
                placeholder = "搜索计划…"
            )
        }
    }
    }

    // 单条删除确认（对齐 Web 版 showConfirmDeleteModal）
    pendingDeletePlan?.let { p ->
        GlassConfirm(
            hazeState = hazeState,
            title = "删除计划",
            message = "删了就找不回来了。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                val target = p
                pendingDeletePlan = null
                onDeleteOne(target)
            },
            onCancel = { pendingDeletePlan = null }
        )
    }

    if (confirmBatch) {
        GlassConfirm(
            hazeState = hazeState,
            title = "批量删除",
            message = "确定删除选中的 ${selected.size} 个计划？此操作无法撤销。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                val targets = plans.filter { selected.contains(it.id) }
                confirmBatch = false
                batchMode.value = false
                selected = emptySet()
                page = 0
                onDeleteMany(targets)
            },
            onCancel = { confirmBatch = false }
        )
    }
}


@Composable
private fun PlansGuideCard(onClose: () -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassPanel(corner = Radii.CARD)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EventNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "想去的山，先写进计划",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.Close, "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).safeClickable(onClick = onClose)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            buildAnnotatedString {
                append("把下一个山头定好日子")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("日历") }
                append("看排期，一眼看清哪天有行程")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("列表") }
                append(" 看全部计划。")
            },
            fontSize = 12.sp, lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .border(
                    1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                    RoundedCornerShape(10.dp)
                )
                .safeClickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text("添加一条计划", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * ★计划列表一行 —— 1:1 对齐 Web `renderPlannedTripsTable` 的行结构：
 *   名称（+ 状态徽章）｜计划时间｜操作（两枚图标按钮：完成 / 完成-延期、删除）
 *
 * 上一版的问题：
 *   ① 只有「名称 + 日期」，**没有操作按钮** —— 要完成/删除必须点进详情，和 Web 差一截；
 *   ② 用了三色圆点表示状态，Web 是文字徽章（已过期 N 天 / 今天 / 明天）；
 *   ③ 批量模式下勾选框挤在最左，Web 是勾选框**占据操作列**。
 */
@Composable
private fun PlanRow(
    plan: PlannedTripEntity,
    today: String,
    batchMode: Boolean,
    checked: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    val isDark = LocalIsDark.current
    val diff = daysBetween(today, plan.plannedDate)   // 计划日 - 今天
    val lineColor = if (isDark) Color.White.copy(alpha = 0.09f) else Color(0xFFE4E9F2)
    Row(
        modifier = Modifier.fillMaxWidth()
            // ★2026-11-03 补行分隔线：Web 每行是 `border-b border-white/10`。
            //   没有这条线，列表缩进收窄之后行与行会糊成一片（记录页一直有，计划页漏了）。
            .drawBehind {
                val y = size.height - 0.5f
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
            .safeClickable(onClick = onOpen)
            // ★2026-11-03：水平内边距归零 —— 面板的 12dp 是唯一基准线，
            //   名称左缘要和上面的标题图标、表头「名称」在同一条竖线上。
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // 第一行：**只有名称**（★2026-11-03 用户：「已过期的小框放到难度·海拔这一行，
            //   放到名称那里的话山名都显示不全」—— 徽章挪走后名称独占整行，不用折两行）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plan.name.ifBlank { "未命名" },
                    // ★2026-11-03 16 → 15sp（用户：「名称、时间字号是不是太大了，再小点」）
                    //   Web 桌面是 text-base(16px)，手机端 media query 会压到 1rem(16px) 但
                    //   宋体字面比黑体宽，同字号下观感偏大 —— 收 1sp 正好。
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(3.dp))
            // ★★2026-11-03 重排第二行：
            //   上一版把「地点」塞在第一行 name 之后，第一条计划「朱雀国家森林公园」
            //   名称一长，地点就被顶到日期列上，两个文本直接叠在一起。
            //   现在地点挪到第二行，和难度/海拔排成一条元信息：
            //       Lv5 · 3767m · 西安鄠邑
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("Lv${plan.difficulty}")
                        if (plan.elevation > 0) append(" · ${plan.elevation}m")
                        if (plan.location.isNotBlank()) append(" · ${plan.location}")
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // ★徽章现在在这条元信息行的末尾 —— 名称独占第一行，能完整显示
                PlanBadge(diff)
            }
        }
        // 列宽与表头一致（106 / 64），否则表头会落在内容左边
        Box(Modifier.width(106.dp), contentAlignment = Alignment.Center) {
            Text(
                plan.plannedDate,
                // Web .rd-time-cell：14px / 居中；颜色 #52606f（深 #cbd5e1）
                // 14 → 13sp（同上，宋体同字号观感偏大）
                fontSize = 13.sp,
                // ★2026-11-03 同记录页：日期串接近占满列宽，Box 的 Center **失效**，
                //   实际是贴左渲染（墨迹中心比列轴左 18dp）。让 Text 自己撑满 + 居中。
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = if (diff == 0) Color(0xFFF59E0B)
                else if (isDark) Color(0xFFCBD5E1) else Color(0xFF52606F),
                // Web `#plannedTripsTable td { font-weight:600 !important }` —— 日期和名称同字重
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        // ★★★2026-11-03 修「行内图标乱码」：
        //   上一版把两个 RowIconAction 一起塞进了同一个 Box ——
        //   而 Box 的多个子节点是**层叠**（都居中叠在 z 轴），不是横向排列。
        //   于是 ✓ 和 🗑 画在同一格像素上，看上去就是一团糊掉的乱码。
        //   这里必须用 Row：两枚按钮横向并排，间距 4dp，整列宽 64dp。
        Row(
            modifier = Modifier.width(64.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (batchMode) {
                // Web：批量模式下操作列换成勾选框
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (checked) AccentIndigo else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
                        .border(
                            1.dp,
                            if (checked) AccentIndigo else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            } else {
                // 完成（到期时是「完成/延期」，图标换成 event_repeat）
                RowIconAction(
                    icon = if ((diff ?: 1) <= 0) Icons.Filled.EventRepeat else Icons.Filled.Check,
                    desc = if ((diff ?: 1) <= 0) "完成 / 延期" else "标记为已完成",
                    onClick = onComplete
                )
                Spacer(Modifier.width(4.dp))
                RowIconAction(
                    icon = Icons.Filled.Delete,
                    desc = "删除",
                    dangerLook = !isDark,
                    onClick = onDelete
                )
            }
        }
    }
}

/**
 * 排序箭头。
 * ★原来用的是「⇅ / ↓ / ↑」三个字体字符 —— 部分机型字体缺字，会画成方框，
 *   用户看到的就是"图标乱码"。这里统一换成 Material 矢量图标。
 */
@Composable
private fun SortArrow(active: Boolean, desc: Boolean, on: Color) {
    Icon(
        imageVector = when {
            !active -> Icons.Filled.UnfoldMore
            desc -> Icons.Filled.ArrowDownward
            else -> Icons.Filled.ArrowUpward
        },
        contentDescription = null,
        tint = if (active) on else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.size(13.dp)
    )
}

/** 状态徽章（Web `planRelBadgeHtml`）：过期红「已过期 N 天」/ 今天靛「今天」/ 明天天蓝「明天」 */
@Composable
private fun PlanBadge(diff: Int?) {
    val isDark = LocalIsDark.current
    if (diff == null) return
    val (text, color) = when {
        // ★2026-11-03 用户：「已过期 XX 天改掉，已过期三个字就行了」
        diff < 0 -> "已过期" to Color(0xFFDC2626)
        diff == 0 -> "今天" to AccentIndigo
        diff == 1 -> "明天" to Color(0xFF0EA5E9)
        else -> return
    }
    Spacer(Modifier.width(6.dp))
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = if (isDark) 0.22f else 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** 行内图标按钮（Web 的 6×6 padding + 18px 图标 + 圆角 10） */
@Composable
private fun RowIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    dangerLook: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (dangerLook) Modifier
                    .background(Color(0xFF64748B).copy(alpha = 0.14f))
                    .border(1.dp, Color(0xFF64748B).copy(alpha = 0.60f), RoundedCornerShape(10.dp))
                else Modifier.liquidGlassChip(corner = 10, flat = true)
            )
            .safeClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, desc,
            tint = if (dangerLook) Color(0xFF334155) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 两个 yyyy-MM-dd 相差几天（b - a） */
private fun daysBetween(a: String, b: String): Int? {
    return try {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val da = f.parse(a) ?: return null
        val db = f.parse(b) ?: return null
        ((db.time - da.time) / 86_400_000L).toInt()
    } catch (t: Throwable) {
        null
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            // ★★2026-11-03 用户：「多选和添加按钮弄成方形，现在都不符合设计语言」
            //   Web 是 `.glass-btn corner-glow px-3 py-2.5` = 圆角 12 的方块，不是圆。
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
            // ★2026-11-04 补描边（和全站按钮同一条 0.20 边线，否则这颗"方框"没有边界）
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                RoundedCornerShape(12.dp)
            )
            .safeClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDesc,
            tint = if (active) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PlanPill(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (danger) Color(0xFFDC2626).copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
            )
            .border(
                1.dp,
                if (danger) Color(0xFFDC2626).copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                RoundedCornerShape(10.dp)
            )
            .safeClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text, fontSize = 13.sp,
            color = if (danger) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PagerBtn(prev: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // ★★2026-11-03 用户：「左右切换框里的符号没有居中，大小也太小」
    //   真因是原来直接画「‹ / ›」两个**字符**——宋体里这两个符号本身偏左上、
    //   字面又小，摆在 34dp 的盒子里怎么都对不齐。Web 用的是 material-icons
    //   的 chevron_left / chevron_right（18px），矢量图形永远在正中。
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.10f else 0.04f))
            // ★2026-11-04 补描边（同全站按钮）
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.40f else 0.16f),
                RoundedCornerShape(10.dp)
            )
            // ★2026-11-03 震动 + 异常兜底统一走 safeClickable（原来手写 clickable，
            //   一旦 onClick 里抛异常就直接杀进程；底栏其它地方早就是 safe 版了）
            .safeClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (prev) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            if (prev) "上一页" else "下一页",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 距计划日还有几天（负数为已过） */
fun daysUntil(date: String): Int {
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val target = fmt.parse(date) ?: return 0
        val t0 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val t1 = Calendar.getInstance().apply {
            time = target
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        ((t1.timeInMillis - t0.timeInMillis) / 86_400_000L).toInt()
    } catch (e: Exception) {
        0
    }
}
