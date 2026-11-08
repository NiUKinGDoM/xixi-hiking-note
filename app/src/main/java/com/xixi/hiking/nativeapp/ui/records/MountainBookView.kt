package com.xixi.hiking.nativeapp.ui.records

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xixi.hiking.nativeapp.data.MtnMerge
import com.xixi.hiking.nativeapp.data.PhotoEntity
import com.xixi.hiking.nativeapp.data.PhotoStore
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.data.parseRecordDate
import com.xixi.hiking.nativeapp.ui.GlassButton
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.GlassTextField
import com.xixi.hiking.nativeapp.ui.photos.PhotoLightbox
import com.xixi.hiking.nativeapp.ui.overview.fmt1
import com.xixi.hiking.nativeapp.ui.overview.fmtDuration
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import java.util.Calendar
import com.xixi.hiking.nativeapp.ui.theme.Ink

/**
 * ★山册（1:1 对齐 Web 版 renderMountainBook / renderMountainCard）
 *
 *   · 双列名册：每两座山一组，卡头并排
 *   · 集章序号：按「第一次去的先后」编号 01 / 02 / 03 …
 *   · 海拔书脊：卡片右缘 + 下缘各 4dp 彩带，按最高海拔分 5 档（低山青绿 → 高山紫）
 *   · 点卡头 → 在该行下方以**整行宽度**拉开抽屉：照片回忆 + 6 格统计 + 最近一次
 *   · 顶部常驻海拔色带图例
 *
 * ⚠️ 抽屉必须「整行」而不是塞在卡片里 —— 两列卡片只有半屏宽，照片和 6 格统计挤不下。
 */
private data class Ridge(val bar: Color, val barDark: Color)

private fun ridgeOf(maxElev: Int): Ridge? = when {
    maxElev <= 0 -> null
    maxElev < 1000 -> Ridge(Color(0xFF0D9488), Color(0xFF2DD4BF))
    maxElev < 2000 -> Ridge(Color(0xFF0284C7), Color(0xFF0EA5E9))
    maxElev < 3000 -> Ridge(Color(0xFF2563EB), Color(0xFF3B82F6))
    maxElev < 4000 -> Ridge(Color(0xFF4338CA), Color(0xFF818CF8))
    else -> Ridge(Color(0xFF9333EA), Color(0xFFC084FC))
}

private val RIDGE_LEGEND = listOf(
    "<1k" to Ridge(Color(0xFF0D9488), Color(0xFF2DD4BF)),
    "1-2k" to Ridge(Color(0xFF0284C7), Color(0xFF0EA5E9)),
    "2-3k" to Ridge(Color(0xFF2563EB), Color(0xFF3B82F6)),
    "3-4k" to Ridge(Color(0xFF4338CA), Color(0xFF818CF8)),
    "4k+" to Ridge(Color(0xFF9333EA), Color(0xFFC084FC))
)

private data class MtnGroup(
    val key: String,
    val label: String,        // 显示名（连接器自定义名优先）
    val sealNo: String,
    val visits: Int,
    val km: Double,
    val minutes: Int,
    val maxElev: Int,
    val avgDiff: Int,
    val companions: List<String>,
    val lastMillis: Long,
    val records: List<RecordEntity>,
    val photos: List<PhotoEntity>,
    val isMergeTarget: Boolean = false
)

@Composable
fun MountainBookView(
    records: List<RecordEntity>,
    photos: List<PhotoEntity>,
    hazeState: HazeState,
    onOpenRecord: (RecordEntity) -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * 页面顶部块（工具行 / 说明 / 引导卡）。
     *
     * ★2026-11-02：山册改成「整页一起滚」—— 顶部块必须跟着内容滚走，
     *   钉死在上面会在滚动时出现一条硬切边（用户说的"遮罩"）。
     *   它自带 LazyColumn，所以只能把顶部块作为 LazyColumn 的第一项塞进来。
     */
    header: (@Composable () -> Unit)? = null
) {
    val isDark = LocalIsDark.current
    val ctx = LocalContext.current
    // 连接器变更后要重算分组（link/unlink 写在 Prefs 里，不是 Compose state）
    var mergeTick by remember { mutableStateOf(0) }
    var mergeFor by remember { mutableStateOf<MtnGroup?>(null) }

    // ★按山名分组 + 连接器归并（同一座山写成不同名字能连成一张）
    val groups = remember(records, photos, mergeTick) {
        val named = records.filter { it.name.trim().isNotEmpty() }
        if (named.isEmpty()) return@remember emptyList()

        // ① 先按「归并后的键」聚合
        val byKey = named.groupBy { MtnMerge.keyOf(ctx, it.name) }
        fun firstAt(list: List<RecordEntity>): Long =
            list.minOfOrNull { parseRecordDate(it.createdAt)?.time ?: Long.MAX_VALUE } ?: Long.MAX_VALUE

        val keys = byKey.keys.filter { it.isNotEmpty() }.sortedWith(
            compareBy<String> { firstAt(byKey[it]!!) }
                .thenByDescending { byKey[it]!!.size }
        )
        val padLen = maxOf(2, keys.size.toString().length)
        val photosByRecord = photos.groupBy { it.recordId }

        keys.mapIndexed { idx, k ->
            val list = byKey[k]!!.sortedByDescending { it.createdAt }
            val allPhotos = list.flatMap { photosByRecord[it.id].orEmpty() }
            val comp = list
                .flatMap { r -> r.companions.split(Regex("[,，、\\s]+")) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(3)
            MtnGroup(
                key = k,
                label = MtnMerge.labelOf(ctx, k),
                sealNo = (idx + 1).toString().padStart(padLen, '0'),
                visits = list.size,
                km = list.sumOf { it.distance },
                minutes = list.sumOf { it.duration },
                maxElev = list.maxOfOrNull { it.elevation } ?: 0,
                avgDiff = (list.sumOf { it.difficulty } / list.size),
                companions = comp,
                lastMillis = list.mapNotNull { parseRecordDate(it.createdAt)?.time }.maxOrNull() ?: 0L,
                records = list,
                photos = allPhotos.take(24),
                isMergeTarget = MtnMerge.isMergeTarget(ctx, k)
            )
        }
    }

    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    // ★★2026-11-03 用户：「山册页，再按一次底栏按钮，应该关闭所有山册再划到顶」
    //   记录页的列表视图已经接了 TabReselect（再点当前 tab → 平滑回顶），
    //   但山册是**另一个滚动容器**（LazyColumn），它有自己的 scrollState，
    //   所以点底栏时列表页回顶了、山册纹丝不动。而且展开的山卡抽屉也得一起收起来 ——
    //   否则"回到顶部"之后下面还是挂着七八个抽屉，看着像没刷新。
    val listState = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(
        com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue
    ) {
        if (com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue > 0) {
            expanded = emptySet()          // ① 先收起所有山卡抽屉
            listState.animateScrollToItem(0)   // ② 再平滑回顶
        }
    }

    if (groups.isEmpty()) {
        Column(modifier.fillMaxWidth()) {
            header?.invoke()
            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "还没有任何记录", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "去「列表」添加第一条，山册会自动在这里汇总",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        // ★尾巴 120dp：最后一张山卡能滚到浮起的底栏之上（底部不再留死区）
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 顶部块（工具行 / 说明 / 引导卡）——跟着内容一起滚
        header?.let { item(key = "page-header") { it() } }
        // 图例
        item(key = "legend") {
            Row(
                // ★2026-11-03 修「海拔说明偏移」：这条图例原来贴在屏幕最左(x=0)，
                //   而下面的山卡统一内缩 18dp —— 差这 18dp，图例就像"掉出去"了。
                //   现在跟着卡片一起 18dp，正好压在山卡的左缘上。
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text(
                    "海拔", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RIDGE_LEGEND.forEach { (label, ridge) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(width = 14.dp, height = 7.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isDark) ridge.barDark else ridge.bar)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            label, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 每两座山一组：卡头并排 + 各自整行抽屉
        groups.chunked(2).forEachIndexed { rowIdx, pair ->
            item(key = "row-$rowIdx") {
                Column {
                    // ★变窄：两列整体再内缩 18dp —— 用户两次说"太宽"，
                    //   卡宽从约 168dp 收到约 150dp（内容 130dp 出头，仍放得下 6 个字）
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        pair.forEach { g ->
                            MtnCard(
                                group = g,
                                expanded = expanded.contains(g.key),
                                isDark = isDark,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    expanded = if (expanded.contains(g.key)) expanded - g.key
                                    else expanded + g.key
                                }
                            )
                        }
                        // 奇数个时补一个空位，避免最后一张卡被拉成整行宽
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    // ★整行抽屉（紧随卡头行下方，不在卡片内部）
                    pair.forEach { g ->
                        AnimatedVisibility(
                            visible = expanded.contains(g.key),
                            enter = expandVertically(tween(220)) + fadeIn(tween(160)),
                            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
                        ) {
                            MtnDrawer(
                                group = g,
                                onOpenRecord = onOpenRecord,
                                isDark = isDark,
                                onMerge = { mergeFor = g },
                                onUnlink = {
                                    MtnMerge.unlink(ctx, g.key)
                                    mergeTick++
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 连接器弹窗 ──
    mergeFor?.let { target ->
        MtnMergeDialog(
            current = target,
            allGroups = groups,
            hazeState = hazeState,
            onDismiss = { mergeFor = null },
            onConfirm = { picked, name ->
                MtnMerge.link(ctx, picked, target.key, name)
                mergeFor = null
                mergeTick++
            }
        )
    }
}

@Composable
private fun MtnCard(
    group: MtnGroup,
    expanded: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val ridge = ridgeOf(group.maxElev)
    val rotate by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "mtnArrow")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            // ★★★2026-11-03 恢复白框，并按 Web `.mb-card` 的精确值（app-modal.css:2012）：
            //     浅色 background rgba(255,255,255,0.32) / border 1px rgba(255,255,255,0.5)
            //     深色 background rgba(255,255,255,0.06) / border rgba(255,255,255,0.12)
            //     圆角 16
            //   上一轮我把它整块去掉了，是理解错了 —— 用户要的是"别去掉"，只是嫌太宽。
            .background(
                if (isDark) Color.White.copy(alpha = 0.06f)
                else Color.White.copy(alpha = 0.32f)
            )
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.50f),
                RoundedCornerShape(16.dp)
            )
            .drawWithContent {
                drawContent()
                // ★海拔书脊：**左缘**（2026-11-03 用户要求「竖条放左边」）
                //   右侧那条竖条在中文阅读习惯里像"书页边"，放左边才像书脊/侧标；
                //   而且左边贴着色条后，右侧留白会显得卡片没那么"宽"。
                if (ridge != null) {
                    val c = if (isDark) ridge.barDark else ridge.bar
                    val t = 3.dp.toPx()
                    drawRect(
                        color = c,
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(t, size.height)
                    )
                }
            }
            .safeClickable(onClick = onClick)
            // 书脊 4dp 在左，正文再让 10dp；右侧收到 12dp —— 卡片整体更紧凑
            .padding(start = 12.dp, top = 9.dp, end = 9.dp, bottom = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 集章圆章
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDark) Color(0xFF818CF8).copy(alpha = 0.13f)
                        else AccentIndigo.copy(alpha = 0.07f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    group.sealNo,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFA5B4FC) else Color(0xFF534AB7)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                group.label,
                // 卡片缩小：单行 + 省略，两行会把卡撑回原来的高度
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.ExpandMore, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotate }
            )
        }
    }
}

@Composable
private fun MtnDrawer(
    group: MtnGroup,
    onOpenRecord: (RecordEntity) -> Unit,
    isDark: Boolean,
    onMerge: () -> Unit,
    onUnlink: () -> Unit
) {
    // 照片灯箱：null = 关着，非 null = 从第几张打开
    var lightboxAt by remember { mutableStateOf<Int?>(null) }
    // 「全部 N 次记录」是否展开
    var showAllRecords by remember(group.key) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDark) Color.White.copy(alpha = 0.06f)
                else Color.White.copy(alpha = 0.32f)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // 照片回忆
        if (group.photos.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "照片回忆", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                val lastDate = group.records.firstOrNull()?.createdAt?.take(10) ?: ""
                Text(
                    "${group.photos.size} 张" + if (lastDate.isNotBlank()) " · $lastDate" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            // ★照片 strip 收「这座山的全量」（跨记录）—— 只给当前记录会「N 张只能翻 M 张」
            // ★点击直接开灯箱（对齐 Web 版）：strip 里是跨记录的 N 张，就该能翻这 N 张。
            //   原来点一张就跳到某条记录，翻到第 3 张又断在那里 —— 正是「N 张只能翻 M 张」。
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(group.photos, key = { _, p -> p.id }) { idx, p ->
                    AsyncImage(
                        model = PhotoStore.file(LocalContext.current, p.fileName),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                            .safeClickable { lightboxAt = idx }
                    )
                }
            }
            // ★★★2026-11-03 修「点开山册里有照片的记录，直接弹照片且关不掉」：
            //   上一版这里是无条件渲染 PhotoLightbox —— `lightboxAt` 默认 null，
            //   于是 startIndex 取 0、灯箱**在抽屉展开的一瞬间就自己开了**；
            //   而 onDismiss 只是把它设回 null（本来就是 null）⇒ 点了也关不掉。
            //   必须让 null 表示"不显示"，这里加 let 才渲染。
            lightboxAt?.let { at ->
                PhotoLightbox(
                    photos = group.photos,
                    startIndex = at.coerceIn(0, group.photos.lastIndex.coerceAtLeast(0)),
                    onDismiss = { lightboxAt = null },
                    title = group.label
                )
            }
        }

        // 统计 6 格
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f))
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
            StatCell("${group.visits} 次", "累计", Modifier.weight(1f))
            StatCell(if (group.km > 0) fmt1(group.km) + "km" else "—", "总里程", Modifier.weight(1f))
            StatCell(if (group.minutes > 0) fmtDuration(group.minutes) else "—", "总用时", Modifier.weight(1f))
            StatCell(if (group.maxElev > 0) "${group.maxElev}m" else "—", "最高海拔", Modifier.weight(1f))
            StatCell(diffNameOnly(group.avgDiff), "平均难度", Modifier.weight(1f))
            StatCell(
                if (group.companions.isNotEmpty()) group.companions.joinToString("/") else "—",
                "一起走过", Modifier.weight(1f), small = true
            )
        }

        // ★「查看全部 N 次记录」—— 之前抽屉里只有照片、统计和「最近一次」，
        //   用户看到「累计 5 次」却翻不到那 5 次到底是哪天、走了多少。
        //   Web 版也没有这个入口，属两个版本共有的可用性缺口，这里补上。
        if (group.records.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(Radii.INPUT.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFFF4F6FB))
                    .safeClickable { showAllRecords = !showAllRecords }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List, null,
                    tint = Ink, modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (group.visits > 1) "查看全部 ${group.visits} 次记录" else "查看这次记录",
                    fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ExpandMore, null,
                    tint = Ink.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer { rotationZ = if (showAllRecords) 180f else 0f }
                )
            }
            AnimatedVisibility(
                visible = showAllRecords,
                enter = expandVertically(tween(200)) + fadeIn(tween(140)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(100))
            ) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    group.records.forEach { r ->
                        MtnRecordLine(record = r, isDark = isDark) { onOpenRecord(r) }
                    }
                }
            }
        }

        // 最近一次 + 连接（并排一行，连接靠右 —— 对齐 Web 版，不让它单独占一行）
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (group.lastMillis > 0) {
                val days = Math.floor(((System.currentTimeMillis() - group.lastMillis) / 86_400_000.0)).toInt()
                val c = Calendar.getInstance().apply { timeInMillis = group.lastMillis }
                val date = "${c.get(Calendar.YEAR)}-" +
                    (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0') + "-" +
                    c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                Text(
                    (if (group.visits > 1) "最近一次" else "唯一一次") +
                        " · " + (if (days <= 0) "今天" else "$days 天前") + "（$date）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            // ★连接器：把「其实是同一座山」的几张连成一张（只在山册里生效，记录不动）
            // ★2026-11-03：条件补上「有自定义名」—— 只改过名、没连过别的山的卡，
            //   也得有路可以断开（把名字还原成原名），否则改错了就没法回头。
            if (group.isMergeTarget || group.label != group.key) {
                DrawerPill("断开连接", Color(0xFF94A3B8), onUnlink)
                Spacer(Modifier.width(6.dp))
            }
            // 已连接的卡，这个入口同时承担「改名」
            DrawerPill(
                if (group.isMergeTarget || group.label != group.key) "改名 / 再连一座" else "连接到另一座山",
                AccentIndigo, onMerge
            )
        }
    }
}

@Composable
private fun DrawerPill(text: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.09f))
            .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            .safeClickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * ★连接器弹窗：把当前这座山与勾选的其它山连成一张，并起个名字。
 *   只在山册里合并 —— 记录、统计、里程碑一律不动。
 */
@Composable
private fun MtnMergeDialog(
    current: MtnGroup,
    allGroups: List<MtnGroup>,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onConfirm: (picked: List<String>, name: String) -> Unit
) {
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var name by remember { mutableStateOf(current.label) }

    val others = allGroups.filter { it.key != current.key && !it.isMergeTarget }

    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.88f,
        corner = 22,
        maxHeightFraction = 0.80f
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "连接到另一座山",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "「${current.label}」和哪几座其实是同一座？勾上后次数、里程、最高海拔、照片会加在一起。\n只在山册里合并，记录本身一条不动。",
                fontSize = 12.sp, lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            if (others.isEmpty()) {
                Text(
                    // ★2026-11-03：没有可连的山 ≠ 这个弹窗没用 —— 它同时是「改名」入口。
                    //   原来这里直接结束（连输入框都不显示），已经连过的山想换个名字根本没有路。
                    "山册里还没有别的山可以连",
                    fontSize = 13.sp, lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    items(others, key = { it.key }) { g ->
                        val on = picked.contains(g.key)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (on) AccentIndigo.copy(alpha = 0.10f)
                                    else Color.Transparent
                                )
                                .safeClickable {
                                    picked = if (on) picked - g.key else picked + g.key
                                }
                                .padding(horizontal = 9.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(17.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (on) AccentIndigo else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (on) AccentIndigo else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (on) Icon(
                                    Icons.Filled.Check, null,
                                    tint = Color.White, modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(
                                g.label,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                "${g.visits} 次",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            // ★名字输入框移到 if 外面：无论有没有可连的山，都能改名
            Spacer(Modifier.height(10.dp))
            Text(
                "这张卡叫什么都行，合并后就用这个名字",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(5.dp))
            GlassTextField(
                value = name,
                onValueChange = { if (it.length <= 12) name = it },
                placeholder = "例如：朱雀森林公园",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) {
                    GlassButton("取消", onClick = com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current)
                }
                Box(Modifier.weight(1f)) {
                    GlassButton(
                        // 按钮文案跟着做的事实：勾了山 = 连接（可能带改名）；没勾只改名
                        if (picked.isEmpty()) "保存名字" else "连接",
                        primary = true,
                        // ★★2026-11-03：原来这里只判 picked.isNotEmpty() —— 于是「只想改名」
                        //   永远点不动（已经连过的山，可勾列表是空的，按钮死灰）。
                        //   现在改了名字也算有效操作。
                        enabled = picked.isNotEmpty() || name.trim() != current.label,
                        onClick = {
                            onConfirm(
                                picked.toList(),
                                name.trim().ifBlank { current.label }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = if (small) 11.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 只要档名（山册 6 格统计宽度很紧，"3 级 · 中等" 会溢出） */
private fun diffNameOnly(d: Int): String = when (d) {
    1 -> "简单"
    2 -> "较易"
    3 -> "中等"
    4 -> "较难"
    5 -> "困难"
    else -> "—"
}

/** 山册抽屉里的一条历史记录：日期 + 里程/时间/难度，点击进详情 */
@Composable
private fun MtnRecordLine(
    record: RecordEntity,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val d = parseRecordDate(record.createdAt)
    val c = d?.let { Calendar.getInstance().apply { time = it } }
    val date = if (c != null) {
        "${c.get(Calendar.YEAR)}-${(c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}-" +
            c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    } else record.createdAt.take(10)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(Radii.INPUT.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.White)
            .safeClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(Radii.TICK.dp))
                .background(AccentIndigo.copy(alpha = 0.55f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            date, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(10.dp))
        Text(
            buildString {
                if (record.distance > 0) append(fmt1(record.distance) + "km")
                if (record.duration > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(fmtDuration(record.duration))
                }
                if (record.elevation > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${record.elevation}m")
                }
            }.ifBlank { "—" },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            diffNameOnly(record.difficulty),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.width(4.dp))
        Text("›", fontSize = 14.sp, color = Ink.copy(alpha = 0.6f))
    }
}
