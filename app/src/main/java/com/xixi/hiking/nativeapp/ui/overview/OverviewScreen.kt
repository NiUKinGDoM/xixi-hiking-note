package com.xixi.hiking.nativeapp.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.PlannedTripEntity
import com.xixi.hiking.nativeapp.data.Prefs
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.data.parseRecordDate
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.glassPanel
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.milestones.MilestoneEntryCard
import com.xixi.hiking.nativeapp.ui.safeClickable
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import java.util.Calendar
import com.xixi.hiking.nativeapp.ui.theme.Ink
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import androidx.compose.foundation.lazy.items
import com.xixi.hiking.nativeapp.ui.liquid.glassStatic
import com.xixi.hiking.nativeapp.ui.theme.DangerRed

/**
 * ★概览页 —— 对齐 Web 版 #tab-overview 的版块顺序：
 *
 *   备份健康提示（有记录、久未备份时出现）
 *   → 引导卡（零记录时，可关）
 *   → 时光机（那年今日，三层回退；无命中整卡不显示）
 *   → 统计概览：2 + 2 + 3 共 7 张指标卡
 *   → 我的里程碑入口（N / 17）
 *   → 徒步足迹（年月可选 + 日历月视图）
 *
 * ★2026-11-02：统计卡统一成靛色系（原来 6 张 6 种色相，像调色盘），
 *   三行卡高度也统一（去掉 compact 档）。
 */
@Composable
fun OverviewScreen(
    records: List<RecordEntity>,
    hazeState: HazeState,
    onOpenRecord: (RecordEntity) -> Unit = {},
    onOpenMilestones: () -> Unit = {},
    plans: List<PlannedTripEntity> = emptyList(),
    onGoPlans: () -> Unit = {},
    onGoBackup: () -> Unit = {},
    /** 引导卡的「去记录页，写下第一笔」（对齐 Web welcomeGoBtn） */
    onGoRecords: () -> Unit = {},
    /** 顶栏是浮层，页面要给内容让出这么高 —— 内容才会从玻璃底下轧过去 */
    topInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val ctx = LocalContext.current
    // ★2026-11-04 「六项优化 ①」诊断卡：被 SafeTap 拦下的软错误累计到界面上来。
    //   以前它们只写进 filesDir/soft_error.txt，用户只偶尔瞥到一条 toast 就没了。
    var softErrors by remember {
        mutableStateOf(com.xixi.hiking.nativeapp.data.CrashGuard.softLines(ctx))
    }
    var showSoft by remember { mutableStateOf(false) }

    // 引导卡：零记录 + 没关过才显示（关掉即持久化，与 Web 版 welcome_seen 一致）
    var showGuide by remember {
        mutableStateOf(
            records.isEmpty() && !Prefs.getBool(ctx, Prefs.KEY_GUIDE_OVERVIEW, false)
        )
    }
    // ★2026-11-05 气味扫描：原写法在组合期直接置 false（组合期写 state）。
    //   改成 LaunchedEffect：数据到位后的下一帧收起，视觉上完全一样，但没有重组隐患。
    androidx.compose.runtime.LaunchedEffect(records.isNotEmpty()) {
        if (records.isNotEmpty() && showGuide) showGuide = false
    }

    // 备份健康提示：有记录 + 从没备份或超 30 天 + 没点过「知道了」
    var hintOff by remember { mutableStateOf(Prefs.getBool(ctx, Prefs.KEY_BACKUP_HINT_OFF, false)) }
    val backupHint: String? = remember(records.size, hintOff) {
        if (records.isEmpty() || hintOff) return@remember null
        val last = Prefs.getLong(ctx, Prefs.KEY_LAST_BACKUP, 0L)
        if (last == 0L) {
            // ★★2026-11-02 对齐 Web 版（app-data-lightbox.js renderBackupHint）：
            //   「从未备份」时要**最早一条记录距今 ≥ 14 天**才提示。
            //   否则刚装好、刚记两条就弹「还没备份过」，纯属打扰（Web 就是为这个加的闸）。
            val oldest = records.minOfOrNull { parseRecordDate(it.createdAt)?.time ?: Long.MAX_VALUE }
                ?: return@remember null
            val ageDays = (System.currentTimeMillis() - oldest) / 86_400_000L
            return@remember if (ageDays < 14L) null
            else "还没备份过，换手机或误删就找不回来了"
        }
        // 已备份过：超过 30 天才提醒（与 Web 的 staleness 判定一致）
        if (System.currentTimeMillis() - last <= 30L * 86_400_000L) null
        else "已经超过 30 天没备份了"
    }

    val count = records.size
    val totalDist = records.sumOf { it.distance }
    val totalMin = records.sumOf { it.duration }
    val maxElev = records.maxOfOrNull { it.elevation } ?: 0
    val avgElev = if (count > 0) (records.sumOf { it.elevation } / count) else 0
    val avgDiff = if (count > 0) records.sumOf { it.difficulty }.toDouble() / count else 0.0
    val avgMin = if (count > 0) totalMin / count else 0

    val memory = remember(records) { findTimeMachine(records) }

    // ★★弹窗必须浮在整页上，不能被滚动 Column 的约束裁住（2026-11-02）
    var pickerReq by remember { mutableStateOf<PickerRequest?>(null) }
    var dayReq by remember { mutableStateOf<DayRequest?>(null) }
    // ★回顾弹窗（2026-11-02）：YearReviewDialog 一直存在但**从没被调用过**，
    //   所以「回顾」按钮点了毫无反应。现在接上。
    var reviewYear by remember { mutableStateOf<Int?>(null) }

    val pageScroll = rememberScrollState()
    // ★2026-11-03 再点当前 tab → 平滑回顶（Web switchTab 同款）
    androidx.compose.runtime.LaunchedEffect(com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue) {
        if (com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue > 0) pageScroll.animateScrollTo(0)
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(pageScroll)
            // ★2026-11-03 对齐 Web 手机端页边距（.container padding:6px）：
            //   白框都去掉之后，两侧再留 12dp 就是白白浪费 —— 收到 6dp，卡片真正撑开。
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ★顶栏让位（2026-11-02）：留白放在**滚动内容里**，不是给整个页面加 padding ——
        //   只有这样内容才能滚到顶栏玻璃底下被模糊掉。
        Spacer(Modifier.height(topInset + 4.dp))
        // ── 引导卡（零记录时） ──
        // ★★2026-11-02 按 Web 版 welcomeBanner 重写：
        //   Web 是「icon insights 24 + 标题 14/600 + 两行说明 + 主按钮 + 脚注 + 关闭」，
        //   原生版原来是「山图标 + 三行说明 + 去计划按钮」，内容和主行动都跟 Web 不一致
        //   （零记录时最该做的是写第一条记录，不是先去排计划）。
        if (showGuide) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(corner = Radii.CARD)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Insights, null,
                        // Web：.wb-ic 24px / rgba(99,102,241,0.9)
                        tint = StatIndigo.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp).padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "这里住着你所有的脚印",
                            // Web .wb-title：14px / 600
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(3.dp))
                        GuideLine(
                            "下面每个月一张热力图，",
                            "每一格都是一次出发",
                            "；点开能翻出那天的记录。"
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            // ★断成两句两行：整句一行放不下时，末尾「理。」会被挤成孤字（截图里很难看）
                            "添加：切到下方「记录」页点右上角 ＋；\n「计划」页同理。",
                            fontSize = 12.sp, lineHeight = 19.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                // Web .glass-btn：13/600、padding 7·16、圆角 12
                                .glassStatic(corner = 12, flat = true, veil = 0.30f)
                                .safeClickable(onClick = onGoRecords)
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "去记录页，写下第一笔",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = Ink
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            // Web 脚注：10px / #64748b
                            "真正开始记录后会自动消失",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Close, "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp).safeClickable {
                            showGuide = false
                            Prefs.setBool(ctx, Prefs.KEY_GUIDE_OVERVIEW, true)
                        }
                    )
                }
            }
        }

        // ── 时光机 ──
        memory?.let { pair ->
            val r = pair.first
            val hint = pair.second

            // ★★时光机缩略图（2026-11-02 用户要求）：显示这条记录的**第一张照片**。
            //   照片不在 RecordEntity 里，要按 recordId 去 photos 表取 —— 必须异步，
            //   绝不能组合期直接查库（那正是之前设置页崩过的写法）。
            //   取不到（没拍照片 / 文件丢了 / 查库抛错）就退回山图标占位，不影响卡片。
            val firstPhoto by androidx.compose.runtime.produceState<com.xixi.hiking.nativeapp.data.PhotoEntity?>(
                initialValue = null,
                r.id
            ) {
                value = runCatching {
                    com.xixi.hiking.nativeapp.ui.settings.AppDatabaseRef.get(ctx)
                        .photoDao().byRecord(r.id).firstOrNull()
                }.getOrNull()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(corner = Radii.CARD)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // ★对齐 Web 版：标题是**靛蓝**（#818cf8）+ 0.5 字距，不是纯黑
                val memoryAccent = if (LocalIsDark.current) Color(0xFFA5B4FC) else Color(0xFF818CF8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.History, null,
                        tint = memoryAccent, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "时光机", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = memoryAccent,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().safeClickable { onOpenRecord(r) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val ph = firstPhoto
                        if (ph != null) {
                            // 缩略图：ContentScale.Crop 铺满方块，圆角由外层 clip 切
                            coil.compose.AsyncImage(
                                model = com.xixi.hiking.nativeapp.data.PhotoStore
                                    .file(ctx, ph.fileName),
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // 没照片 / 读失败：山图标占位（和原来一致）
                            Icon(
                                Icons.Filled.Landscape, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            r.name.ifBlank { "未命名" },
                            // Web 版山名是 16px/700
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                        val meta = buildList {
                            // ★Web 版只有 distance 真非 0 才显示（原来是无条件 add，0 km 也照写）
                            if (r.distance > 0) add(fmt1(r.distance) + " km")
                            if (r.difficulty > 0) add("${r.difficulty} 级")
                            if (r.mood.isNotBlank()) add(r.mood)
                            if (r.weather.isNotBlank()) add(r.weather)
                        }
                        // ★Web 版 meta 为空时**整行不渲染**（不留一条空白）
                        if (meta.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                meta.joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        Icons.Outlined.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }


        // ── 诊断卡（有软错误才出现）──
        //
        //  ★2026-11-04 对齐 Web「六项优化 ①」：出错自动出现 + 可导出 + 可清除。
        //  和崩溃横幅的区别：崩溃是进程挂了（横幅在顶栏），软错误是"某个按钮的处理函数
        //  抛了异常、被 SafeTap 拦下来了" —— 页面还在，只是那一次操作没成功。
        //  这类问题最容易积少成多，所以攒着显示、允许导出。
        if (softErrors.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(corner = Radii.CARD)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(DangerRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Warning, null,
                        tint = DangerRed, modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "有 ${softErrors.size} 次操作被拦下",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "页面还能正常用，导出后发给我就能定位",
                        fontSize = 11.5.sp, lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftErrChip("查看") {
                            softErrors = com.xixi.hiking.nativeapp.data.CrashGuard.softLines(ctx)
                            showSoft = true
                        }
                        SoftErrChip("导出") {
                            com.xixi.hiking.nativeapp.data.CrashGuard.shareDiagnostics(ctx)
                        }
                        SoftErrChip("清除") {
                            com.xixi.hiking.nativeapp.data.CrashGuard.clearSoft(ctx)
                            softErrors = emptyList()
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── 备份健康提示 ──
        if (backupHint != null) {
            // ★★2026-11-03 重排备份提示卡：
            //   上一版是「图标 + 长文案 + 去备份 + ✕」全塞一行 ——
            //   360dp 屏上文案被挤成 2 行，按钮和 ✕ 贴着卡片右边缘，非常挤。
            //   现在：图标放进小圆底 → 文案占满整行 → 操作按钮落在文案下面；
            //        ✕ 单独钉在右上角（有 28dp 的点击区），不与文案抢宽度。
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPanel(corner = Radii.CARD)
                        .padding(start = 14.dp, top = 13.dp, end = 42.dp, bottom = 13.dp)
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFF59E0B).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Shield, null,
                            // Web 版是琥珀色 #f59e0b（提醒色），不是主色
                            tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            backupHint,
                            fontSize = 12.5.sp, lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(9.dp))
                        Box(
                            modifier = Modifier
                                .liquidGlassChip(corner = Radii.INPUT, flat = true)
                                .safeClickable(onClick = onGoBackup)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("去备份", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                // ✕ 钉右上角：独立 28dp 点击区，不挤占文案宽度
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .safeClickable {
                            Prefs.setBool(ctx, Prefs.KEY_BACKUP_HINT_OFF, true)
                            hintOff = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close, "不再提示",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // ── 统计概览 ──
        // ★★2026-11-02 对齐 Web 版结构：Web 用一个 .glass-panel 把
        //   「标题 + 4 张主卡 + 3 张矮副卡 + 里程碑入口」整体包住（padding 24）。
        //   原生版之前是散在页面背景上的 —— 和「徒步足迹」那张卡视觉上不是一套。
        //   现在补上外层大卡，两组内容才对称。
        // ★2026-11-03 去掉「统计概览」外层白底框（用户要求）：
        //   里面已经套了 14dp 内边距，外面还有页面 16dp —— 内容被挤窄 60dp，
        //   两侧白白空着。现在标题和四色卡直接铺在页面上。
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ★emoji 换图标（项目规则：不用 emoji）
            Icon(
                Icons.Filled.Analytics, null,
                // Web 版这个图标是蓝色（text-blue-300），不是全站主色
                tint = StatBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                // Web h2 是 text-2xl；手机端 media query 压到 1.1rem = 17.6px
                "统计概览", fontSize = 17.5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // ★2026-11-03 重排：2×2 主卡原本是「两行 Row 直接排在父 Column 里」，
        //   横向间距 12、纵向间距 10（父容器给的）—— 一横一竖不一致，看着就是"没对齐"。
        //   现在包一层 Column(spacedBy(12))，横竖都是 12，网格才是方的。
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("总记录数", "$count", "次", StatBlue, Modifier.weight(1f), icon = Icons.Filled.Hiking)
                StatCard("总里程", fmt1(totalDist), "km", StatTeal, Modifier.weight(1f), icon = Icons.Filled.Route)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("总用时", fmtH(totalMin), "h", StatRose, Modifier.weight(1f), icon = Icons.Filled.Schedule)
                StatCard("最高海拔", "$maxElev", "m", StatOrange, Modifier.weight(1f), icon = Icons.Filled.Terrain)
            }
        }
        // ★三张「矮副卡」（对齐 Web 版 .ov-mini：图标在左、文字在右、左侧 3dp 色条）
        //   之前它们和主卡用同一个 StatCard，高度一样 —— 主次完全分不出来。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(
                // ★Web 版这一格用「height」图标（海拔），不是趋势箭头
                "平均海拔", "$avgElev", "m", MiniGreen,
                Icons.Filled.Height, Modifier.weight(1f)
            )
            MiniStat(
                "平均难度",
                if (count > 0) String.format(java.util.Locale.getDefault(), "%.1f", avgDiff) else "—",
                // ★没有记录时不要拼出「—级」—— 远看像"一级"，误导成难度 1
                if (count > 0) "级" else "",
                MiniPurple,
                Icons.Filled.Star, Modifier.weight(1f)
            )
            MiniStat(
                "平均用时", fmtH(avgMin), "h", MiniSky,
                Icons.Filled.Schedule, Modifier.weight(1f)
            )
        }

        // ── 里程碑入口 ──
        MilestoneEntryCard(records = records, onClick = onOpenMilestones)
        }   // ← 统计概览大卡结束

        // ── 徒步足迹 ──
        HeatmapCard(
            records = records,
            hazeState = hazeState,
            onOpenRecord = onOpenRecord,
            onRequestPicker = { pickerReq = it },
            onRequestDay = { req ->
                // ★★2026-11-03 用户：「热力图这种的，点开之后直接显示记录弹窗，不用中间那个小弹窗」
                //   一天只有一条记录时没有"选择"的必要 → 直接开那条记录的详情。
                //   一天有多条时才保留「今天有哪几次」的列表（否则用户没法选）。
                val y = req.ym / 100
                val m = req.ym % 100
                val sameDay = req.records.filter { r ->
                    val d = parseRecordDate(r.createdAt) ?: return@filter false
                    val c = java.util.Calendar.getInstance().apply { time = d }
                    c.get(java.util.Calendar.YEAR) == y &&
                        c.get(java.util.Calendar.MONTH) + 1 == m &&
                        c.get(java.util.Calendar.DAY_OF_MONTH) == req.day
                }
                if (sameDay.size == 1) onOpenRecord(sameDay.first()) else dayReq = req
            },
            onOpenReview = { reviewYear = it }
        )

        Spacer(Modifier.height(100.dp))
    }

    // ══ 年月选择弹窗（浮在整页中央）══
    pickerReq?.let { req ->
        GlassOverlay(
            hazeState = hazeState,
            onDismiss = { pickerReq = null },
            maxWidthFraction = 0.86f,
            corner = 22
        ) {
            // ★★2026-11-05 修「点年份整个弹窗就关了」（用户原话）
            //
            //   原来年份 chip 的点击是 `onPick(y, 当前月); pickerReq = null` ——
            //   它把"选年"当成了"选完"，于是点 2025 弹窗直接消失，
            //   用户还没选月份就被踢出去。
            //   现在拆成两步：点年份**只换**本地 pickedYear（月份格跟着换），
            //   点月份才真正 onPick + 关闭。这也是用户要的"选完月份再自动关"。
            var pickedYear by androidx.compose.runtime.remember(req) {
                androidx.compose.runtime.mutableStateOf(req.year)
            }
            val monthSet = req.monthsByYear[pickedYear].orEmpty()
            val nowC = java.util.Calendar.getInstance()
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "选择年月", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Text("年份", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    req.years.take(4).forEach { y ->
                        YMChip("$y", y == pickedYear, true, Modifier.weight(1f)) {
                            // 只切年份，不关弹窗
                            pickedYear = y
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("月份", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 3) {
                                val m = row * 3 + col + 1
                                val has = monthSet.contains(m) ||
                                    (pickedYear == nowC.get(java.util.Calendar.YEAR)
                                        && m == nowC.get(java.util.Calendar.MONTH) + 1)
                                YMChip("$m 月", m == req.month && pickedYear == req.year, has, Modifier.weight(1f)) {
                                    if (has) {
                                        // 选完月份 → 应用并关闭
                                        req.onPick(pickedYear, m)
                                        pickerReq = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ══ 年度回顾 ══
    reviewYear?.let {
    // ── 软错误详情（诊断卡的「查看」）──
    if (showSoft) {
        com.xixi.hiking.nativeapp.ui.GlassOverlay(
            hazeState = hazeState,
            onDismiss = { showSoft = false }
        ) {
            Column(Modifier.fillMaxWidth()) {
                androidx.compose.material3.Text(
                    "最近的操作异常",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                androidx.compose.material3.Text(
                    "只显示最近 10 条，给开发者看的那几行",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    softErrors.take(10).forEach { line ->
                        androidx.compose.material3.Text(
                            line,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }

        YearReviewDialog(
            records = records,
            hazeState = hazeState,
            // ★2026-11-05 默认看**今年**（用户要求）。
            //   原来传的是 y（最近一条记录的年份）—— 今年还没写记录时，
            //   一打开年度回顾就是"今年还没留下徒步足迹"，看着像数据丢了。
            initialYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
            onDismiss = { reviewYear = null }
        )
    }

    // ══ 某天记录弹窗（同样浮在整页中央）══
    dayReq?.let { req ->
        val y = req.ym / 100
        val m = req.ym % 100
        val list = remember(req.records, req.ym, req.day) {
            req.records.filter { r ->
                val d = parseRecordDate(r.createdAt) ?: return@filter false
                val c = java.util.Calendar.getInstance().apply { time = d }
                c.get(java.util.Calendar.YEAR) == y &&
                    c.get(java.util.Calendar.MONTH) + 1 == m &&
                    c.get(java.util.Calendar.DAY_OF_MONTH) == req.day
            }
        }
        GlassOverlay(
            hazeState = hazeState,
            onDismiss = { dayReq = null },
            maxWidthFraction = 0.88f,
            corner = 22,
            maxHeightFraction = 0.80f
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$y 年 $m 月 ${req.day} 日",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${list.size} 条", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { r ->
                        DayRecordRow(r) { dayReq = null; onOpenRecord(r) }
                    }
                }
            }
        }
    }
    }
}

/** 年月选择的小方块（平面） */
@Composable
private fun YMChip(
    text: String,
    on: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = LocalIsDark.current
    Box(
        modifier = modifier
            .glassStatic(corner = 10, flat = true, veil = if (on) 0.55f else 0.25f)
            .safeClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                on -> Ink
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun GuideLine(bold: String, normal: String, tail: String) {
    Text(
        buildString { append(bold); append(normal); append(tail) },
        fontSize = 12.sp,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * ★时光机（对齐 Web 版 renderOnThisDay 的三层回退）：
 *   ① 往年的同月同日 → 「N 年前的今天」
 *   ② 往年的同月    → 「N 年前的 X 月」
 *   ③ 恰好整百/整年天（±1 天）→ 「N 天前」
 * 全都没有就返回 null（整卡不渲染 —— 宁缺毋滥，避免误导）
 */
private fun findTimeMachine(records: List<RecordEntity>): Pair<RecordEntity, String>? {
    if (records.isEmpty()) return null
    val now = Calendar.getInstance()
    val mm = now.get(Calendar.MONTH) + 1
    val dd = now.get(Calendar.DAY_OF_MONTH)
    val yy = now.get(Calendar.YEAR)

    data class Item(val r: RecordEntity, val date: Calendar, val year: Int, val month: Int, val day: Int)

    val items = records.mapNotNull { r ->
        val d = parseRecordDate(r.createdAt) ?: return@mapNotNull null
        val c = Calendar.getInstance().apply { time = d }
        Item(r, c, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
    if (items.isEmpty()) return null

    items.filter { it.month == mm && it.day == dd && it.year < yy }
        .maxByOrNull { it.date.timeInMillis }
        ?.let { return it.r to "${yy - it.year} 年前的今天" }

    items.filter { it.month == mm && it.year < yy }
        .maxByOrNull { it.date.timeInMillis }
        ?.let { return it.r to "${yy - it.year} 年前的 $mm 月" }

    val marks = listOf(100, 200, 300, 365, 500, 730, 1000, 1460, 1825, 2555, 3650)
    for (m in marks) {
        val hit = items.filter {
            val days = Math.abs(((now.timeInMillis - it.date.timeInMillis) / 86_400_000L).toInt() - m)
            days <= 1
        }
        hit.maxByOrNull { it.date.timeInMillis }?.let { return it.r to "$m 天前" }
    }
    return null
}

/**
 * 指标卡：左边缘竖条 + 小标签 + 大数值 + 右侧图标。
 * ★高度用 IntrinsicSize.Min 让竖条跟着内容长；所有卡统一尺寸（不再分 compact 档），
 *   2 / 2 / 3 三行看起来才是一套系统。
 */

// ★概览页四主色 —— **直接取 Web 的真实值**（app-modal.css .glass-stat-card.xxx-accent）：
//   blue-accent 其实是 rgba(99,102,241) 的**靛蓝**（不是 blue-500 的 #3B82F6），
//   orange-accent 是 orange-400 #FB923C（不是 orange-500 #F97316）。
private val StatIndigo = Color(0xFF6366F1)
private val StatBlue = StatIndigo          // 保留旧名，调用点不用改
private val StatTeal = Color(0xFF14B8A6)
private val StatRose = Color(0xFFF43F5E)
private val StatOrange = Color(0xFFFB923C)
// 三副色：绿 / 紫 / 天蓝
private val MiniGreen = Color(0xFF10B981)
private val MiniPurple = Color(0xFFA855F7)
private val MiniSky = Color(0xFF38BDF8)

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val isDark = LocalIsDark.current
    // ★★平面化（2026-11-02 全站统一平面）：去掉 shadow + clip，
    //   用 drawBehind 画「圆角底 + 左侧 3dp 色条 + 1dp 细边」，零离屏层。
    Row(
        modifier = modifier
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            // ★★★色条修法（2026-11-02）：之前给 3dp 宽的条套了 16dp 圆角半径，
            //   圆角半径 > 条宽，画出来是个溢出卡片的变形块（用户一眼指出）。
            //   正确做法：**先把卡片裁成圆角，再在里面画直角色条** ——
            //   色条左边缘被卡片圆角自然切掉，贴合得严丝合缝。
            .drawWithContent {
                val r = androidx.compose.ui.geometry.CornerRadius(
                    Radii.SUB.dp.toPx(), Radii.SUB.dp.toPx()
                )
                val outline = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            0f, 0f, size.width, size.height, r
                        )
                    )
                }
                clipPath(outline) {
                    // ★2026-11-03 按 Web `.glass-stat-card`：背景 rgba(255,255,255,0.05)（深色 rgba(30,41,59,0.06)）
                    drawRect(
                        color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.06f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    // 直角色条，被上面的圆角裁切
                    drawRect(
                        // ★Web 是 rgba(accent, 0.32) —— 色条只是点缀，不是四根荧光棒
                        color = accent.copy(alpha = 0.32f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                    )
                }
                // ★2026-11-03 加回一条**极淡**发丝边（0.06）：
                //   之前完全去掉描边，白卡压在半透明白面板上，卡形读不出来 ——
                //   截图里只剩四根彩色条"浮"在面板上，反而更乱。
                //   0.06 的边在浅色下几乎看不见，但足够勾出圆角；深色用 0.10 才立得住。
                drawRoundRect(
                    color = if (isDark) Color.White.copy(alpha = 0.08f)
                    else Color(0xFF000000).copy(alpha = 0.04f),
                    cornerRadius = r,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                drawContent()
            }
    ) {
        // 占位：原来这里是色条 Box，色条已移进 drawBehind
        Spacer(Modifier.width(3.dp).fillMaxHeight())
        // ★Web 规格：.glass-stat-card padding 13；标签 13px；数值 text-xl=20px；单位 13px/0.8
        Column(Modifier.padding(start = 13.dp, top = 12.dp, bottom = 12.dp, end = 13.dp)) {
            Text(
                label, fontSize = 13.sp,
                color = accent,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, color = Ink
                )
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(1.dp))
                    Text(
                        // .stat-unit：13px / 600 / opacity .8 / 基线对齐
                        // ★颜色必须**继承数值色**（Ink 0.8），不是灰 —— Web 的 stat-unit 就是继承
                        unit, fontSize = 13.sp,
                        color = Ink.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
        if (icon != null) {
            Spacer(Modifier.weight(1f))
            Icon(
                icon, null,
                // 图标也用强调色，和标签同色 —— 这是 web 版的"一卡一色"
                tint = accent,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 12.dp)
                    .size(24.dp)
            )
        }
    }
}

private fun fmtH(min: Int): String {
    val h = min / 60.0
    return if (h % 1.0 == 0.0) h.toInt().toString()
    else String.format(java.util.Locale.getDefault(), "%.1f", h)
}

/**
 * 矮副卡（对齐 Web 版 `.ov-mini`）：
 * 左侧 3dp 色条 + 小图标 + 「标签上 / 数值下」，整块只有主卡的一半高。
 * 平面绘制（drawBehind），无阴影无离屏层。
 */
@Composable
private fun MiniStat(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDark.current
    Row(
        modifier = modifier
            // 同 StatCard：裁圆角 → 内部画直角色条
            .drawWithContent {
                // ★Web `.ov-mini`：圆角 12（原来是 SUB=16，和主卡的 16 撞在一起没有大小区分）
                val miniR = 12.dp.toPx()
                val r = androidx.compose.ui.geometry.CornerRadius(miniR, miniR)
                val outline = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            0f, 0f, size.width, size.height, r
                        )
                    )
                }
                clipPath(outline) {
                    // ★2026-11-03 按 Web `.glass-stat-card`：背景 rgba(255,255,255,0.05)（深色 rgba(30,41,59,0.06)）
                    drawRect(
                        color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.06f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    drawRect(
                        // ★Web 色条是 rgba(...,0.4) 的淡色，不是 0.7 —— 副卡是点缀，不能抢主卡
                        color = accent.copy(alpha = 0.40f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                    )
                }
                // 同主卡：一条极淡发丝边，把卡形勾出来
                drawRoundRect(
                    color = if (isDark) Color.White.copy(alpha = 0.08f)
                    else Color(0xFF000000).copy(alpha = 0.04f),
                    cornerRadius = r,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                drawContent()
            }
            // ★2026-11-03 收紧：横向 12→10（窄屏 360dp 上「平均海拔」四个字会顶到边）
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ★图标 18→15、间距 9→5：三张卡是等分宽度，360dp 屏上原尺寸会把
        //   「平均海拔」这类四字标签挤到换行/截断（用户截图反馈）
        Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Column {
            Text(
                label, fontSize = 11.sp, color = accent,
                fontWeight = FontWeight.Medium, maxLines = 1,
                letterSpacing = (-0.2).sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = Ink, maxLines = 1
                )
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(1.dp))
                    Text(
                        unit, fontSize = 11.sp,
                        color = Ink.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 诊断卡里的小按钮（和备份卡的「去备份」同一套玻璃胶囊）。
 */
@androidx.compose.runtime.Composable
private fun SoftErrChip(label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .liquidGlassChip(corner = Radii.INPUT, flat = true)
            .safeClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        androidx.compose.material3.Text(
            label, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold
        )
    }
}
