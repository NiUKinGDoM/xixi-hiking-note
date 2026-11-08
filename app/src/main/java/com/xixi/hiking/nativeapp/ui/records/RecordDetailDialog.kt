package com.xixi.hiking.nativeapp.ui.records

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.theme.Radii
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.PhotoEntity
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.photos.PhotoLightbox
import com.xixi.hiking.nativeapp.ui.photos.PhotoStrip
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import com.xixi.hiking.nativeapp.ui.theme.Ink

/**
 * ★1:1 对齐现有 App 的详情弹窗：
 *   ① 标题行：▲ + 「记录详情」+ 右上 ✕
 *   ② 山名（大字）+ 日期（右侧小字）
 *   ③ 三个彩色胶囊标签：难度（绿）/ 心情（紫）/ 天气（蓝）
 *   ④ 指标网格 2 列，**无卡片背景**，只有小标签 + 大数值 + 极淡分隔线
 *   ⑤ 小日记（标题 + 正文）
 *   ⑥ 底部全宽「编辑这条记录」（白底描边，不是紫底）
 */
@Composable
fun RecordDetailDialog(
    record: RecordEntity,
    photos: List<PhotoEntity>,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var confirmDelete by remember { mutableStateOf(false) }
    var lightboxAt by remember { mutableStateOf<Int?>(null) }
    var showShare by remember { mutableStateOf(false) }

    GlassOverlay(
        bottomClose = false,
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.88f,
        corner = 26
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ① 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Terrain, null, tint = Ink, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "记录详情", fontSize = 18.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ② 山名 + 日期
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.name, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    record.createdAt.replace("T", " ").take(16),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // ③ 彩色胶囊标签
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Pill("难度  ${diffLabel(record.difficulty)}", Color(0xFF10B981))
                if (record.mood.isNotBlank()) Pill("心情  ${record.mood}", Color(0xFFA855F7))
                if (record.weather.isNotBlank()) Pill("天气  ${record.weather}", Color(0xFF3B82F6))
            }

            Spacer(Modifier.height(14.dp))

            // ④ 指标网格（无卡片背景）
            val metrics = buildList {
                add("最高海拔" to if (record.elevation > 0) "${record.elevation}m" else "—")
                add("里程" to if (record.distance > 0) "${fmt1(record.distance)}km" else "—")
                add("用时" to if (record.duration > 0) fmtDur(record.duration) else "—")
                if (record.companions.isNotBlank()) add("同行人" to record.companions)
            }
            metrics.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { (label, value) ->
                        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                            Text(
                                label, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                )
            }

            // ⑤ 小日记
            if (record.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ★原来这里放的是一个「✎」字符 —— 11sp 渲染出来就是一根斜杠，
                    //   像个排版事故。换成真图标。
                    Icon(
                        Icons.AutoMirrored.Filled.Notes, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "小日记", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    record.notes, fontSize = 13.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 照片
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                PhotoStrip(photos = photos, onOpen = { lightboxAt = it })
            }

            Spacer(Modifier.height(14.dp))

            // ⑥ 底部按钮：**四个，两排两列**（★2026-11-05 用户要求）
            //   原来是「编辑这条记录」整行 + 分享/删除一排 —— 没有关闭，
            //   看完想退出只能点空白处（很多人不知道能这样关）。
            //
            // ★2026-11-07 用户：「把删除和关闭键互换一下位置」
            //   现在：编辑 / 关闭 一排，分享 / 删除 一排。
            //   删除（不可逆、又带二次确认）挪到右下角最偏的位置，离拇指最远；
            //   关闭升到第一排，看完随手就能退。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .liquidGlassChip(corner = Radii.BUTTON, strong = true)
                        .safeClickable(onClick = onEdit)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, null, tint = Ink, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("编辑", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .liquidGlassChip(corner = Radii.BUTTON)
                        .safeClickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Close, null, tint = Ink, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("关闭", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .liquidGlassChip(corner = Radii.BUTTON)
                        .safeClickable { showShare = true }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.IosShare, null, tint = Ink, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("分享", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // 破坏性操作保持全站统一的危险语义（淡红底 + 红字）
                        .liquidGlassChip(corner = Radii.BUTTON, danger = true)
                        .safeClickable { confirmDelete = true }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DeleteOutline, null,
                            tint = if (LocalIsDark.current) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
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

    if (showShare) {
        com.xixi.hiking.nativeapp.ui.share.ShareCardDialog(
            record = record, photos = photos, hazeState = hazeState,
            onDismiss = { showShare = false }
        )
    }

    lightboxAt?.let { at ->
        PhotoLightbox(
            photos = photos,
            startIndex = at,
            onDismiss = { lightboxAt = null },
            hazeState = hazeState,
            title = record.name
            // ★2026-11-04 对齐 Web 的【预览态】：记录详情里看照片只有「保存」，**没有删除**。
            //   Web 的 openPhotoLightbox(recordId, pid) 不传 editing ⇒ lb-del 一直是 display:none，
            //   删照片属于编辑态（进「编辑」后点照片才有添加/删除）。
        )
    }

    if (confirmDelete) {
        com.xixi.hiking.nativeapp.ui.GlassConfirm(
            hazeState = hazeState,
            title = "删除这条记录？",
            message = "「${record.name}」将被永久删除，不能恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onCancel = { confirmDelete = false }
        )
    }
}

/** 彩色胶囊标签（淡色底 + 同色字，对齐截图里的「难度 / 心情 / 天气」） */
@Composable
private fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, color = Ink, fontWeight = FontWeight.Medium)
    }
}

private fun fmt1(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

private fun fmtDur(min: Int): String {
    val h = min / 60
    val m = min % 60
    return if (h > 0) "${h}h${if (m > 0) "${m}m" else ""}" else "${m}m"
}
