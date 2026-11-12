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
            // ★2026-11-11 搬到 SettingsSections.kt 的 DataSection（syncBusy/autoSync 内聚）
            DataSection(
                dav = dav,
                onShowSync = { showSync = it },
                onShowCloud = { showCloud = it },
                onShowExport = { showExport = it },
                onShowImport = { showImport = it },
                onShowSyncStatus = { showSyncStatus = it },
                onRefreshDav = { refreshDav() },
                onToast = { toast = it },
                onShowDataInfo = { showDataInfo = it },
                onExportOptions = { c, p -> davCfgOn = c; davPwdOn = p },
                onWipeStep = { wipeStep = it },
                syncTick = syncTick,
                onSyncTick = { syncTick++ },
            )

            // ── 外观 ────────────────────────────────────────
            // ★2026-11-11 搬到 SettingsSections.kt（无状态组件）。
            //   写 Prefs / 切主题 的副作用集中在这里，分组本身只认值和回调。
            AppearanceSection(
                themeMode = themeMode,
                isDark = isDark,
                onPick = { next ->
                    themeMode = next
                    com.xixi.hiking.nativeapp.data.Prefs.setString(
                        context, com.xixi.hiking.nativeapp.data.Prefs.KEY_THEME, next
                    )
                    onThemeChange(next)
                }
            )

            // ── 杂项 ──────────────────────────────────────
            // ★2026-11-11 搬到 SettingsSections.kt 的 MiscSection（无状态 + 自查更新状态内聚）
            MiscSection(
                photoUsageDesc = photoUsageDesc,
                pageSizePref = pageSizePref,
                notifLauncher = notifLauncher,
                onOpenPhotoUsage = {
                    showPhotoUsage = true
                    com.xixi.hiking.nativeapp.data.Trace.step("点击:照片占用")
                },
                onOpenPageSize = { showPageSize = true },
                onToast = { toast = it },
                onUpdateFound = { updateRelease = it },
            )

            // ── 关于应用（Web: .about-card）────────────────────
            // ★2026-11-11 搬到 SettingsSections.kt 的 AboutSection（无状态）
            AboutSection(
                firstRecordAt = firstRecordAt,
                onChangelog = { showChangelog = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:更新日志") },
                onPrivacy = { showPrivacy = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:隐私政策") },
                onDisclaimer = { showDisclaimer = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:免责声明") },
                onSupport = { showSupport = true; com.xixi.hiking.nativeapp.data.Trace.step("点击:支持作者") },
            )
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
    // ★2026-11-12 改成命名参数：ChangelogDialog 后面多了个 entries（默认值），
    //   尾随 lambda 会把 onDismiss 对到错的参数位置上
    if (showChangelog) ChangelogDialog(hazeState, onDismiss = { showChangelog = false })
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
