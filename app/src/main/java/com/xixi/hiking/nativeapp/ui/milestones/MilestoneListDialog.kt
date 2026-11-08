package com.xixi.hiking.nativeapp.ui.milestones

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.MILESTONES
import com.xixi.hiking.nativeapp.data.MilestoneStats
import com.xixi.hiking.nativeapp.data.Prefs
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.data.milestoneDone
import com.xixi.hiking.nativeapp.data.milestoneProgressText
import com.xixi.hiking.nativeapp.data.milestoneStats
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import dev.chrisbanes.haze.HazeState
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark

private val Amber = Color(0xFFF59E0B)

/**
 * ★「我的里程碑」一览（1:1 对齐 Web 版 showMilestoneList）：
 *   🏆 标题 + 已达成 N / 17 项 + 17 档逐条（达成=琥珀色奖杯，未达成=空心圈 + 当前进度）
 *   收集类额外把清单列出来并给已达成项打勾（还缺哪几个一眼看到）
 *   打开即视为「已查看」→ 记 Prefs，概览入口的红点随之熄灭
 */
@Composable
fun MilestoneListDialog(
    records: List<RecordEntity>,
    hazeState: HazeState,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val stats: MilestoneStats = remember(records) { milestoneStats(records) }
    val done: List<String> = remember(records) { milestoneDone(records) }

    // ★一进来就记「已查看」：红点只提示「有新的达成没看过」
    LaunchedEffect(done) {
        // 异步副作用内必须自备 try —— 存储写失败不能把整个弹窗带崩
        try {
            Prefs.setStringSet(ctx, Prefs.KEY_MS_SEEN, done.toSet())
        } catch (e: Throwable) {
            android.util.Log.w("hiking", "里程碑已读标记写入失败", e)
        }
    }

    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.92f,
        corner = 22,
        maxHeightFraction = 0.80f     // 17 档内容多，限高让上下都留空当
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ★2026-11-03 用户：「给里程碑里加上颜色吧，奖杯不能没有颜色」
                Icon(
                    Icons.Filled.EmojiEvents, null,
                    tint = if (LocalIsDark.current) Color(0xFFFBBF24) else Color(0xFFD97706),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "我的里程碑",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "已达成 ${done.size} / ${MILESTONES.size} 项",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.fillMaxWidth()) {
                items(MILESTONES, key = { it.id }) { ms ->
                    val has = done.contains(ms.id)
                    MilestoneRow(
                        title = ms.title,
                        desc = ms.desc,
                        progress = milestoneProgressText(ms, stats, has),
                        has = has,
                        // 收集类：列清单 + 打勾
                        checklist = if (ms.type == "collect") {
                            val hit = stats.collections[ms.collectKey] ?: emptySet()
                            ms.items.map { group ->
                                val label = group.firstOrNull() ?: ""
                                label to hit.contains(label)
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(
    title: String,
    desc: String,
    progress: String,
    has: Boolean,
    checklist: List<Pair<String, Boolean>>?
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (has) Icons.Filled.EmojiEvents else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                // 达成的奖杯必须是**琥珀色**（金色的杯子），未达成是灰圈
                tint = if (has) {
                    if (LocalIsDark.current) Color(0xFFFBBF24) else Color(0xFFD97706)
                } else {
                    muted.copy(alpha = 0.55f)
                },
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                // ★进度只占「标题行」右侧（Web 版教训：与描述同列竞争宽度会把描述挤成两行）
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (has) MaterialTheme.colorScheme.onBackground else muted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        progress,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (has) Ink else muted.copy(alpha = 0.8f),
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    desc,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = muted
                )
                if (checklist != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        checklist.joinToString(" · ") { (label, ok) -> if (ok) "$label ✓" else label },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = if (has) Ink.copy(alpha = 0.9f) else muted.copy(alpha = 0.85f)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                // 每行之间一条极淡分隔（最后一行也有，视觉上像卡片内边距，不刺眼）
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.07f))
        )
    }
}

/**
 * ★概览页的「我的里程碑」入口卡（1:1 对齐 Web 版 milestoneEntry）：
 *   琥珀 10% 透明底 + 琥珀 25% 描边 + 奖杯 + 标题 + 「N / 17」+ 有未看过的新成就时亮红点
 *   —— 不是渐变、不是白卡，就是淡淡一层琥珀，放在统计卡下面最后一行。
 */
@Composable
fun MilestoneEntryCard(
    records: List<RecordEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val done = remember(records) { milestoneDone(records) }
    val seen = remember(records) { Prefs.getStringSet(ctx, Prefs.KEY_MS_SEEN) }
    val unseen = done.any { !seen.contains(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // ★2026-11-03 调淡：0.10 的琥珀铺满一整条，在白卡里像块实心橙砖。
            //   0.07 + 0.18 描边 —— 还是"成就"的暖调，但退到背景层，不抢主数据。
            .clip(RoundedCornerShape(14.dp))
            .background(Amber.copy(alpha = 0.07f))
            .border(1.dp, Amber.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .safeClickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // ★★2026-11-02 对齐 Web 版：这张卡是**琥珀色系**（图标/标题/计数都是 #d97706），
        //   之前用纯黑，和琥珀底放在一起看不出这是"里程碑/成就"语义。
        //   深色模式提到 amber-500，否则在深底上太暗。
        val msAccent = if (LocalIsDark.current) Color(0xFFF59E0B) else Color(0xFFD97706)
        Icon(Icons.Filled.EmojiEvents, null, tint = msAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "我的里程碑",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = msAccent,
            modifier = Modifier.weight(1f)
        )
        if (unseen) {
            // ★新达成红点（不是弹窗打断，只在入口上点一下）
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFEF4444))
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            "${done.size} / ${MILESTONES.size}",
            fontSize = 13.sp, color = msAccent, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(3.dp))
        Icon(
            Icons.Filled.ChevronRight, null,
            tint = msAccent, modifier = Modifier.size(18.dp)
        )
    }
}
