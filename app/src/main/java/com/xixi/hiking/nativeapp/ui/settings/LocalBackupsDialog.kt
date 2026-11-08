package com.xixi.hiking.nativeapp.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.data.LocalBackup
import com.xixi.hiking.nativeapp.ui.GlassConfirm
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xixi.hiking.nativeapp.ui.theme.Ink

/**
 * 本地备份管理 —— 对齐 Web 版 `showManageBackupsModal`。
 *
 * 这是「数据丢了还能救」的最后一道。之前原生版只有一个「导出备份」——
 * 导出到手机哪个目录、还在不在、什么时候备的，全都要用户自己去文件管理器找。
 */
@Composable
fun LocalBackupsDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    /** 立即备份一份（由调用方提供 JSON 生成逻辑，避免这里依赖数据库） */
    onBackupNow: () -> Unit,
    /** 恢复某份备份（本地读文件后走导入流程） */
    onRestore: (File) -> Unit,
    /** 恢复成功后刷新页面 */
    refreshKey: Int = 0
) {
    val ctx = LocalContext.current
    val isDark = LocalIsDark.current
    var tick by remember { mutableStateOf(0) }
    var confirmRestore by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }

    // ★列表项异步算好再渲染。
    //   原来在 composable 里直接 LocalBackup.describe(f) —— 那个函数要 readText() 整个备份
    //   （可能几 MB）来数条数，等于在主线程做文件 IO，备份多了列表会明显卡。
    var items by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(tick, refreshKey) {
        loading = true
        // ★必须 try：LaunchedEffect 里抛出的异常没人接（不在 rememberCoroutineScope 的
        //   CoroutineExceptionHandler 管辖内）⇒ 目录读失败就是一次闪退。
        items = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                LocalBackup.list(ctx).map { f ->
                    val (date, size, count) = LocalBackup.describe(f)
                    BackupItem(f, date, size, count, f.name.startsWith("manual"))
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("hiking", "读取本地备份失败", e)
            emptyList()
        }
        loading = false
    }

    GlassOverlay(hazeState = hazeState, onDismiss = onDismiss, maxWidthFraction = 0.92f, corner = 26) {
        Column(Modifier.fillMaxWidth()) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = Ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "本地备份", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "每天自动存一份在本机（保留最近 7 份）。换手机、误删、手滑抹掉，都能从这儿捞回来",
                fontSize = 12.sp, lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            // 立即备份
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassChip(corner = Radii.BUTTON, strong = true)
                    .safeClickable { onBackupNow(); tick++ }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "现在备份一份", fontSize = 15.sp,
                    color = Ink, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "正在读取备份…", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (items.isEmpty()) {
                // ★空态要说清楚「为什么空」和「怎么办」，不要只丢一个图标
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CloudOff, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "还没有任何备份",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "明天起会自动生成；也可以点上面的按钮立刻备一份",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                // ★2026-11-05 崩溃点扫描：`items.first()` 依赖"这个 else 就是非空分支"。
                //   但这个列表是异步刷新的（备份完会重新扫目录），换成 firstOrNull 之后
                //   哪怕刷新瞬间列表变空，也只是少显示"最近一份"，不会崩在文案拼接里。
                val newest = items.firstOrNull()
                Text(
                    "共 ${items.size} 份" +
                        (newest?.let { " · 最近一份 ${agoText(it.file.lastModified())}" } ?: ""),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items.forEach { it ->
                        val f = it.file
                        val manual = it.manual
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.06f)
                                    else Color.White
                                )
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFEDF0F7),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(start = 11.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        it.date, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (manual) {
                                        Spacer(Modifier.width(5.dp))
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (com.xixi.hiking.nativeapp.ui.theme.LocalIsDark.current)
                                                        Color.White.copy(alpha = 0.10f)
                                                    else Color(0xFF1F2937).copy(alpha = 0.08f)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                "手动", fontSize = 10.sp, color = Ink,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    "${it.count} · ${it.size}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 恢复
                            Box(
                                modifier = Modifier
                                    .liquidGlassChip(corner = Radii.INPUT)
                                    .safeClickable { confirmRestore = f }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "恢复", fontSize = 11.sp,
                                    color = if (isDark) Color(0xFF6EE7A8) else Color(0xFF15803D),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .liquidGlassChip(corner = Radii.INPUT)
                                    .safeClickable { confirmDelete = f }
                                    .padding(7.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete, "删除",
                                    tint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmRestore?.let { f ->
        GlassConfirm(
            hazeState = hazeState,
            title = "用这份备份覆盖当前数据？",
            message = "备份时间：${SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(f.lastModified()))}\n\n" +
                "现在已有的记录会被同名条目覆盖；备份里没有的条目会保留。建议先备份一份保底",
            confirmText = "恢复",
            onConfirm = { confirmRestore = null; onRestore(f); tick++ },
            onCancel = { confirmRestore = null }
        )
    }

    confirmDelete?.let { f ->
        GlassConfirm(
            hazeState = hazeState,
            title = "删除这份备份？",
            message = "删了就没法从这一份恢复了，其他备份不受影响",
            confirmText = "删除",
            danger = true,
            onConfirm = { f.delete(); confirmDelete = null; tick++ },
            onCancel = { confirmDelete = null }
        )
    }
}

/** 「3 小时前」这种相对时间，比一个绝对时间戳好读 */
private fun agoText(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val min = diff / 60000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "${min} 分钟前"
        min < 60 * 24 -> "${min / 60} 小时前"
        min < 60 * 24 * 30 -> "${min / (60 * 24)} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
    }
}

/** 列表里一份备份已经算好的展示信息（避免在 composable 里做文件 IO） */
private data class BackupItem(
    val file: File,
    val date: String,
    val size: String,
    val count: String,
    val manual: Boolean
)
