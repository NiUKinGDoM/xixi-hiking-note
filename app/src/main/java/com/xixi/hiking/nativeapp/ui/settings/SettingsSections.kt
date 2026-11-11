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
