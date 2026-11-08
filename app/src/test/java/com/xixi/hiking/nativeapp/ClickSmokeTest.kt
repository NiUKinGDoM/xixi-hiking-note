package com.xixi.hiking.nativeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xixi.hiking.nativeapp.ui.settings.SettingsScreen
import com.xixi.hiking.nativeapp.ui.theme.HikingTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ★★设置页全量点击测试（2026-11-02）
 *
 * 用户报「点击设置页所有按钮都闪退」。之前只测 5 个是不够的 ——
 * 这里把设置页里**每一个可点元素都点一遍**（跳过会拉系统窗口/联网的：
 * 导入、导出、导出诊断、检查更新）。
 *
 * 每个用例独立 setContent，互不干扰（点开弹窗会改变树结构）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClickSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun openSettings() {
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        recordCount = 1,
                        planCount = 1,
                        onImported = {},
                        onThemeChange = {},
                        hazeState = HazeState()
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    // ── 数据管理分组 ──────────────────────────────────────
    @Test
    fun click_dataInfoIcon() {
        openSettings()
        composeRule.onNodeWithContentDescription("数据管理说明").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_wipeAll() {
        openSettings()
        composeRule.onNodeWithText("抹掉所有足迹").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_upload() {
        openSettings()
        composeRule.onNodeWithText("上传").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun downloadButtonIsGone() {
        // ★2026-11-05 用户：「下载和云端文件是不是功能一样，把下载删掉吧」
        //   删掉之后这里改成**反向断言**：设置页不该再有「下载」这一格。
        //   留着的价值是防止以后又手滑加回一个和「云端文件」重复的按钮。
        openSettings()
        composeRule.onAllNodesWithText("下载", useUnmergedTree = true)
            .assertCountEquals(0)
        // 而「云端文件」必须在 —— 否则说明我删错了对象
        composeRule.onNodeWithText("云端文件").assertExists()
    }

    @Test
    fun click_backupGrid() {
        openSettings()
        // ★2026-11-03 对齐 Web 版：第三个按钮从「备份（本地）」换成「云端文件」
        composeRule.onNodeWithText("云端文件").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_bindAccount() {
        openSettings()
        composeRule.onNodeWithText("绑定账号").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_autoSyncSwitch() {
        openSettings()
        composeRule.onNodeWithTag("sw_auto").performClick()
        composeRule.waitForIdle()
        // 再点回去，验证状态来回切换不炸
        composeRule.onNodeWithTag("sw_auto").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_syncHealthRow() {
        openSettings()
        composeRule.onNodeWithText("未配置 · 点这里绑定").performClick()
        composeRule.waitForIdle()
    }

    // ── 外观分组 ──────────────────────────────────────────
    @Test
    fun click_followSystemSwitch() {
        openSettings()
        composeRule.onNodeWithTag("sw_follow").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_darkModeSwitch() {
        openSettings()
        composeRule.onNodeWithTag("sw_dark").performClick()
        composeRule.waitForIdle()
    }

    // ── 杂项分组 ──────────────────────────────────────────
    @Test
    fun click_photoUsage() {
        openSettings()
        composeRule.onNodeWithText("照片占用").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_localBackup() {
        // ★2026-11-03：「本地备份」行已按 1:1 对齐要求移除（Web 版没有这一项）。
        //   这一条冒烟改成测同一分组里的「震动反馈」开关。
        openSettings()
        composeRule.onNodeWithText("震动反馈").performClick()
        composeRule.waitForIdle()
    }

    // ── 关于分组：四个玻璃按钮 ────────────────────────────
    @Test
    fun click_privacy() {
        openSettings()
        composeRule.onNodeWithText("隐私政策").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_disclaimer() {
        openSettings()
        composeRule.onNodeWithText("免责声明").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_support() {
        openSettings()
        composeRule.onNodeWithText("支持作者").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun click_changelog() {
        openSettings()
        composeRule.onNodeWithText("更新日志").performClick()
        composeRule.waitForIdle()
    }
}
