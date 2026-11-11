package com.xixi.hiking.nativeapp.ui.records

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Checklist
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.PhotoEntity
import com.xixi.hiking.nativeapp.data.Prefs
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.ui.GlassConfirm
import com.xixi.hiking.nativeapp.ui.GlassTextField
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.glassPanel
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.DangerRed

/**
 * 每页条数的兜底值。
 *
 * ★2026-11-04 改成**用户可调**（对齐 Web 的 RemoteConfig pageSize：5~50）：
 *   真正的值从 Prefs 读，这里只作为首次安装的默认。
 */
private const val DEFAULT_PAGE_SIZE = 10

/**
 * ★记录页 —— 1:1 对齐现有 App（www/index.html#tab-records）
 *
 *   顶栏：🏔️ XX の徒步小记
 *   工具行：⛰ 记录 + 「总计：N 条记录」+ [✓ 批量] + [＋] + [山册 ⇄ 列表]
 *   视图说明：随模式切文案（点一下可收起）
 *   内容：
 *     · 列表视图 = 表格（年份分组 + 表头排序）→ 分页（每页 10 条）→ 批量工具栏
 *     · 山册视图 = 双列名册（见 MountainBookView）
 *   搜索框：列表视图才有（山册视图不搜索 —— 与 Web 版一致）
 *
 * ★山册在这里是**视图切换**，不是二级页 —— 之前做成二级页是错的，现有 App 里
 *   「山册」按钮就在工具行最右边，切过去底栏还在。
 */
@Composable
fun RecordListScreen(
    records: List<RecordEntity>,
    photos: List<PhotoEntity>,
    hazeState: HazeState,
    onOpen: (RecordEntity) -> Unit,
    onNew: () -> Unit,
    onDelete: (RecordEntity) -> Unit = {},
    onDeleteMany: (List<RecordEntity>) -> Unit = {},
    /** 顶栏是浮层，页面要给内容让出这么高 —— 内容才会从玻璃底下轧过去 */
    topInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val ctx = LocalContext.current
    // ★2026-11-03：年份分组头到深浅两套色（Web .year-group-head 有 dark 覆盖）
    val isDark = LocalIsDark.current
    var query by remember { mutableStateOf("") }
    var captionCollapsed by remember { mutableStateOf(false) }   // 视图说明是否收起
    // ★2026-11-03 对齐 Web 表头排序：名称 / 记录时间 两列各自可点
    var sortField by remember { mutableStateOf("createdAt") }
    var sortDesc by remember { mutableStateOf(true) }
    // ★★2026-11-03 默认列表 + 记忆（用户：「上次关闭前是哪一页，下次点开就是哪一页」）
    var showMountainBook by remember {
        mutableStateOf(!Prefs.getBool(ctx, Prefs.KEY_RECORDS_VIEW, true))
    }
    var page by remember { mutableStateOf(0) }
    // 每页条数（设置 → 杂项 → 每页条数；改完切页回来会重新读）
    val pageSize = remember {
        com.xixi.hiking.nativeapp.data.Prefs
            .getInt(ctx, com.xixi.hiking.nativeapp.data.Prefs.KEY_PAGE_SIZE, DEFAULT_PAGE_SIZE)
            .coerceIn(5, 50)
    }
    // ★2026-11-03 搜索框的显隐开关就是这一条滚动位移（Web：默认隐藏，滚动才滑出）
    val listScroll = rememberScrollState()
    // ★2026-11-03 再点当前 tab → 平滑回顶（Web switchTab 的行为），顺便把分页拉回第 1 页
    androidx.compose.runtime.LaunchedEffect(com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue) {
        if (com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue > 0) {
            listScroll.animateScrollTo(0)
            page = 0
        }
    }

    var recordsGuideVisible by remember {
        mutableStateOf(
            records.isEmpty() && !Prefs.getBool(ctx, Prefs.KEY_GUIDE_RECORDS, false)
        )
    }
    // ★2026-11-05 气味扫描：原写法在组合期直接置 false（组合期写 state）。
    //   改成 LaunchedEffect：数据到位后的下一帧收起，视觉上完全一样，但没有重组隐患。
    androidx.compose.runtime.LaunchedEffect(records.isNotEmpty()) {
        if (records.isNotEmpty() && recordsGuideVisible) recordsGuideVisible = false
    }

    // 批量
    var batchMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    // ★★2026-11-03 单条删除的二次确认（用户：「删除要有二次确认，你忘了吗」）
    //   行内垃圾桶原来是**点了就删**，连撤销的机会都没有 —— 批量删除一直有确认，
    //   单条反而没有，这是重构时漏掉的一道闸。
    var pendingDeleteRecord by remember { mutableStateOf<RecordEntity?>(null) }

    // ★山册视图不提供搜索（Web 版同规则）
    //
    // ★★2026-11-11 加 remember：原来这段 filter+sort 在**每次重组**都会重算一遍，
    //   而列表滚动时重组非常频繁 —— 几百条记录 × 三个字段的 contains + 排序，
    //   白白烧 CPU。现在只有这几个输入真的变了才重算。
    val shown = remember(records, query, showMountainBook, sortField, sortDesc) {
        records
            .filter {
                showMountainBook || query.isBlank() || it.name.contains(query, true) ||
                    it.notes.contains(query, true) || it.companions.contains(query, true)
            }
            .let { list ->
                when (sortField) {
                    "name" -> list.sortedWith(
                        if (sortDesc) compareByDescending<RecordEntity> { it.name.lowercase() }
                        else compareBy { it.name.lowercase() }
                    )
                    else -> list.sortedWith(
                        if (sortDesc) compareByDescending<RecordEntity> { it.createdAt }
                        else compareBy { it.createdAt }
                    )
                }
            }
    }

    val totalPages = maxOf(1, (shown.size + pageSize - 1) / pageSize)
    val safePage = page.coerceIn(0, totalPages - 1)
    val pageItems = shown.drop(safePage * pageSize).take(pageSize)
    val grouped = pageItems.groupBy { it.createdAt.take(4) }.toSortedMap(compareByDescending { it })

    // ★★2026-11-02 重做结构（对齐 Web 版「整页一张长纸」）：
    //   旧结构 = 工具行钉死 + 中间一块独立滚动区 + **滚动区外面**再垫 108dp。
    //   那 108dp 是滚动区之外的空地：列表滚到底就硬断在那儿，下面一大片什么都没有，
    //   看着就像底部压了一块遮罩（用户原话）。Web 版整页一起滚，根本没有这个断口。
    //   现在：顶部块 + 列表 + 分页 全在同一个滚动容器里，尾巴留 120dp 给浮起的底栏。
    //   ⚠️ 山册视图自带 LazyColumn，不能套进 verticalScroll（无限高约束会崩），
    //      所以顶部块做成 lambda，两种视图各自把它摆在滚动容器的第一段。
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
    val topBlock: @Composable () -> Unit = {
        // ★顶栏让位放在滚动内容里（不是给整页加 padding）：内容才会滚到玻璃底下
        Spacer(Modifier.height(topInset + 2.dp))

        // ══ 标题行（Web .panel-hdr：h2 text-2xl + 右侧按钮组 gap-2）══
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Terrain, null,
                tint = AccentIndigo,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "记录",
                // Web text-2xl 手机端 1.1rem = 17.6px
                fontSize = 17.5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(10.dp))
            // Web：`总计: N条记录`（.total-badge：12px / 0.62）
            Text(
                "总计: ${records.size}条记录",
                fontSize = 12.sp,
                color = Ink.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))

            // 批量 + 新建：山册视图下隐藏（对齐 Web 版 display:none）
            if (!showMountainBook) {
                RoundIconButton(
                    // ★原来是对勾 DoneAll：没人知道那是对「批量删除」的入口。换成清单图标。
                    icon = if (batchMode) Icons.Outlined.Close else Icons.Outlined.Checklist,
                    active = batchMode,
                    contentDesc = if (batchMode) "退出批量" else "批量管理"
                ) {
                    batchMode = !batchMode
                    if (!batchMode) selected = emptySet()
                }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(Icons.Outlined.Add, contentDesc = "添加徒步记录", onClick = onNew)
                Spacer(Modifier.width(8.dp))
            }

            // 山册 ⇄ 列表 切换（带文字，别人一眼知道切到哪）
            Row(
                modifier = Modifier
                    // 玻璃胶囊（不要半透明白块 + 描边那种"假玻璃"）
                    .liquidGlassChip(corner = Radii.INPUT)
                    .safeClickable {
                        showMountainBook = !showMountainBook
                        Prefs.setBool(ctx, Prefs.KEY_RECORDS_VIEW, !showMountainBook)
                        batchMode = false
                        selected = emptySet()
                        page = 0
                        query = ""
                        captionCollapsed = false   // 切视图后说明重新出现
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (showMountainBook) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.Landscape,
                    null, tint = Ink, modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (showMountainBook) "列表" else "山册",
                    fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ══ 视图说明 + 搜索开关 ══
        // ★搜索框原来是「浮在底栏上方的浮层」，把分页条整条盖住了（截图实锤）。
        //   现在收到说明行右侧做图标按钮，点开才在内容流里展开 —— 不遮挡任何东西。
        //
        // ★2026-11-03 改成 Box 叠放：搜索按钮 absolute 在右端，不再把说明文字挤偏。
        //   原来用 Row + weight(1f)，文字是在「屏宽 − 按钮宽」里居中 —— 实际偏左 17dp，
        //   跟计划页那行纯居中的说明对不上（用户说"每个标题和下边的内容都对不上"）。
        Box(Modifier.fillMaxWidth()) {
            // ★点一下收起（Web 版同款）：熟手不需要每次都看这行说明
            Text(
                if (captionCollapsed) "点这里展开说明"
                else if (showMountainBook) "按山峰汇总成册"
                else "全部记录按时间排列",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (captionCollapsed) 0.55f else 1f
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                // Web .view-caption 是居中灰字
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .safeClickable { captionCollapsed = !captionCollapsed }
            )
        }

        // ★2026-11-03 10 → 4dp：说明行到表头原本隔着 22dp，太空
        Spacer(Modifier.height(4.dp))

        // ══ 记录页引导（零记录 + 没关过才出现；✕ 才关，与 Web 版一致）══
        if (recordsGuideVisible) {
            RecordsGuideCard(
                onClose = {
                    recordsGuideVisible = false
                    Prefs.setBool(ctx, Prefs.KEY_GUIDE_RECORDS, true)
                },
                onAdd = onNew,
                onMountain = { showMountainBook = true }
            )
            Spacer(Modifier.height(10.dp))
        }
    }   // ← 闭合 topBlock（顶部那块：标题行 / 说明 / 搜索 / 引导卡）

    if (showMountainBook) {
        // 山册：LazyColumn 自己就是滚动容器，顶部块作为它的第一项
        MountainBookView(
            records = records,
            photos = photos,
            hazeState = hazeState,
            onOpenRecord = onOpen,
            modifier = Modifier.fillMaxSize(),
            // ★2026-11-03：山册是卡片网格，不套大面板；但顶部那块仍要跟列表视图同款底板
            header = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .glassPanel(corner = 20)
                        .padding(12.dp)
                ) { topBlock() }
                Spacer(Modifier.height(12.dp))
            }
        )
    } else {
        // ── 列表视图：整页一个滚动容器 ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listScroll)
        ) {
            // ★★2026-11-03 对齐 Web：整块内容装进一张 glass-panel
            //   Web 手机端数值：.container padding 6px / .p-6 padding 12px / radius 20
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .glassPanel(corner = 20)
                    .padding(12.dp)
            ) {
            topBlock()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()

                        // ★2026-11-03：glass-panel 已经给了 12dp 内边距，这里不再重复缩进
                        .padding(vertical = 4.dp)
                ) {
                    // ── 表头（对齐 Web thead）──
                    //   名称 ⇅ | 记录时间 ⇅ | 操作
                    // ★★2026-11-03 对齐修正（用户：「每个标题和下边的内容都对不上」）：
                    //   表格区不再自己加水平内边距（面板的 12dp 就是唯一基准），
                    //   于是 标题图标 / 表头「名称」/ 行名称 三者左缘完全重合；
                    //   「记录时间」也在 106dp 列里居中，和下面的日期同轴。
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SortHeader(
                            label = "名称",
                            active = sortField == "name",
                            desc = sortDesc,
                            modifier = Modifier.weight(1f)
                        ) { sortField = "name"; sortDesc = !sortDesc; page = 0 }
                        Box(Modifier.width(RecordColDate), contentAlignment = Alignment.Center) {
                            SortHeader(
                                label = "记录时间",
                                active = sortField == "createdAt",
                                desc = sortDesc,
                                // ★文字精确居中在列轴上（原来被箭头带偏 8dp）
                                modifier = Modifier.fillMaxWidth(),
                                centerLabel = true
                            ) { sortField = "createdAt"; sortDesc = !sortDesc; page = 0 }
                        }
                        Box(Modifier.width(RecordColAction), contentAlignment = Alignment.Center) {
                            Text(
                                "操作", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (shown.isEmpty()) {
                        // ★不能再用 fillMaxSize()：这里在 verticalScroll 里，高度约束是无限
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 70.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Landscape, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (query.isBlank()) "暂无徒步记录，点击上方按钮添加" else "没有匹配的记录",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (query.isBlank()) "记录你的每一次登山之旅" else "换个关键词试试",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        }
                    } else {
                        Column {
                            grouped.forEach { (year, list) ->
                                run {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // ★★2026-11-03 去掉这行前面的 16dp 山图标。
                                        //   Web 的年份行是「图标 + 2026年」，图标占掉 23dp，
                                        //   于是「2026年」比表头「名称」和下面每一行山名都右缩 23dp ——
                                        //   一条列表里出现两条左基准线，看起来就是"乱"。
                                        //   现在年份文字直接对齐「名称」/行名（同一条 18dp 左缘），
                                        //   靠 13sp/800 + 靛蓝保住分组头的存在感。
                                        Text(
                                            "${year}年",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDark) Color(0xFF818CF8) else Color(0xFF667EEA)
                                        )
                                        // Web year-group-count：全年次数（跨页准确）
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "${records.count { it.createdAt.startsWith(year) }} 次",
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
                                }
                                list.forEach { r ->
                                    RecordRow(
                                        r = r,
                                        batchMode = batchMode,
                                        checked = selected.contains(r.id),
                                        onOpen = {
                                            if (batchMode) {
                                                selected = if (selected.contains(r.id)) selected - r.id else selected + r.id
                                            } else onOpen(r)
                                        },
                                        onDelete = { pendingDeleteRecord = r }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 分页 / 批量工具栏 ──
                if (batchMode) {
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
                        SmallPill("删除选中", danger = true) {
                            if (selected.isEmpty()) return@SmallPill
                            confirmBatchDelete = true
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallPill("取消") {
                            batchMode = false
                            selected = emptySet()
                        }
                    }
                } else if (shown.size > pageSize) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ★Web：到边界隐藏按钮（不是置灰）
                        if (safePage > 0) PagerButton(prev = true, enabled = true) { page = safePage - 1 }
                        Text(
                            "${safePage * pageSize + 1}-${minOf((safePage + 1) * pageSize, shown.size)} / 共${shown.size}条",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (safePage < totalPages - 1) PagerButton(prev = false, enabled = true) { page = safePage + 1 }
                    }
                }
                // ★尾巴留在**滚动内容里**：分页条 / 最后一条能被推到浮起的底栏之上。
                //   滚动容器本身一路铺到屏幕底 —— 底部不会再出现那片"遮罩"。
                }   // ← 闭合 glass-panel
                Spacer(Modifier.height(120.dp))
            }   // ← 闭合「整页滚动容器」
        }       // ← 闭合「列表视图」分支

    // ★★2026-11-03 全局搜索条（1:1 搬 Web `.global-search-bar`）
    //   浮在底栏上方、水平居中、宽最多 260dp；山册视图不提供搜索（与 Web 一致）。
    //   底栏是浮层（高 60 + 外边距 10），这里让 96dp 正好悬在它上面。
    if (!showMountainBook) {
        // ★★2026-11-03 「搜索的精髓是默认没有、滑动才出来」——
        //   停在列表顶部时**没有搜索框**；往下一滚就从句尾滑出来（淡入 + 上移 18dp，
        //   与 Web 的 translateY(18px)+opacity 过渡同款）；滚回顶部再收回去。
        //   有搜索词时常显，否则词还没删完框就跑了。
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
                placeholder = "搜索记录…"
            )
        }
    }
    }           // ← 闭合根 Box

// 单条删除确认（对齐 Web 版 showConfirmDeleteModal）
pendingDeleteRecord?.let { rec ->
    GlassConfirm(
        hazeState = hazeState,
        title = "删除记录",
        message = "照片会一并删除，此操作无法撤销。",
        confirmText = "删除",
        danger = true,
        onConfirm = {
            val target = rec
            pendingDeleteRecord = null
            onDelete(target)
        },
        onCancel = { pendingDeleteRecord = null }
    )
}

// 批量删除确认
if (confirmBatchDelete) {
    GlassConfirm(
        hazeState = hazeState,
        title = "批量删除",
        message = "确定删除选中的 ${selected.size} 条记录？照片将一并删除，此操作无法撤销。",
        confirmText = "删除",
        danger = true,
        onConfirm = {
            val targets = records.filter { selected.contains(it.id) }
            confirmBatchDelete = false
            batchMode = false
            selected = emptySet()
            page = 0
            onDeleteMany(targets)
        },
        onCancel = { confirmBatchDelete = false }
    )
}
}

@Composable
private fun RecordRow(
    r: RecordEntity,
    batchMode: Boolean,
    checked: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = LocalIsDark.current
    val lineColor = if (isDark) Color.White.copy(alpha = 0.09f) else Color(0xFFE4E9F2)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // ★行分隔线：没有它，5 行记录连成一片分不清
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
            // ★2026-11-03：水平内边距归零 —— 面板的 12dp 是唯一基准线
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            r.name.ifBlank { "未命名" },
            // ★2026-11-03 16 → 15sp（宋体同字号观感偏大，用户要求再小点）
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        // ★列宽与表头严格对齐（同一组常量），否则「记录时间」标题会落在日期的左边
        Box(Modifier.width(RecordColDate), contentAlignment = Alignment.Center) {
            Text(
                r.createdAt.replace("T", " ").take(16),
                // 14 → 13sp
                fontSize = 13.sp,
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF52606F),
                // Web `#recordsTable td { font-weight:600 !important }`
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                // ★★2026-11-03 关键：`Box(contentAlignment=Center)` 在这里**是失效的** ——
                //   "2026-11-02 08:30" 16 个字符在 13sp 下几乎正好占满 106dp，
                //   Text 被量到列宽，没有可居中的余量，于是实际是**贴左**渲染。
                //   （实测：日期墨迹中心 499，列轴 536，差 18dp —— 就是"歪"的元凶。）
                //   让 Text 自己撑满列宽并 textAlign 居中，墨迹才真正落在列轴上。
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(Modifier.width(RecordColAction), contentAlignment = Alignment.Center) {
        // ★★2026-11-03 用户：「记录页的多选按钮点击后，变为和计划页一样的，在操作列选中」
        //   原来勾选框塞在**行首**（名称左边），整行文字被推右、和表头错位 —— 很违和。
        //   现在和计划页一致：操作列的删除按钮**原地换成**勾选框。
        if (batchMode) {
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
                if (checked) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        } else {
            // ★1:1 对齐 Web `recordDelStyle`：30dp 方块、圆角 10、18dp 垃圾桶；
            //   浅色用灰蓝底 + 0.6 描边（纯玻璃在白卡上看不见边），深色保持玻璃。
            val darkRow = LocalIsDark.current
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (darkRow) Modifier.liquidGlassChip(corner = 10, flat = true)
                        else Modifier
                            .background(Color(0xFF64748B).copy(alpha = 0.14f))
                            .border(1.dp, Color(0xFF64748B).copy(alpha = 0.60f), RoundedCornerShape(10.dp))
                    )
                    .safeClickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DeleteOutline, "删除",
                    tint = if (darkRow) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF334155),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    // ★★2026-11-03 用户：「记录页多选后的状态改为和计划页一样」「多选和添加按钮统一改为方框」
    //   上一版这里是 32dp 圆形 + 激活时整块靛蓝，和计划页那套（30dp / 圆角 12 / 中性底 / 只换图标）
    //   完全是两个东西。现在两页共用同一套规格。
    Box(
        modifier = Modifier
            .size(30.dp)
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
private fun RecordsGuideCard(onClose: () -> Unit, onAdd: () -> Unit, onMountain: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassPanel(corner = Radii.CARD)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Landscape, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "从这里开始你的第一座山",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildAnnotatedString {
                        append("点「＋」记下第一座山，名字、海拔、难度、照片都能记")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("山册") }
                        append("；卡右下那圈彩条就是它的最高海拔。")
                    },
                    fontSize = 12.sp, lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .border(
                                1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
                                RoundedCornerShape(12.dp)
                            )
                            .safeClickable(onClick = onAdd)
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text("记下第一笔", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentIndigo.copy(alpha = 0.08f))
                            .border(1.dp, AccentIndigo.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                            .safeClickable(onClick = onMountain)
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text("去山册看看", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Icon(
                Icons.Filled.Close, "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).safeClickable(onClick = onClose)
            )
        }
    }
}

@Composable
private fun SmallPill(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (danger) DangerRed.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f))
            .border(
                1.dp,
                if (danger) DangerRed.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                RoundedCornerShape(10.dp)
            )
            .safeClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = if (danger) DangerRed else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PagerButton(prev: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // ★★2026-11-03 「‹ ›」字符换矢量图标：宋体里这两个符号偏左上、字面小，怎么摆都不居中
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
            // ★2026-11-03 同计划页：统一 safeClickable（震动 + 兜异常）
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

/** 记录页表格列宽（表头与行共用，改一处两边同时变 —— 保证永远对齐） */
// ★2026-11-03 对齐 Web：.rd-time-th width:106px、操作列 width:56px。
//   原生操作列要放两枚 30dp 按钮，56 塞不下（Web 靠 nowrap + overflow:visible 溢出），
//   所以操作列保持 72，时间列收到 106。
private val RecordColDate = 106.dp
private val RecordColAction = 72.dp

/** 表头排序单元（对齐 Web `.sort-header` + `.sort-icon`：未排序显示 ⇅，排序后显示方向） */
/**
 * 可排序的表头。
 *
 * ★★2026-11-03 用户：「记录页列表对齐小标题呀，怎么看着这么乱」
 *
 *   实测（Robolectric 量真实边界）：「记录时间」这组的文字中心在 x=520.5，
 *   而下面日期文字的中心是 x=536 —— **差 8dp**。原因就是箭头跟在文字右边，
 *   「文字+箭头」作为一组居中，于是文字被顶偏了半个箭头宽。
 *   列头（小标题）和列内容因此永远差一条轴线，肉眼就是"歪的"。
 *
 *   现在两种模式：
 *     · [centerLabel] = false → 「名称」：文字靠左、箭头紧跟其后（左侧列没有轴线问题）
 *     · [centerLabel] = true  → 「记录时间」：文字**精确落在列中轴**，箭头贴列右缘
 */
@Composable
private fun SortHeader(
    label: String,
    active: Boolean,
    desc: Boolean,
    modifier: Modifier = Modifier,
    centerLabel: Boolean = false,
    onClick: () -> Unit
) {
    // 箭头不再用「⇅ ↓ ↑」字体字符 —— 部分机型缺字形会渲染成方框/细竖线
    val arrow: @Composable () -> Unit = {
        Icon(
            imageVector = when {
                !active -> Icons.Filled.UnfoldMore
                desc -> Icons.Filled.ArrowDownward
                else -> Icons.Filled.ArrowUpward
            },
            contentDescription = null,
            tint = if (active) AccentIndigo
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(13.dp)
        )
    }
    val text: @Composable () -> Unit = {
        Text(
            label, fontSize = 12.sp,
            // 文字不随排序变色（Web 只有 sort-icon 变靛蓝）
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (centerLabel) {
        // 光学居中：整组（文字+4dp+箭头 13dp）居中后，文字中心会偏在列轴**左侧**
        // 半个箭头组宽（因为右半边被箭头占掉了）——
        // 所以把整组往**右**挪 8.5dp，文字中心正好落回列轴，箭头紧跟在文字右边。
        // （既和下面的日期同轴，箭头也不会孤零零飘到列右缘。）
        Box(modifier.safeClickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset(x = 8.5.dp)
            ) {
                text()
                Spacer(Modifier.width(4.dp))
                arrow()
            }
        }
    } else {
        Row(
            modifier = modifier.safeClickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            text()
            Spacer(Modifier.width(3.dp))
            arrow()
        }
    }
}
