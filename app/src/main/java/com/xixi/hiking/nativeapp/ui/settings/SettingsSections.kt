package com.xixi.hiking.nativeapp.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.BuildConfig
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.ui.graphics.Color
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.foundation.border

// ══════════════════════════════════════════════════════════════
//  ★2026-11-11 从 SettingsScreen.kt 拆出来的分组（第 2 步）
//
//  规矩（写在这里提醒后来的人）：
//    · 这些函数一律**无状态**：不持有 remember、不读写 Prefs、不碰 context
//    · 只接"当前值 + 回调"，写完 Prefs / 切主题 的活留给调用方
//    · 好处不只是行数：分组单独一个文件后，改外观不会被旁边 800 行同步逻辑干扰；
//      而且这种签名天然可测（给值、给回调，断言点了什么）
// ══════════════════════════════════════════════════════════════

/**
 * 外观：跟随系统 + 深色开关。
 *
 * 两个开关有**上下级关系**：开了「跟随系统」，下面那颗就被接管（置灰、不可点）。
 * 以前这里是两个独立开关，跟随系统时下面还亮着，点了会把跟随悄悄关掉 —— 语义冲突。
 */
@Composable
internal fun AppearanceSection(
    themeMode: String,
    isDark: Boolean,
    /** 用户选了新模式的回调（"auto" / "dark" / "light"） */
    onPick: (String) -> Unit
) {
    val followSystem = themeMode == "auto"
    val darkOn = if (followSystem) isDark else themeMode == "dark"

    SettingsGroup(title = "外观", icon = Icons.Filled.Palette) {
        SettingsItem(
            icon = Icons.Filled.BrightnessAuto,
            title = "跟随系统外观模式",
            desc = "跟随系统深浅色",
            topGap = 0,
            trailing = {
                GlassSwitch(checked = followSystem, tag = "sw_follow") { on ->
                    val next = if (on) "auto" else if (isDark) "dark" else "light"
                    onPick(next)
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
                    onPick(if (dark) "dark" else "light")
                }
            }
        )
    }
}


/**
 * 关于应用：版本、名称、隐私/免责/支持/更新日志四个入口。
 *
 * ★2026-11-11 从 SettingsScreen.kt 拆出。无状态：点哪个由调用方决定（回调）。
 *   原来这里直接写 `showPrivacy = true; Trace.step(...)`，把「点了要干什么」
 *   和「长什么样」焊在一起，搬出来才发现这四行逻辑在别处也要用。
 */
@Composable
internal fun AboutSection(
    /** 最早一条记录的时间（"yyyy-MM-ddTHH:mm:ss"），null = 还没记录 → 不显示年资 */
    firstRecordAt: String?,
    onChangelog: () -> Unit,
    onPrivacy: () -> Unit,
    onDisclaimer: () -> Unit,
    onSupport: () -> Unit
) {
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
                            .safeClickable { onPrivacy() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("隐私政策", fontSize = 13.sp, color = Ink) }
                    Box(
                        Modifier.liquidGlassChip(corner = 12, flat = true)
                            .safeClickable { onDisclaimer() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("免责声明", fontSize = 13.sp, color = Ink) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.liquidGlassChip(corner = 12, flat = true)
                            .safeClickable { onSupport() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("支持作者", fontSize = 13.sp, color = Ink) }
                    Box(
                        Modifier.liquidGlassChip(corner = 12, flat = true)
                            .safeClickable { onChangelog() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("更新日志", fontSize = 13.sp, color = Ink) }
                }
                // ★★2026-11-04 用户：「关于应用里的 github 和官方网站先删掉」
                //   这两个外链先撤掉（页面内容和版本未必同步，指向外面容易误导）。
                //   函数 AboutLink / openUrl 保留，以后要加回来直接调即可。
            }
        }
}


/**
 * 杂项：照片占用 / 每页条数 / 帧率 / 震动 / 安装包校验 / 通知权限 / 检测更新。
 *
 * ★2026-11-11 从 SettingsScreen.kt 拆出。
 *   `checkingUpdate` **内聚到这里**：它只被「检测更新」这一行读写，
 *   留在父级毫无意义，还会让父级多背一个状态。
 *   其余跨组件的东西走参数：只读值直接给，要改的给回调。
 */
@Composable
internal fun MiscSection(
    photoUsageDesc: String,
    pageSizePref: Int,
    notifLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onOpenPhotoUsage: () -> Unit,
    onOpenPageSize: () -> Unit,
    onToast: (String) -> Unit,
    onUpdateFound: (com.xixi.hiking.nativeapp.data.Updater.Release) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var checkingUpdate by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
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
            onClick = { onOpenPhotoUsage(); com.xixi.hiking.nativeapp.data.Trace.step("点击:照片占用") }
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
            onClick = { onOpenPageSize() }
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
                    onToast(
                        when (ir.state) {
                            com.xixi.hiking.nativeapp.data.Integrity.State.OK ->
                                "签名一致，是官方包"
                            com.xixi.hiking.nativeapp.data.Integrity.State.MODIFIED ->
                                "签名不一致，请重装"
                            com.xixi.hiking.nativeapp.data.Integrity.State.UNKNOWN ->
                                "这台设备读不到签名"
                        }
                    )
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
                    onToast("正在下载，稍等")
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
                            onUpdateFound(res.release)
                        }
                        com.xixi.hiking.nativeapp.data.Updater.Result.Offline -> {
                            // 离线不是错误：中性一句话，别说"失败"
                            onToast("离线，稍后再试")
                        }
                        com.xixi.hiking.nativeapp.data.Updater.Result.UpToDate -> {
                            onToast("已是最新版本 v${com.xixi.hiking.nativeapp.BuildConfig.VERSION_NAME}")
                        }
                        com.xixi.hiking.nativeapp.data.Updater.Result.Failed -> {
                            onToast("检查失败，稍后再试")
                        }
                    }
                    checkingUpdate = false
                }
            }
        )
    }
}


/**
 * 数据管理：WebDAV 账号卡、云备份、上传/下载、导出/导入、自动同步。
 *
 * ★2026-11-11 从 SettingsScreen.kt 拆出。
 *   `syncBusy` / `autoSync` **内聚到这里**（只在组内读写）。
 *   弹窗状态走 setter（onShowSync 等）：因为 `showSync` 既被用来**打开**弹窗，
 *   也被用来**关闭**（解绑后置 false）—— 只传 true 的回调会丢掉关闭语义。
 */
@Composable
internal fun DataSection(
    dav: com.xixi.hiking.nativeapp.data.WebDavClient.Config,
    onShowSync: (Boolean) -> Unit,
    onShowCloud: (Boolean) -> Unit,
    onShowExport: (Boolean) -> Unit,
    onShowImport: (Boolean) -> Unit,
    onShowSyncStatus: (Boolean) -> Unit,
    onRefreshDav: () -> Unit,
    onToast: (String) -> Unit,
    onShowDataInfo: (Boolean) -> Unit,
    /** 导出前设置两个勾选（是否带网盘配置 / 是否带密码） */
    onExportOptions: (cfg: Boolean, pwd: Boolean) -> Unit,
    /** 抹除流程的步骤（0 = 关闭，1 = 首次确认） */
    onWipeStep: (Int) -> Unit,
    /** 让这一行跟着同步结果重组（值变化 = 重新读「上次同步」） */
    syncTick: Int,
    /** 同步完成后通知父级刷新 */
    onSyncTick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = LocalIsDark.current
    var syncBusy by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    var autoSync by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            com.xixi.hiking.nativeapp.data.Prefs.getBool(
                context, com.xixi.hiking.nativeapp.data.Prefs.KEY_AUTO_SYNC, false
            )
        )
    }

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
                    .safeClickable { com.xixi.hiking.nativeapp.data.Trace.step("点击:数据管理说明"); onShowDataInfo(true) }
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
                onShowImport(true)
            }
            GridGlassButton(
                icon = Icons.Filled.Upload, label = "导出数据", cols = 2,
                modifier = Modifier.weight(1f)
            ) {
                // ★2026-11-04 对齐 Web：先弹「导出备份」小窗，三选一 + 网盘配置开关
                onExportOptions(true, false)
                onShowExport(true)
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
                .safeClickable { com.xixi.hiking.nativeapp.data.Trace.step("点击:抹掉所有足迹"); onWipeStep(1) }
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
                if (!dav.configured) { onToast("先绑定账号") ; return@GridGlassButton }
                if (!com.xixi.hiking.nativeapp.data.Net.isOnline(context)) {
                    onToast("离线，稍后再试") ; return@GridGlassButton
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
                        onSyncTick()
                        onToast(if (ok) "已上传：$name" else "上传失败：$name")
                    }
                }
            }
            GridGlassButton(
                icon = Icons.Filled.FolderOpen, label = "云端文件",
                cols = 2, modifier = Modifier.weight(1f), enabled = !syncBusy
            ) {
                if (!dav.configured) { onToast("先绑定账号") ; return@GridGlassButton }
                if (!com.xixi.hiking.nativeapp.data.Net.isOnline(context)) {
                    onToast("离线，稍后再试") ; return@GridGlassButton
                }
                // ★对齐 Web 版「管理云端备份」：列目录 → 下载 / 删除
                onShowCloud(true)
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
                            .safeClickable { com.xixi.hiking.nativeapp.data.Trace.step("点击:绑定账号"); onShowSync(true) }
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
                            .safeClickable { com.xixi.hiking.nativeapp.data.Trace.step("点击:绑定账号"); onShowSync(true) }
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
                                onRefreshDav()
                                onToast("已解绑")
                                onShowSync(false)
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
                    onToast(if (on) "已开启自动同步" else "已关闭自动同步")
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
                    if (dav.configured) onShowSyncStatus(true) else onShowSync(true)
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
}
