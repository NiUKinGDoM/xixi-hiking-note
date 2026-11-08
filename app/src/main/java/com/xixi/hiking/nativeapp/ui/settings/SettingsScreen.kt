package com.xixi.hiking.nativeapp.ui.settings

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
 * 设置：数据导出/导入 + 关于。
 * 导出格式与 Web 版备份的 records[] 结构一致 —— 以后能互相导入。
 */

/**
 * ★带异常兜底的 IO 作用域 —— 2026-11-01
 *
 * 原来 6 处都是裸 `CoroutineScope(Dispatchers.IO)`：导入损坏的 JSON、网盘 401、
 * 磁盘写满……任何一处抛错都会变成「未捕获异常」，直接把 App 干掉（闪退）。
 * 这里统一挂 CoroutineExceptionHandler，出错只记日志，不崩。
 */
/** 打开外部浏览器（Web 关于页那两个链接：GitHub / 官网） */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 关于页底部的轻量链接（Web `.about-link`：图标 + 文字，无底色） */
@Composable
private fun AboutLink(
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
private fun ExportOpt(
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

private fun ioScope(): CoroutineScope = CoroutineScope(
    Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        android.util.Log.e("hiking", "设置页异步任务失败，已拦下", e)
    }
)

@Composable
fun SettingsScreen(
    recordCount: Int,
    planCount: Int,
    /** 最早一条记录的日期（yyyy-MM-dd），用来算「徒步年资」；没有记录传 null */
    firstRecordAt: String? = null,
    onImported: () -> Unit,
    onThemeChange: (String) -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    /** 顶栏是浮层，页面要给内容让出这么高 */
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    /** 从「迁移引导」跳过来时，进页面直接打开导入弹窗 */
    autoOpenImport: Boolean = false,
    onAutoOpenConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    var toast by remember { mutableStateOf<String?>(null) }
    var showSync by remember { mutableStateOf(false) }
    var showCloud by remember { mutableStateOf(false) }
    var showPhotoUsage by remember { mutableStateOf(false) }
    // 照片占用的那一行说明（Web：照片 N 张 · 12.3 MB / 暂无照片 / 统计失败）
    var photoUsageDesc by remember { mutableStateOf("统计中…") }
    var photoUsageTick by remember { mutableStateOf(0) }
    // ★2026-11-04 每页条数（对齐 Web 的 RemoteConfig pageSize：5~50）
    var showPageSize by remember { mutableStateOf(false) }
    var pageSizePref by remember {
        mutableStateOf(
            com.xixi.hiking.nativeapp.data.Prefs
                .getInt(context, com.xixi.hiking.nativeapp.data.Prefs.KEY_PAGE_SIZE, 10)
                .coerceIn(5, 50)
        )
    }
    androidx.compose.runtime.LaunchedEffect(photoUsageTick) {
        photoUsageDesc = withContext(Dispatchers.IO) {
            runCatching { com.xixi.hiking.nativeapp.data.PhotoMaintenance.scan(context) }
                .getOrNull()
                ?.let { u ->
                    when {
                        u.dbCount == 0 -> "暂无照片"
                        else -> buildString {
                            append("照片 ${u.dbCount} 张 · ")
                            if (u.bytes >= 1048576) {
                                append(
                                    String.format(java.util.Locale.US, "%.1f", u.bytes / 1048576.0) + " MB"
                                )
                            } else {
                                append("${(u.bytes + 1023) / 1024} KB")
                            }
                            if (u.orphans.isNotEmpty()) {
                                append("（含孤立 ${u.orphans.size} 张，点开可优化）")
                            }
                        }
                    }
                } ?: "统计失败"
        }
    }
    var showChangelog by remember { mutableStateOf(false) }
    var showDataInfo by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<com.xixi.hiking.nativeapp.data.Updater.Release?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }
    // ★2026-11-05 同步状态：写 Prefs 不是 Compose state，改完要让这一行重新读
    var syncTick by remember { mutableStateOf(0) }
    var showSyncStatus by remember { mutableStateOf(false) }
    // ★抹掉所有足迹：0=关 1=第一次确认 2=最后确认
    var wipeStep by remember { mutableStateOf(0) }
    // ★★2026-11-02：原来这里是 remember { load() } —— 一次性读死。
    //   导入/保存/解绑之后 UI 还显示"未配置"，用户以为没导进来（其实是存进去了）。
    //   改成可变状态，凡是会改配置的地方都 refreshDav()。
    var dav by remember { mutableStateOf(com.xixi.hiking.nativeapp.data.WebDavClient.load(context)) }
    fun refreshDav() { dav = com.xixi.hiking.nativeapp.data.WebDavClient.load(context) }
    var autoSync by remember {
        mutableStateOf(com.xixi.hiking.nativeapp.data.Prefs.getBool(context, com.xixi.hiking.nativeapp.data.Prefs.KEY_AUTO_SYNC, false))
    }
    var themeMode by remember {
        mutableStateOf(com.xixi.hiking.nativeapp.data.Prefs.getString(context, com.xixi.hiking.nativeapp.data.Prefs.KEY_THEME, "auto"))
    }

    // ══ 导出（★★2026-11-04 对齐 Web showExportModal：三种导出 + 网盘配置开关）══
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    // ★2026-11-05 首启「从网页版迁移」引导点「去导入」→ 直接落地到这个弹窗
    androidx.compose.runtime.LaunchedEffect(autoOpenImport) {
        if (autoOpenImport) {
            showImport = true
            onAutoOpenConsumed()
        }
    }
    // 网盘配置两级勾选（Web 默认：带配置、不带密码）
    var davCfgOn by remember { mutableStateOf(true) }
    var davPwdOn by remember { mutableStateOf(false) }

    /** 把 BackupExport 生成的字节写进用户选的位置 */
    fun writeExport(uri: android.net.Uri?, kind: com.xixi.hiking.nativeapp.data.BackupExport.Kind) {
        if (uri == null) return
        ioScope().launch {
            val ok = try {
                val bytes = com.xixi.hiking.nativeapp.data.BackupExport.build(
                    context, kind,
                    com.xixi.hiking.nativeapp.data.BackupExport.DavOpts(
                        includeCfg = davCfgOn, includePwd = davCfgOn && davPwdOn
                    )
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                // ★记下备份时间：概览页据此判断「超 30 天没备份」
                if (ok) com.xixi.hiking.nativeapp.data.Prefs.setLong(
                    context, com.xixi.hiking.nativeapp.data.Prefs.KEY_LAST_BACKUP,
                    System.currentTimeMillis()
                )
                toast = if (ok) "已导出" else "导出失败"
            }
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> writeExport(uri, com.xixi.hiking.nativeapp.data.BackupExport.Kind.FULL_ZIP) }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> writeExport(uri, com.xixi.hiking.nativeapp.data.BackupExport.Kind.DATA_ONLY) }
    val diagLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> writeExport(uri, com.xixi.hiking.nativeapp.data.BackupExport.Kind.DIAGNOSTICS) }

    // ★通知权限（Android 13+）：对齐 Web 版「通知权限未开启」那一行
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        com.xixi.hiking.nativeapp.data.UiPrefs.canNotify =
            com.xixi.hiking.nativeapp.data.Notifier.canPost(context)
        toast = if (granted) "通知已开启" else "没给权限，提醒不会弹"
    }

    // 导入
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ioScope().launch {
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) { null }
            val (ok, msg) = if (bytes == null || bytes.isEmpty()) false to "文件读不出来，可能被移走了"
            else importJson(context, bytes)
            withContext(Dispatchers.Main) {
                toast = if (ok) "导入完成：$msg" else "导入失败：$msg"
                if (ok) { refreshDav(); onImported() }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ★★★ 设置页 —— 按 Web 版 index.html 第 449 行起 1:1 复刻
    //
    //  Web 版结构：**整页只有一张大 glass-panel 卡片**，里面按
    //  分组标题 + 网格按钮 + 设置行 组织；不是原生版之前那种"每块一个卡片"。
    //  尺寸/间距/圆角/图标底色全部照抄 app.css（规格写在 SettingsComponents.kt 顶部）。
    //
    //  唯一有意偏离：文字色保持全站黑白（用户明确要求过），
    //  图标与开关描边仍用 Web 版的靛蓝 —— 那是页面上唯一的一点颜色。
    // ══════════════════════════════════════════════════════════════
    // ★★崩溃堆栈横幅（2026-11-02）：上次进程崩过就在这里直接显示堆栈。
    //   用户崩完重开 App → 进设置页 → 一眼看到死因 → 截图给我，不用再猜。
    var crashText by remember {
        mutableStateOf(com.xixi.hiking.nativeapp.data.CrashGuard.readLast(context))
    }

    // ★★2026-11-04 用户：「设置页底栏按钮再按一次不会回到顶，咋回事？」
    //   概览/计划/记录三页都接了 TabReselect，唯独设置页漏了 ——
    //   它自己 new 了一个 rememberScrollState()，外面拿不到，也就没人能把它滚回去。
    val settingsScroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue) {
        if (com.xixi.hiking.nativeapp.ui.TabReselect.tick.intValue > 0) {
            settingsScroll.animateScrollTo(0)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(settingsScroll)
            // ★2026-11-03 去框：外框没了，左右只留 10dp 呼吸位（原 12 页面边距 + 20 面板内边距 = 32dp 太窄）
            .padding(horizontal = 10.dp)
    ) {
        // ★顶栏让位：留白在滚动内容里，内容才能滚到玻璃底下
        if (topInset > 0.dp) Spacer(Modifier.height(topInset + 2.dp))
        crashText?.let { txt ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .androidxClip()
                    .androidxBg()
                    .padding(14.dp)
            ) {
                androidx.compose.material3.Text(
                    "上次闪退的堆栈（截图发给我就能定位）",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFB91C1C)
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    // 只取最后 1400 字：真正有用的（异常类型 + 我们自己的栈帧）都在末尾
                    txt.takeLast(1400),
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color(0xFF7F1D1D)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .androidxChip()
                        .safeClickable {
                            com.xixi.hiking.nativeapp.data.CrashGuard.clear(context)
                            crashText = null
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "已看到，清掉这条", fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = Ink
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // ★★2026-11-03 去掉最外面那张大 glassPanel（用户：「内容还是太窄，把最外边
                //   那个边框可以去掉」）。去掉的不只是边框 —— 连同它 20dp 的内边距一起撤了，
                //   内容宽度整整多出 40dp，这才是"变宽"的来源。
                //   分组感由 SettingsGroup 自己的标题 + 行分隔线承担，不需要再套一层壳。
                .padding(top = 2.dp, bottom = 12.dp)
        ) {
            // ── 标题（Web: text-2xl font-semibold + 齿轮）──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Settings, null,
                    tint = indigoColor(),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "设置", fontSize = 17.5.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 数据管理 ──────────────────────────────────────
            SettingsGroup(
                title = "数据管理",
                icon = Icons.Filled.Storage,
                trailing = {
                    // Web 版这里有个 ⓘ，点开是「数据管理说明」弹窗
                    Icon(
                        Icons.Filled.Info, contentDescription = "数据管理说明",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .size(15.dp)
                            .safeClickable { com.xixi.hiking.nativeapp.data.Trace.step("点击:数据管理说明"); showDataInfo = true }
                    )
                }
            ) {
                // 2 列大按钮：导入数据 / 导出数据（Web: grid grid-cols-2 gap-3, py-4）
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GridGlassButton(
                        icon = Icons.Filled.Download, label = "导入数据", cols = 2,
                        modifier = Modifier.weight(1f)
                    ) {
                        // ★2026-11-04 对齐 Web：先弹「导入」小窗（说明 + 选择文件），
                        //   而不是一点就直接拉系统选择器 —— 用户根本不知道要选什么文件。
                        showImport = true
                    }
                    GridGlassButton(
                        icon = Icons.Filled.Upload, label = "导出数据", cols = 2,
                        modifier = Modifier.weight(1f)
                    ) {
                        // ★2026-11-04 对齐 Web：先弹「导出备份」小窗，三选一 + 网盘配置开关
                        davCfgOn = true
                        davPwdOn = false
                        showExport = true
                    }
                }

                // 抹掉所有足迹（Web: check-go-btn，图标 17 + 13sp/600）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        // ★2026-11-03 用户：「抹掉所有足迹的按钮还是实色没改」
                        //   Web 这里是 .check-go-btn：**淡红底 + 0.9 红边 + 红字**，
                        //   而不是一块实心红。这里补上 danger。
                        .liquidGlassChip(corner = 12, flat = true, danger = true)
                        .safeClickable { com.xixi.hiking.nativeapp.data.Trace.step("点击:抹掉所有足迹"); wipeStep = 1 }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DeleteForever, null,
                            // 图标原来用的是主题色（靛蓝），跟"危险操作"完全不搭 —— 跟文字一起改红
                            tint = if (isDark) Color(0xFFFECACA) else Color(0xFFB91C1C),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "抹掉所有足迹",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFFECACA) else Color(0xFFB91C1C)
                        )
                    }
                }

                // ★2026-11-05 用户：「下载和云端文件是不是功能一样，把下载删掉吧，保留云端文件」
                //   确实重复：原来「下载」= 直接拉云端最新那份并覆盖导入，
                //   「云端文件」= 列目录后自己挑一份。后者是前者的超集（最新那份也在列表里），
                //   而且能看到时间/大小再决定，误覆盖的风险更低。
                //   ⇒ 只留「上传 / 云端文件」两个，改成两列（图标也大一号）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    GridGlassButton(
                        icon = Icons.Filled.CloudUpload, label = if (syncBusy) "上传中" else "上传",
                        cols = 2, modifier = Modifier.weight(1f), enabled = !syncBusy
                    ) {
                        if (!dav.configured) { toast = "先绑定账号" ; return@GridGlassButton }
                        if (!com.xixi.hiking.nativeapp.data.Net.isOnline(context)) {
                            toast = "离线，稍后再试" ; return@GridGlassButton
                        }
                        syncBusy = true
                        ioScope().launch {
                            // ★2026-11-05 传的是**整包 zip**（含照片 + 回忆册），
                            //   命名与目录都对齐 Web 版，两边云端互相看得见
                            val (ok, name) = com.xixi.hiking.nativeapp.data.WebDavClient
                                .uploadBackup(context)
                            withContext(Dispatchers.Main) {
                                syncBusy = false
                                if (ok) com.xixi.hiking.nativeapp.data.Prefs.setLong(
                                    context, com.xixi.hiking.nativeapp.data.Prefs.KEY_LAST_BACKUP,
                                    System.currentTimeMillis()
                                )
                                // 记一笔同步状态，供「同步状态」弹窗显示
                                com.xixi.hiking.nativeapp.data.Prefs.setLong(context,
                                    com.xixi.hiking.nativeapp.data.Prefs.KEY_SYNC_LAST_AT, System.currentTimeMillis())
                                com.xixi.hiking.nativeapp.data.Prefs.setBool(context,
                                    com.xixi.hiking.nativeapp.data.Prefs.KEY_SYNC_LAST_OK, ok)
                                com.xixi.hiking.nativeapp.data.Prefs.setString(context,
                                    com.xixi.hiking.nativeapp.data.Prefs.KEY_SYNC_LAST_ACT, "上传")
                                syncTick++
                                toast = if (ok) "已上传：$name" else "上传失败：$name"
                            }
                        }
                    }
                    GridGlassButton(
                        icon = Icons.Filled.FolderOpen, label = "云端文件",
                        cols = 2, modifier = Modifier.weight(1f), enabled = !syncBusy
                    ) {
                        if (!dav.configured) { toast = "先绑定账号" ; return@GridGlassButton }
                        if (!com.xixi.hiking.nativeapp.data.Net.isOnline(context)) {
                            toast = "离线，稍后再试" ; return@GridGlassButton
                        }
                        // ★对齐 Web 版「管理云端备份」：列目录 → 下载 / 删除
                        showCloud = true
                    }
                }

                // ── 账号卡（Web: #syncAccountCard）──
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.04f)
                            else Color.White.copy(alpha = 0.30f)
                        )
                        .border(
                            1.dp,
                            if (isDark) Color(0xFF94B4FF).copy(alpha = 0.14f)
                            else Color(0xFF0F172A).copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (!dav.configured) {
                        // 未绑定：说明 + 主按钮
                        Column {
                            Text(
                                "还没有绑定", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "换手机不用重填",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 11.dp)
                                    .liquidGlassChip(corner = 12, flat = true)
                                    .safeClickable { com.xixi.hiking.nativeapp.data.Trace.step("点击:绑定账号"); showSync = true }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "绑定账号", fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, color = Ink
                                )
                            }
                        }
                    } else {
                        // 已绑定：●已绑定 + 网盘信息 / 解绑
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "已绑定", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier
                                    .liquidGlassChip(corner = 12, flat = true)
                                    .safeClickable { com.xixi.hiking.nativeapp.data.Trace.step("点击:绑定账号"); showSync = true }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    "网盘信息", fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold, color = Ink
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .liquidGlassChip(corner = 12, flat = true)
                                    .safeClickable {
                                        com.xixi.hiking.nativeapp.data.WebDavClient.save(
                                            context, com.xixi.hiking.nativeapp.data.WebDavClient.Config()
                                        )
                                        refreshDav()
                                        toast = "已解绑"
                                        showSync = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    "解绑", fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold, color = Ink
                                )
                            }
                        }
                    }
                }

                // ── 自动同步（Web: #autoSyncCard：开关行 + 状态子行）──
                SettingsItem(
                    icon = Icons.Filled.Autorenew,
                    title = "自动同步",
                    desc = "变更后自动上传",
                    topGap = 10,
                    trailing = {
                        GlassSwitch(checked = autoSync, tag = "sw_auto") { on ->
                            autoSync = on
                            com.xixi.hiking.nativeapp.data.Prefs.setBool(
                                context, com.xixi.hiking.nativeapp.data.Prefs.KEY_AUTO_SYNC, on
                            )
                            toast = if (on) "已开启自动同步" else "已关闭自动同步"
                        }
                    }
                )
                // 状态子行（Web: #syncHealthRow）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp)
                        // ★2026-11-05 用户：「把同步状态那个弹窗挪到自动同步下边那一栏，
                        //   没打开显示上次同步时间状态，点开显示弹窗内容」。
                        //   已配置 → 打开「同步状态」；未配置 → 打开绑定弹窗（否则点了没反应）。
                        .safeClickable {
                            com.xixi.hiking.nativeapp.data.Trace.step("点击:同步状态")
                            if (dav.configured) showSyncStatus = true else showSync = true
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (dav.configured) Color(0xFF22C55E)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                    Spacer(Modifier.width(7.dp))
                    // 读 syncTick：上传/下载完要立刻反映到这一行
                    @Suppress("UNUSED_VARIABLE") val tick = syncTick
                    Text(
                        if (dav.configured) {
                            runCatching {
                                val last = com.xixi.hiking.nativeapp.data.Prefs
                                    .getLong(context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SYNC_LAST_AT, 0L)
                                if (last <= 0) "还没同步过" else {
                                    val ok = com.xixi.hiking.nativeapp.data.Prefs.getBool(
                                        context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SYNC_LAST_OK, true
                                    )
                                    com.xixi.hiking.nativeapp.data.RelativeTime.ago(last) +
                                        if (ok) " · 成功" else " · 失败"
                                }
                            }.getOrElse { "状态读取失败" }
                        } else "未配置 · 点这里绑定",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── 外观（Web: 两个开关行）────────────────────────
            SettingsGroup(title = "外观", icon = Icons.Filled.Palette) {
                val followSystem = themeMode == "auto"
                val darkOn = if (followSystem) isDark else themeMode == "dark"
                SettingsItem(
                    icon = Icons.Filled.BrightnessAuto,
                    title = "跟随系统外观模式",
                    desc = "跟随系统深浅色",
                    topGap = 0,
                    trailing = {
                        GlassSwitch(checked = followSystem, tag = "sw_follow") { on ->
                            val next = if (on) "auto" else if (isDark) "dark" else "light"
                            themeMode = next
                            com.xixi.hiking.nativeapp.data.Prefs.setString(
                                context, com.xixi.hiking.nativeapp.data.Prefs.KEY_THEME, next
                            )
                            onThemeChange(next)
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Filled.DarkMode,
                    title = "外观模式",
                    desc = if (followSystem) "跟随系统时由系统决定" else "手动切换深色 / 浅色",
                    topGap = 10,
                    trailing = {
                        // ★2026-11-03 修语义冲突：跟随系统时这颗开关被上级接管，
                        //   以前它仍然可点、而且显示成"当前是深色=开"——两个开关同时亮着，
                        //   点了还会把"跟随系统"悄悄关掉。现在置灰不可点。
                        GlassSwitch(checked = darkOn, tag = "sw_dark", enabled = !followSystem) { dark ->
                            val next = if (dark) "dark" else "light"
                            themeMode = next
                            com.xixi.hiking.nativeapp.data.Prefs.setString(
                                context, com.xixi.hiking.nativeapp.data.Prefs.KEY_THEME, next
                            )
                            onThemeChange(next)
                        }
                    }
                )
            }

            // ── 杂项（Web: 照片占用 / 检查更新；原生版另加备份与诊断）──
            SettingsGroup(title = "杂项", icon = Icons.Filled.Tune) {
                SettingsItem(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "照片占用",
                    // ★★2026-11-04 对齐 Web `refreshPhotoUsage()`：
                    //   这一行原来写的是固定文案「清理照片缓存」，Web 是**真实统计**
                    //   （「照片 42 张 · 12.3 MB（含孤立 3 张，点开可优化）」/「暂无照片」）。
                    //   现在在设置页一进来就扫一次，关掉弹窗后再刷一次。
                    desc = photoUsageDesc,
                    titleSuffix = "（点开看详情）",
                    // ★2026-11-04 修「每页条数和照片占用挤在一起」：
                    //   两项原来都是 topGap = 0（各自都以为是分组第一项），
                    //   于是中间没有半点缝，视觉上糊成一块。
                    //   现在每页条数保持 0（它是分组第一个），照片占用给标准 10dp。
                    topGap = 0,
                    onClick = { showPhotoUsage = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:照片占用") }
                )

                // ★2026-11-04 每页条数（Web 端这是远程配置，原生做成用户可调）
                SettingsItem(
                    icon = Icons.Filled.Tune,
                    title = "每页条数",
                    desc = "记录和计划每页 $pageSizePref 条",
                    topGap = 10,
                    trailing = {
                        Icon(
                            Icons.Filled.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { showPageSize = true }
                )

                // ★显示帧率（对齐 Web 版 .settings-item + #fpsToggle）：
                //   开关只改偏好，真正的统计在顶栏 FpsPill（关掉时零开销）
                var showFps by remember {
                    mutableStateOf(
                        com.xixi.hiking.nativeapp.data.Prefs.getBool(
                            context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SHOW_FPS, false
                        )
                    )
                }
                SettingsItem(
                    icon = Icons.Filled.Speed,
                    title = "显示帧率",
                    desc = "顶栏显示实时帧率",
                    topGap = 10,
                    trailing = {
                        GlassSwitch(checked = showFps, tag = "sw_fps") { on ->
                            showFps = on
                            com.xixi.hiking.nativeapp.data.Prefs.setBool(
                                context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SHOW_FPS, on
                            )
                            com.xixi.hiking.nativeapp.data.UiPrefs.showFps = on
                        }
                    }
                )

                // ★震动反馈（对齐 Web 版 .settings-item + #hapticToggle）：
                //   开关只写偏好，真正的震动在 safeClickable 的入口统一做。
                var haptic by remember {
                    mutableStateOf(
                        com.xixi.hiking.nativeapp.data.Prefs.getBool(
                            context, com.xixi.hiking.nativeapp.data.Prefs.KEY_HAPTIC, true
                        )
                    )
                }
                SettingsItem(
                    icon = Icons.Filled.Vibration,
                    title = "震动反馈",
                    desc = "点击轻微震动",
                    topGap = 10,
                    trailing = {
                        GlassSwitch(checked = haptic, tag = "sw_haptic") { on ->
                            haptic = on
                            com.xixi.hiking.nativeapp.data.Prefs.setBool(
                                context, com.xixi.hiking.nativeapp.data.Prefs.KEY_HAPTIC, on
                            )
                        }
                    }
                )

                // ★2026-11-05 安装包校验（对应网页版 ResGuard 的"资源校验"）
                //   显示一行结论，点一下看详情。文案遵守「说明 ≤10 字」。
                run {
                    val ir = com.xixi.hiking.nativeapp.data.Integrity.check(context)
                    val ok = ir.state == com.xixi.hiking.nativeapp.data.Integrity.State.OK
                    SettingsItem(
                        icon = if (ok) Icons.Filled.VerifiedUser else Icons.Filled.GppMaybe,
                        title = "安装包校验",
                        desc = when (ir.state) {
                            com.xixi.hiking.nativeapp.data.Integrity.State.OK -> "官方签名"
                            com.xixi.hiking.nativeapp.data.Integrity.State.MODIFIED -> "已被改动"
                            com.xixi.hiking.nativeapp.data.Integrity.State.UNKNOWN -> "无法读取"
                        },
                        topGap = 10,
                        onClick = {
                            toast = when (ir.state) {
                                com.xixi.hiking.nativeapp.data.Integrity.State.OK ->
                                    "签名一致，是官方包"
                                com.xixi.hiking.nativeapp.data.Integrity.State.MODIFIED ->
                                    "签名不一致，请重装"
                                com.xixi.hiking.nativeapp.data.Integrity.State.UNKNOWN ->
                                    "这台设备读不到签名"
                            }
                        }
                    )
                }

                // ★通知权限（Web: #notifyPermItem —— 没权限时才出现，有权限就整行隐藏）
                if (!com.xixi.hiking.nativeapp.data.UiPrefs.canNotify) {
                    SettingsItem(
                        icon = Icons.Filled.NotificationsOff,
                        title = "通知权限未开启",
                        desc = "允许弹出提醒",
                        topGap = 10,
                        onClick = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                        trailing = {
                            Icon(
                                Icons.Filled.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }

                // ★2026-11-04 用户：「检测更新放到震动反馈下面」——
                //   原来它排在最前面（紧跟照片占用），现在按用户要求挪到开关组的最后。
                // ★★2026-11-03 用户：「设置页杂项里的检测更新去哪了？」
                //   之前是被故意摘掉的 —— 那个版本查的是**网页版仓库**，会报出网页版的版本号，
                //   一比就"有新版本"，误导。现在 Updater 指向本应用自己的 releases，可以放回来了。
                //
                // ★★2026-11-09 应用内更新：这一行同时当**下载状态指示器**用。
                //   弹窗可以关掉，下载在后台继续；回到设置页还能看见进行到哪了。
                val dl = com.xixi.hiking.nativeapp.data.ApkDownloader.state.collectAsState().value
                SettingsItem(
                    icon = Icons.Filled.SystemUpdate,
                    title = "检测更新",
                    desc = when (val s = dl) {
                        is com.xixi.hiking.nativeapp.data.ApkDownloader.State.Downloading ->
                            if (s.total > 0) "正在下载 ${(s.done * 100 / s.total).toInt()}%"
                            else "正在下载…"
                        is com.xixi.hiking.nativeapp.data.ApkDownloader.State.Installing -> "正在安装…"
                        is com.xixi.hiking.nativeapp.data.ApkDownloader.State.Ready -> "已下载，可安装"
                        is com.xixi.hiking.nativeapp.data.ApkDownloader.State.Failed -> "上次下载失败"
                        else -> if (checkingUpdate) "正在检查…" else "应用内直接更新"
                    },
                    topGap = 10,
                    onClick = {
                        if (checkingUpdate) return@SettingsItem
                        // 下载好了就直接装，不用再查一遍
                        val ready = dl as? com.xixi.hiking.nativeapp.data.ApkDownloader.State.Ready
                        if (ready != null) {
                            com.xixi.hiking.nativeapp.data.ApkDownloader
                                .installCached(context, ready.version)
                            return@SettingsItem
                        }
                        // 正在下载 → 只提示，别打断
                        if (dl is com.xixi.hiking.nativeapp.data.ApkDownloader.State.Downloading) {
                            toast = "正在下载，稍等"
                            return@SettingsItem
                        }
                        checkingUpdate = true
                        ioScope().launch {
                            // ★★★2026-11-03 三态结果（用户：「检测更新不允许检测到 web 版的更新」）
                            //   以前只有「有更新 / 检查失败」两态，而"版本号一样"是被
                            //   Updater 内部当失败吞掉的。现在 Updater 只挑**本应用**发布，
                            //   没有更新时明确返回 UpToDate，这里如实说"已是最新"。
                            when (val res = com.xixi.hiking.nativeapp.data.Updater.check(context)) {
                                is com.xixi.hiking.nativeapp.data.Updater.Result.Update -> {
                                    updateRelease = res.release
                                }
                                com.xixi.hiking.nativeapp.data.Updater.Result.Offline -> {
                                    // 离线不是错误：中性一句话，别说"失败"
                                    toast = "离线，稍后再试"
                                }
                                com.xixi.hiking.nativeapp.data.Updater.Result.UpToDate -> {
                                    toast = "已是最新版本 v${com.xixi.hiking.nativeapp.BuildConfig.VERSION_NAME}"
                                }
                                com.xixi.hiking.nativeapp.data.Updater.Result.Failed -> {
                                    toast = "检查失败，稍后再试"
                                }
                            }
                            checkingUpdate = false
                        }
                    }
                )
            }

            // ── 关于应用（Web: .about-card）────────────────────
            SettingsGroup(
                title = "关于应用",
                icon = Icons.Filled.Info,
                bottomMargin = false
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ★★2026-11-04 用户：「关于应用下我的山呢，放回来，别放个图片」
                    //   上一版是个靛蓝圆角方块里塞一枚 Terrain 图标 —— 那是 Material 的
                    //   "地形"图形，跟顶栏那座山完全不是一个东西，看着像占位图。
                    //   现在换成和顶栏**同一个** 🏔️（见 designcheck 的 EMOJI_OK 白名单：
                    //   这两个文件里它是品牌标识，不是正文表情）。
                    Text("🏔️", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "徒步小记", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "版本 ${BuildConfig.VERSION_NAME}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // ★★2026-11-03 对齐 Web 的 `.about-since`（徒步年资）
                    //   按**最早一条记录**算，没记录就整行不显示 —— 不能写死"第 1 天"
                    firstRecordAt?.let { d ->
                        val since = runCatching {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(d.take(10))
                        }.getOrNull()
                        if (since != null) {
                            val days = ((System.currentTimeMillis() - since.time) / 86_400_000L)
                                .coerceAtLeast(0L) + 1
                            val cal = java.util.Calendar.getInstance().apply { time = since }
                            val ym = "${cal.get(java.util.Calendar.YEAR)} 年 ${cal.get(java.util.Calendar.MONTH) + 1} 月"
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "徒步第 $days 天 · 从 $ym 出发",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Web 里 "XiXi" 是 <b class="about-highlight">：加粗 + 主题色
                    Text(
                        buildAnnotatedString {
                            append("一款由 ")
                            withStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = indigoColor()
                                )
                            ) { append("XiXi") }
                            // textcheck:ok 「关于应用」的介绍，用户明确要求加回完整说明
                            append(" 亲手制作的徒步记录小软件：记录每一次徒步、登山的足迹，" +
                                "统计海拔与难度，规划下一次出发。")
                        },
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    // 四个玻璃按钮：隐私政策 / 免责声明 // 支持作者 / 更新日志
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.liquidGlassChip(corner = 12, flat = true)
                                .safeClickable { showPrivacy = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:隐私政策") }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("隐私政策", fontSize = 13.sp, color = Ink) }
                        Box(
                            Modifier.liquidGlassChip(corner = 12, flat = true)
                                .safeClickable { showDisclaimer = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:免责声明") }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("免责声明", fontSize = 13.sp, color = Ink) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.liquidGlassChip(corner = 12, flat = true)
                                .safeClickable { showSupport = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:支持作者") }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("支持作者", fontSize = 13.sp, color = Ink) }
                        Box(
                            Modifier.liquidGlassChip(corner = 12, flat = true)
                                .safeClickable { showChangelog = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:更新日志") }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("更新日志", fontSize = 13.sp, color = Ink) }
                    }
                    // ★★2026-11-04 用户：「关于应用里的 github 和官方网站先删掉」
                    //   这两个外链先撤掉（页面内容和版本未必同步，指向外面容易误导）。
                    //   函数 AboutLink / openUrl 保留，以后要加回来直接调即可。
                }
            }
        }

        // ★2026-11-04 用户要求去掉「上次执行到：xxx」那行红字 ——
        //   它是给我排查闪退用的内部痕迹，不该出现在用户的设置页里。
        //   （轨迹本身照写：诊断报告导出里还有「上次运行最后一步」，需要时仍能拿到。）

        Spacer(Modifier.height(96.dp))   // 给悬浮底栏让位
    }


    if (showSync) {
        SyncConfigDialog(
            initial = dav,
            hazeState = hazeState,
            onDismiss = { showSync = false },
            onSave = { c ->
                com.xixi.hiking.nativeapp.data.WebDavClient.save(context, c)
                refreshDav()
                showSync = false
                toast = "已保存配置"
            },
            onTest = { c ->
                com.xixi.hiking.nativeapp.data.WebDavClient.save(context, c)
                refreshDav()
                toast = "测试中…"
                ioScope().launch {
                    val (ok, msg) = if (!com.xixi.hiking.nativeapp.data.Net.isOnline(context)) {
                        false to "离线，稍后再试"
                    } else com.xixi.hiking.nativeapp.data.WebDavClient.test(context)
                    withContext(Dispatchers.Main) { toast = (if (ok) "✅ " else "❌ ") + msg }
                }
            }
        )
    }

    if (showCloud) {
        CloudBackupsDialog(
            hazeState = hazeState,
            onDismiss = { showCloud = false },
            onRestored = { refreshDav(); onImported() }
        )
    }

    if (showDataInfo) {
        com.xixi.hiking.nativeapp.ui.GlassConfirm(
            hazeState = hazeState,
            title = "数据管理说明",
            message = "· 数据全部存在本机（Room 数据库 + 照片文件），不上传任何服务器。\n" +
                "· 导出备份会生成一个 JSON 文件，可存到网盘或发给新手机再导入。\n" +
                "· 导入时同 id 的条目会覆盖，建议先导出备份。\n" +
                "· 抹掉后记录、照片、计划全部删除，无法恢复",
            // ★2026-11-05 纯告知弹窗：一个「关闭」就够（原来「知道了 / 关闭」两个同义按钮）
            singleButton = true,
            confirmText = "关闭",
            onConfirm = { showDataInfo = false },
            onCancel = { showDataInfo = false }
        )
    }

    // ══ 导出备份（★★2026-11-04 对齐 Web showExportModal）══
    if (showExport) {
        com.xixi.hiking.nativeapp.ui.GlassOverlay(
            hazeState = hazeState,
            onDismiss = { showExport = false },
            maxWidthFraction = 0.92f,
            corner = 20,
            bottomClose = false
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Backup, null,
                        tint = com.xixi.hiking.nativeapp.ui.theme.AccentIndigo, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "导出备份", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(12.dp))

                // ① 完整备份（zip，含照片）
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "导出完整备份压缩包",
                    primary = true,
                    onClick = {
                        showExport = false
                        zipLauncher.launch(
                            com.xixi.hiking.nativeapp.data.BackupExport.fileName(
                                com.xixi.hiking.nativeapp.data.BackupExport.Kind.FULL_ZIP
                            )
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                // ② 纯数据（不含照片，体积小）
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "导出纯数据备份",
                    onClick = {
                        showExport = false
                        jsonLauncher.launch(
                            com.xixi.hiking.nativeapp.data.BackupExport.fileName(
                                com.xixi.hiking.nativeapp.data.BackupExport.Kind.DATA_ONLY
                            )
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                // ③ 诊断报告（不含任何记录内容）
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "导出诊断报告",
                    onClick = {
                        showExport = false
                        diagLauncher.launch(
                            com.xixi.hiking.nativeapp.data.BackupExport.fileName(
                                com.xixi.hiking.nativeapp.data.BackupExport.Kind.DIAGNOSTICS
                            )
                        )
                    }
                )

                // 网盘配置（只在已绑定网盘时出现 —— 没绑就没有配置可带）
                if (dav.configured) {
                    Spacer(Modifier.height(12.dp))
                    ExportOpt(
                        checked = davCfgOn,
                        title = "同时包含网盘配置",
                        sub = "换机后自动填好地址与账号（不含密码）"
                    ) { on ->
                        davCfgOn = on
                        if (!on) davPwdOn = false
                    }
                    if (davCfgOn) {
                        Spacer(Modifier.height(4.dp))
                        ExportOpt(
                            checked = davPwdOn,
                            title = "连应用密码一起带上",
                            sub = "含网盘地址与账号，文件外泄别人就能访问你的网盘"
                        ) { davPwdOn = it }
                    }
                }

                Spacer(Modifier.height(14.dp))
                com.xixi.hiking.nativeapp.ui.GlassButton("取消", onClick = com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current)
            }
        }
    }

    // ══ 导入（★★2026-11-04 对齐 Web showImportModal）══
    if (showImport) {
        com.xixi.hiking.nativeapp.ui.GlassOverlay(
            hazeState = hazeState,
            onDismiss = { showImport = false },
            maxWidthFraction = 0.92f,
            corner = 20,
            bottomClose = false
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.UploadFile, null,
                        tint = com.xixi.hiking.nativeapp.ui.theme.AccentIndigo, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "导入", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "支持 zip 完整备份、纯数据备份（.json / .html）与云端备份文件；" +
                        "同一条记录按最后修改时间取新，本地独有的记录会保留。\n\n" +
                        "从网页版搬过来：在网页版「关于应用 → 导出完整备份」得到一个 zip，" +
                        "在这里选它就能把记录、照片、计划一起搬过来。",
                    fontSize = 12.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                com.xixi.hiking.nativeapp.ui.GlassButton(
                    "选择备份文件",
                    primary = true,
                    onClick = {
                        showImport = false
                        // ★必须放开 html：Web 版「导出纯数据备份」产出的就是 .html（内嵌 JSON）
                        importLauncher.launch(
                            arrayOf("application/json", "text/html", "text/plain", "application/zip", "*/*")
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                com.xixi.hiking.nativeapp.ui.GlassButton("取消", onClick = com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current)
            }
        }
    }

    // ══ 每页条数（5 / 10 / 15 / 20 / 30 / 50）══
    if (showPageSize) {
        com.xixi.hiking.nativeapp.ui.GlassOverlay(
            hazeState = hazeState,
            onDismiss = { showPageSize = false },
            maxWidthFraction = 0.90f,
            corner = 20,
            bottomClose = true
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Tune, null,
                        tint = com.xixi.hiking.nativeapp.ui.theme.AccentIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "每页条数", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "记录和计划列表每页显示多少条（5～50）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                listOf(5, 10, 15, 20, 30, 50).forEach { n ->
                    val on = n == pageSizePref
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (on) com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
                                    .copy(alpha = if (isDark) 0.20f else 0.10f)
                                else Color.Transparent
                            )
                            .safeClickable {
                                pageSizePref = n
                                com.xixi.hiking.nativeapp.data.Prefs.setInt(
                                    context,
                                    com.xixi.hiking.nativeapp.data.Prefs.KEY_PAGE_SIZE,
                                    n
                                )
                                showPageSize = false
                                toast = "每页改为 $n 条"
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$n 条", fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        if (on) {
                            Icon(
                                Icons.Filled.Check, null,
                                tint = com.xixi.hiking.nativeapp.ui.theme.AccentIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPhotoUsage) {
        PhotoUsageDialog(hazeState) {
            showPhotoUsage = false
            photoUsageTick++      // 清完缓存回来，这一行的数字要跟着变
        }
    }
    if (showChangelog) ChangelogDialog(hazeState) { showChangelog = false }
    if (showPrivacy) PrivacyPolicyDialog(hazeState) { showPrivacy = false }
    if (showDisclaimer) DisclaimerDialog(hazeState) { showDisclaimer = false }
    if (showSupport) SupportDialog(hazeState) { showSupport = false }
    updateRelease?.let { r ->
        UpdateAvailableDialog(
            hazeState = hazeState,
            release = r,
            // ★2026-11-09 不再跳浏览器：应用内下载 → 校验签名 → 拉起系统安装器。
            //   ★故意**不关弹窗**：进度和失败原因就地显示，关掉就什么也看不到了。
            onStart = {
                com.xixi.hiking.nativeapp.data.ApkDownloader.start(context, r)
            },
            onDismiss = { updateRelease = null }
        )
    }

    // ★2026-11-05 同步状态弹窗
    if (showSyncStatus) {
        val lastAt = com.xixi.hiking.nativeapp.data.Prefs.getLong(
            context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SYNC_LAST_AT, 0L
        )
        val lastOk = com.xixi.hiking.nativeapp.data.Prefs.getBool(
            context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SYNC_LAST_OK, true
        )
        val act = com.xixi.hiking.nativeapp.data.Prefs.getString(
            context, com.xixi.hiking.nativeapp.data.Prefs.KEY_SYNC_LAST_ACT, ""
        )
        val online = com.xixi.hiking.nativeapp.data.Net.isOnline(context)
        val body = if (lastAt <= 0L) {
            if (online) "还没同步过" else "离线中"
        } else {
            "上次：${com.xixi.hiking.nativeapp.data.RelativeTime.ago(lastAt)}\n" +
                "动作：$act\n" +
                "结果：" + (if (lastOk) "成功" else "失败")
        }
        com.xixi.hiking.nativeapp.ui.GlassConfirm(
            hazeState = hazeState,
            title = "同步状态",
            message = body,
            singleButton = true,
            confirmText = "关闭",
            onConfirm = { showSyncStatus = false },
            onCancel = { showSyncStatus = false }
        )
    }

    // ★★2026-11-03 真正的 toast：以前这里是个 GlassConfirm 弹窗（要点「好」才消），
    //   用户反馈"现在都没有 toast 了"。现在统一走 AppToast：底部浮出、自动消失、点一下就关。
    androidx.compose.runtime.LaunchedEffect(toast) {
        toast?.let {
            com.xixi.hiking.nativeapp.ui.AppToast.show(it)
            toast = null
        }
    }

    // ★抹掉所有足迹：第一次确认
    if (wipeStep == 1) {
        com.xixi.hiking.nativeapp.ui.GlassConfirm(
            hazeState = hazeState,
            title = "抹掉所有足迹？",
            message = "将删除 $recordCount 条记录、$planCount 个计划和全部照片，不可恢复。\n\n建议先点「导出备份」留一份。",
            confirmText = "继续",
            danger = true,
            onConfirm = { wipeStep = 2 },
            onCancel = { wipeStep = 0 }
        )
    }
    // ★最后确认
    if (wipeStep == 2) {
        com.xixi.hiking.nativeapp.ui.GlassConfirm(
            hazeState = hazeState,
            title = "最后确认",
            message = "这一步会永久删除全部徒步数据与照片，并解除网盘绑定；云端备份文件不受影响。",
            confirmText = "彻底抹掉",
            cancelText = "返回",
            danger = true,
            onConfirm = {
                wipeStep = 0
                ioScope().launch {
                    val db = AppDatabaseRef.get(context)
                    // 先删文件再删表，避免留下无主的照片文件
                    db.photoDao().allOnce().forEach {
                        com.xixi.hiking.nativeapp.data.PhotoStore.delete(context, it.fileName)
                    }
                    db.photoDao().deleteAll()
                    db.recordDao().deleteAll()
                    db.plannedTripDao().deleteAll()
                    // 引导与里程碑标记一并清（否则「全新开始」还留着旧痕迹）
                    listOf(
                        com.xixi.hiking.nativeapp.data.Prefs.KEY_GUIDE_OVERVIEW,
                        com.xixi.hiking.nativeapp.data.Prefs.KEY_GUIDE_RECORDS,
                        com.xixi.hiking.nativeapp.data.Prefs.KEY_GUIDE_PLANS,
                        com.xixi.hiking.nativeapp.data.Prefs.KEY_MS_SEEN
                    ).forEach { com.xixi.hiking.nativeapp.data.Prefs.remove(context, it) }
                    // 解除网盘绑定（云端文件不动）
                    com.xixi.hiking.nativeapp.data.WebDavClient.save(
                        context, com.xixi.hiking.nativeapp.data.WebDavClient.Config()
                    )
                    withContext(Dispatchers.Main) {
                        refreshDav()      // ★解绑后卡片要立刻回到「还没有绑定」
                        toast = "已抹掉全部足迹"
                        onImported()
                    }
                }
            },
            onCancel = { wipeStep = 0 }
        )
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
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
private fun StatInline(label: String, value: String, modifier: Modifier = Modifier) {
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
private fun SettingRow(
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

private fun Modifier.androidxClip(): Modifier =
    this.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))

private fun Modifier.androidxBg(): Modifier = this
    .background(Color(0x14DC2626))
    .border(
        1.dp, Color(0xFFDC2626).copy(alpha = 0.35f),
        androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    )

@androidx.compose.runtime.Composable
private fun Modifier.androidxChip(): Modifier = this.liquidGlassChip(corner = 12, flat = true)
