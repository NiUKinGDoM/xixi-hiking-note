package com.xixi.hiking.nativeapp.ui.settings

// ══════════════════════════════════════════════════════════════
//  ★2026-11-11 从 SettingsScreen.kt 拆出来（第 1 步：纯搬移，未改逻辑）
//
//  SettingRow（分组里的一行）、InfoTile / StatInline（关于页信息块）、
//  AboutLink / ExportOpt（关于页链接、导出勾选行）、ioScope（带异常兜底的
//  IO 作用域）、androidxClip 等 modifier 扩展。
//
//  ⚠️ 这里一律保持**无状态**：只接值 + 回调，不持有 remember。
//     状态留在 SettingsScreen —— 否则切页再回来状态会被重建（填一半的配置全丢）。
// ══════════════════════════════════════════════════════════════

import com.xixi.hiking.nativeapp.ui.safeClickable
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.BuildConfig
import com.xixi.hiking.nativeapp.data.AppDatabase
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.glassPanel
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import com.xixi.hiking.nativeapp.ui.theme.TextMuted
import com.xixi.hiking.nativeapp.ui.theme.TextPrimary
import com.xixi.hiking.nativeapp.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip


/**
 * ★带异常兜底的 IO 作用域 —— 2026-11-01
 *
 * 原来 6 处都是裸 `CoroutineScope(Dispatchers.IO)`：导入损坏的 JSON、网盘 401、
 * 磁盘写满……任何一处抛错都会变成「未捕获异常」，直接把 App 干掉（闪退）。
 * 这里统一挂 CoroutineExceptionHandler，出错只记日志，不崩。
 */
/** 打开外部浏览器（Web 关于页那两个链接：GitHub / 官网） */
internal fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 关于页底部的轻量链接（Web `.about-link`：图标 + 文字，无底色） */
@Composable
internal fun AboutLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.safeClickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 导出弹窗里的勾选行（Web `.chk-row`）：方框 + 标题 + 一句灰字说明。
 * 只用于导出备份的两级网盘配置选择。
 */
@Composable
internal fun ExportOpt(
    checked: Boolean,
    title: String,
    sub: String,
    onChange: (Boolean) -> Unit
) {
    val isDark = LocalIsDark.current
    val indigo = com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .safeClickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) indigo else Color.Transparent)
                .border(
                    1.5.dp,
                    if (checked) indigo
                    else if (isDark) Color.White.copy(alpha = 0.40f)
                    else Color(0xFF0F172A).copy(alpha = 0.35f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                sub, fontSize = 11.sp, lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

internal fun ioScope(): CoroutineScope = CoroutineScope(
    Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        android.util.Log.e("hiking", "设置页异步任务失败，已拦下", e)
    }
)
@Composable
internal fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    // ★2026-11-03 去掉分组白底框：每组的行直接铺在页面上，
    //   靠「分组标题 + 行」分组，不再用一层大玻璃把内容框住。
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

/**
 * 概览数字（横排版）：`记录 18`。
 * 标签用次要色、数字用主色，横向基线对齐 —— 一条里塞两个指标也不挤。
 */
@Composable
internal fun StatInline(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    /** 最后一行不画分隔线 —— 否则线下面紧跟「抹掉」按钮，会像多出一条断头线 */
    showDivider: Boolean = true
) {
    val dark = LocalIsDark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // ★行分隔线：同组内多行不加线会糊成一片
            .drawBehind {
                if (!showDivider) return@drawBehind
                val y = size.height - 0.5f
                drawLine(
                    // ★2026-11-02 加深一档：0xFFEDEFF5 在淡紫白背景上几乎看不见，
                    //   行与行看起来是"糊在一起"而不是"分开的列表"
                    color = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFFE2E6EF),
                    start = androidx.compose.ui.geometry.Offset(58.dp.toPx(), y),
                    end = androidx.compose.ui.geometry.Offset(size.width - 12.dp.toPx(), y),
                    strokeWidth = 1f
                )
            }
            .clip(RoundedCornerShape(Radii.SUB.dp))
            .safeClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                // ★★图标底改成中性灰（2026-11-02）：
                //   原来是 AccentIndigo 10% —— 全站文字已经黑白，这里还留着淡紫，
                //   是页面上唯一的彩色残留，而且这些图标（下载/上传/存储/照片/历史）
                //   本来就没有语义色，用强调色纯属装饰。中性灰更整齐。
                .background(
                    if (dark) Color.White.copy(alpha = 0.07f)
                    else Color(0xFF1F2937).copy(alpha = 0.06f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 导入导出 ────────────────────────────────────────────────

suspend fun buildExportJsonPublic(context: Context): String = buildExportJson(context)

private suspend fun buildExportJson(context: Context): String {
    val db = AppDatabaseRef.get(context)
    val root = JSONObject()
    // ★与 Web 版 buildFullBackupPayload 对齐的顶层标记：
    //   有了 app 标记，导入时才能明确判断"这是不是本应用的文件"
    root.put("app", "XiXiHiking")
    root.put("appName", "徒步小记")
    root.put("version", BuildConfig.VERSION_NAME)
    root.put("exportedAt", System.currentTimeMillis())
    val recs = JSONArray()
    db.recordDao().observeAllOnce().forEach { r ->
            // ★照片：原生版存文件，导出时转成 dataURL —— 与 Web 版备份格式一致，可互相导入
            val photos = JSONArray()
            db.photoDao().byRecord(r.id).forEach { ph ->
                val b64 = com.xixi.hiking.nativeapp.data.PhotoStore.toBase64(context, ph.fileName)
                if (b64 != null) {
                    photos.put(JSONObject().apply {
                        put("id", ph.id)
                        put("data", "data:image/jpeg;base64,$b64")
                        put("w", ph.width)
                        put("h", ph.height)
                    })
                }
            }
            recs.put(JSONObject().apply {
                put("id", r.id); put("name", r.name); put("distance", r.distance)
                put("duration", r.duration); put("elevation", r.elevation); put("difficulty", r.difficulty)
                put("mood", r.mood); put("weather", r.weather); put("companions", r.companions)
                put("notes", r.notes); put("createdAt", r.createdAt); put("updatedAt", r.updatedAt)
                put("photos", photos)
            })
    }
    root.put("records", recs)
    val plans = JSONArray()
    db.plannedTripDao().observeAllOnce().forEach { p ->
        plans.put(JSONObject().apply {
            put("id", p.id); put("name", p.name); put("plannedDate", p.plannedDate)
            put("location", p.location); put("note", p.note)
            put("difficulty", p.difficulty); put("createdAt", p.createdAt)
            // ★elevation 必须一起导出：漏了就会出现「导出→导入」后计划海拔全变 0
            put("elevation", p.elevation)
        })
    }
    root.put("plannedTrips", plans)

    // ★★2026-11-02：备份里带上网盘账号 —— 换机/重装后导入即可恢复，
    //   不用再翻坚果云找那个「应用密码」。Web 版备份也是这么做的。
    val davCfg = com.xixi.hiking.nativeapp.data.WebDavClient.load(context)
    if (davCfg.configured) {
        root.put("webdav", JSONObject().apply {
            put("url", davCfg.url)
            put("user", davCfg.user)
            put("pass", davCfg.pass)
        })
    }
    return root.toString(2)
}

/**
 * 导入备份 —— 与 Web 版备份格式双向兼容。
 *
 * ★ 2026-11-02 修了三个会导致"数据看起来导进来了、其实是空的"的问题：
 *
 * 1. **计划的日期字段名不同**：Web 版计划对象里叫 `createdAt`，原生版叫 `plannedDate`。
 *    只读 `plannedDate` 的话，Web 版备份导进来日历全是空的。
 * 2. **照片的 dataURL 字段名/前缀不统一**：可能叫 data / url / base64 / blob，
 *    也可能不带 `data:image/jpeg;base64,` 前缀。老代码只认 `data` + 必须带前缀，
 *    结果整批照片被静默跳过。
 * 3. **计划丢了 elevation**：字段是这轮才加的，导入侧忘了读。
 *
 * 另外：id 为空的记录/计划会被跳过（脏数据进了库，主键是空的会互相覆盖）。
 */
/** 仅测试用：暴露 importJson 供 JVM 单测校验格式兼容性 */
internal suspend fun importJsonForTest(context: Context, text: String): Pair<Boolean, String> =
    importJson(context, text.toByteArray(Charsets.UTF_8))

/**
 * 导入入口 —— 收字节而不是字符串：zip 完整备份是二进制，当文本读会直接损坏。
 *
 * 自动识别三种来源：
 * - **zip**：Web 版「导出完整备份压缩包」，唯一能带照片的格式
 * - **html**：Web 版「导出纯数据备份」（JSON 内嵌在 script 里，不含照片）
 * - **json**：WebDAV 云端备份 / 原生版自己导出的
 */
internal suspend fun importJson(context: Context, bytes: ByteArray): Pair<Boolean, String> {
    return try {
        // ★保护：超大备份（几百 MB）一次性读进内存会 OOM，先挡一下并给明确提示
        if (bytes.size > 300 * 1024 * 1024) {
            return false to "备份文件超过 300MB，太大了。可以先在网页版导出「纯数据备份」只搬记录"
        }

        // 解析交给 BackupParser（纯函数，可单测），这里只负责"把结果写进库 + 把照片落盘"
        val parsed = if (com.xixi.hiking.nativeapp.data.BackupParser.looksLikeZip(bytes)) {
            com.xixi.hiking.nativeapp.data.BackupParser.parseZip(bytes)
        } else {
            com.xixi.hiking.nativeapp.data.BackupParser.parse(String(bytes, Charsets.UTF_8))
        }
        val db = AppDatabaseRef.get(context)

        // ★★2026-11-02 第二次修「导入后账号密码还是空的」：
        //   上一版只对**非 zip** 的备份读配置，而用户导的正是 Web 版的 zip 完整备份；
        //   而且只认 webdav 键，Web 版的键叫 syncConfig。
        //   现在配置由解析器统一带出来（parsed.dav），zip / json / html 三条路都覆盖。
        var davRestored = false
        runCatching {
            parsed.dav?.let { cfg ->
                // ★合并而不是覆盖：云端自动上传的备份不带密码（Web 版默认就不带），
                //   直接覆盖会把本机已经存好的应用密码抹成空。
                val cur = com.xixi.hiking.nativeapp.data.WebDavClient.load(context)
                val merged =
                    if (cfg.pass.isBlank() && cur.pass.isNotBlank() && cur.user == cfg.user)
                        cfg.copy(pass = cur.pass) else cfg
                com.xixi.hiking.nativeapp.data.WebDavClient.save(context, merged)
                davRestored = true
            }
        }

        if (parsed.records.isNotEmpty()) {
            db.recordDao().upsertAll(parsed.records.map { it.entity })
        }
        parsed.records.forEach { pr ->
            if (pr.photos.isEmpty()) return@forEach
            val rows = pr.photos.mapIndexed { idx, ph ->
                // ★文件名必须消毒：ph.id 来自备份 JSON（外部输入），
                //   原来是 `"${ph.id}.jpg"` 直接拼路径 —— 传 `../../databases/x` 就能写到沙盒别处
                val name = com.xixi.hiking.nativeapp.data.PhotoStore.safeName("${ph.id}.jpg")
                com.xixi.hiking.nativeapp.data.PhotoStore.file(context, name).writeBytes(ph.bytes)
                com.xixi.hiking.nativeapp.data.PhotoEntity(
                    id = ph.id, recordId = pr.entity.id, fileName = name,
                    width = ph.w, height = ph.h,
                    createdAt = System.currentTimeMillis() + idx
                )
            }
            db.photoDao().insertAll(rows)
        }
        if (parsed.plans.isNotEmpty()) {
            db.plannedTripDao().upsertAll(parsed.plans)
        }

        val detail = buildString {
            append("${parsed.records.size} 条记录")
            if (parsed.photoCount > 0) append("、${parsed.photoCount} 张照片")
            append("、${parsed.plans.size} 个计划")
            if (parsed.skipped > 0) append("（跳过 ${parsed.skipped} 条缺 id 的脏数据）")
            if (parsed.badPhotos > 0) append("（${parsed.badPhotos} 张照片数据损坏）")
            if (davRestored) {
                append("、网盘配置")
                if (parsed.dav?.pass.isNullOrBlank()) append("密码没随备份带过来，去设置页补填一次")
            }
        }
        true to detail
    } catch (e: com.xixi.hiking.nativeapp.data.BackupParser.WrongAppException) {
        false to e.message.orEmpty()
    } catch (e: Throwable) {
        // ★接 Throwable 而不是 Exception：嵌套极深的坏 JSON 会抛 StackOverflowError，
        //   超大照片会让 base64 解码 OOM —— 这两个都是 Error，只 catch Exception 接不住 ⇒ 闪退
        false to (e.message ?: "格式不对")
    }
}

/** 全局单例持有 DB（避免每次导出重建） */
object AppDatabaseRef {
    private var db: AppDatabase? = null
    fun get(context: Context): AppDatabase {
        db?.let { return it }
        val d = com.xixi.hiking.nativeapp.data.buildDatabase(context)
        db = d
        return d
    }
    fun set(database: AppDatabase) { db = database }
}

internal fun Modifier.androidxClip(): Modifier =
    this.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))

internal fun Modifier.androidxBg(): Modifier = this
    .background(Color(0x14DC2626))
    .border(
        1.dp, Color(0xFFDC2626).copy(alpha = 0.35f),
        androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    )

@androidx.compose.runtime.Composable
internal fun Modifier.androidxChip(): Modifier = this.liquidGlassChip(corner = 12, flat = true)
