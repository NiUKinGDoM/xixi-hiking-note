package com.xixi.hiking.nativeapp.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable

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
