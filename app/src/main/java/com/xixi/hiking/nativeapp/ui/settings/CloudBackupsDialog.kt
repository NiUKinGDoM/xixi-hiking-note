package com.xixi.hiking.nativeapp.ui.settings

import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.xixi.hiking.nativeapp.data.WebDavClient
import com.xixi.hiking.nativeapp.ui.GlassConfirm
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import com.xixi.hiking.nativeapp.ui.theme.Radii
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xixi.hiking.nativeapp.ui.theme.DangerRed

/**
 * ★管理云端备份（对齐 Web 版 showManageBackupsModal）
 *
 * Web 版的做法：PROPFIND 列 `xixi-hiking/` 下的文件 → 按时间新→旧 → 每行「下载 / 删除」。
 * 原生版之前没有这层：上传固定写 backup.json、下载固定读 backup.json，
 * 用户在网盘里手动留的多份备份根本看不到。
 *
 * 文件名规则两边已经统一成 `xixi-backup-YYYYMMDD_HHmmss.json`（见 WebDavClient.backupName）。
 */
@Composable
fun CloudBackupsDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    /** 下载并恢复成功后回调（刷新数据） */
    onRestored: () -> Unit
) {
    val ctx = LocalContext.current
    val isDark = LocalIsDark.current
    var tick by remember { mutableStateOf(0) }
    var files by remember { mutableStateOf<List<WebDavClient.CloudFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<WebDavClient.CloudFile?>(null) }
    var confirmRestore by remember { mutableStateOf<WebDavClient.CloudFile?>(null) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun reload() {
        loading = true
        scope.launch {
            val (list, msg) = withContext(Dispatchers.IO) { WebDavClient.listBackups(ctx) }
            files = list
            error = if (list.isEmpty()) msg else null
            loading = false
        }
    }

    LaunchedEffect(tick) { reload() }

    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.90f,
        corner = 24,
        maxHeightFraction = 0.80f
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Restore, null, tint = Ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "管理云端备份", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "网盘目录里的备份文件，新版在前。下载会合并进本机",
                fontSize = 12.sp, lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("读取中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                files.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CloudOff, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error ?: "云端还没有备份",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .liquidGlassChip(corner = Radii.INPUT, flat = true)
                            .safeClickable { tick++ }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) { Text("重新读取", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold) }
                }

                else -> Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    files.forEach { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isDark) Color.White.copy(alpha = 0.04f)
                                    else Color.White.copy(alpha = 0.34f)
                                )
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f)
                                    else Color(0xFF0F172A).copy(alpha = 0.06f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    f.name, fontSize = 12.sp, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    buildString {
                                        if (f.lastModified > 0) {
                                            append(
                                                SimpleDateFormat("MM-dd HH:mm", Locale.US)
                                                    .format(Date(f.lastModified))
                                            )
                                        }
                                        if (f.size > 0) {
                                            if (isNotEmpty()) append(" · ")
                                            append("${f.size / 1024} KB")
                                        }
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                Modifier
                                    .liquidGlassChip(corner = 10, flat = true)
                                    .safeClickable(enabled = !busy) { confirmRestore = f }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text("恢复", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold) }
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .liquidGlassChip(corner = 10, flat = true, danger = true)
                                    .safeClickable(enabled = !busy) { confirmDelete = f },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Delete, "删除云端备份",
                                    tint = DangerRed, modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            toast?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    confirmRestore?.let { f ->
        GlassConfirm(
            hazeState = hazeState,
            title = "恢复这份备份？",
            message = "「${f.name}」会下载并合并进本机：同 id 取较新的那条，本机独有的保留。",
            confirmText = "下载并恢复",
            onConfirm = {
                val target = f
                confirmRestore = null
                busy = true
                scope.launch {
                    // ★2026-11-05 云端文件现在可能是 zip（Web 版 / 新版原生），按字节下载
                    val (bytes, msg) = withContext(Dispatchers.IO) { WebDavClient.downloadBytes(ctx, target.name) }
                    if (bytes == null) {
                        busy = false
                        toast = "下载失败：$msg"
                        return@launch
                    }
                    val (ok, detail) = withContext(Dispatchers.IO) { importJson(ctx, bytes) }
                    busy = false
                    toast = if (ok) "已恢复：$detail" else "恢复失败：$detail"
                    if (ok) { onRestored(); tick++ }
                }
            },
            onCancel = { confirmRestore = null }
        )
    }

    confirmDelete?.let { f ->
        GlassConfirm(
            hazeState = hazeState,
            title = "删除这份云端备份？",
            message = "「${f.name}」会从网盘删除，本机数据不受影响。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                val target = f
                confirmDelete = null
                busy = true
                scope.launch {
                    val (ok, msg) = withContext(Dispatchers.IO) { WebDavClient.deleteFile(ctx, target.name) }
                    busy = false
                    toast = if (ok) "已删除" else "删除失败：$msg"
                    tick++
                }
            },
            onCancel = { confirmDelete = null }
        )
    }
}
