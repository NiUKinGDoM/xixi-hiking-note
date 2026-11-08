package com.xixi.hiking.nativeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.xixi.hiking.nativeapp.ui.AppTab
import com.xixi.hiking.nativeapp.ui.GlassBottomBar
import com.xixi.hiking.nativeapp.ui.theme.HikingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 底栏拖动的**行为**回归（2026-11-06）。
 *
 * 复现的是用户报的：「长按挪到概览的时候，会自动跳回去，不会停在概览页」。
 *
 * 根因不在手势，在两条动画抢同一个 Animatable：
 *   松手时 onDragEnd 先写 `dragging = false`、再调 `onSelect(...)`，
 *   而 `LaunchedEffect(current, dragging)` 的 key 里有 `dragging` ——
 *   于是 effect 在 current 还没变之前就重启了一次，把指示块
 *   `animateTo(旧 index)`，顺势取消掉"滑向新格子"的那条动画。
 *
 * 这个测试盯的就是：拖到第一个格子松手后，**必须**切过去且只切一次。
 * 如果以后有人再把动画分两处播放，这里会红。
 */
@RunWith(RobolectricTestRunner::class)
// ★Robolectric 4.13 最高只到 SDK 34，项目 targetSdk 是 36 —— 不锁 sdk 会初始化失败。
//   qualifiers 固定屏幕尺寸，拖动坐标才算得准（跟其它界面测试保持一致）。
@org.robolectric.annotation.Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class BottomBarDragTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val barWidth = 320.dp
    private val barHeight = 60.dp
    /** GlassBottomBar 内部的水平内边距（源码里 pad = 6.dp），算坐标要用 */
    private val innerPad = 6.dp
    private val tabCount = 4

    @Test
    fun `从第三个标签拖到第一个会切过去且不弹回`() {
        var current by mutableStateOf(AppTab.RECORDS)   // index 2
        val picks = mutableListOf<AppTab>()

        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .width(barWidth)
                            .height(barHeight)
                            .testTag("bar")
                    ) {
                        GlassBottomBar(
                            current = current,
                            backdrop = backdrop,
                            onSelect = { current = it; picks += it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("bar").performTouchInput {
            val padPx = innerPad.toPx()
            val tabW = (width - padPx * 2) / tabCount.toFloat()
            val y = height / 2f
            // 按下在「记录」（index 2）的正中
            down(Offset(padPx + tabW * 2.5f, y))
            // 往左拖 3 格（多拖半格，保证 coerceIn 之后稳稳落在 0）
            moveBy(Offset(-tabW * 1.5f, 0f))
            moveBy(Offset(-tabW * 1.5f, 0f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals("松手后必须停在概览", AppTab.OVERVIEW, current)
        assertEquals("只能切一次，不能切过去又切回来", listOf(AppTab.OVERVIEW), picks)
    }

    @Test
    fun `拖一点点就松手不会乱切页`() {
        var current by mutableStateOf(AppTab.RECORDS)
        val picks = mutableListOf<AppTab>()

        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .width(barWidth)
                            .height(barHeight)
                            .testTag("bar")
                    ) {
                        GlassBottomBar(
                            current = current,
                            backdrop = backdrop,
                            onSelect = { current = it; picks += it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("bar").performTouchInput {
            val padPx = innerPad.toPx()
            val tabW = (width - padPx * 2) / tabCount.toFloat()
            down(Offset(padPx + tabW * 2.5f, height / 2f))
            // 只挪 0.2 格 —— 四舍五入还在原来那格
            moveBy(Offset(-tabW * 0.2f, 0f))
            up()
        }
        composeRule.waitForIdle()

        // ★注意：这么小的位移可能根本没超过 touch slop，于是退化成一次普通点击 ——
        //   那也会回调 onSelect(RECORDS)。所以这里只断言"页面没变"，不要求 picks 为空。
        assertEquals("没跨过中线不该切页", AppTab.RECORDS, current)
    }

    /**
     * ★★这一条是为了抓「滑到概览但页面不切」而加的（2026-11-06 第二次）。
     *
     * 旧实现里 `pointerInput(tabW)` 的闭包捕获的是**第一次组合时**的 current。
     * 先点「记录」让 current 变成 RECORDS，闭包里却还是 OVERVIEW，
     * 再拖回概览时 `target != current` 判成 "OVERVIEW != OVERVIEW" = false，
     * 于是只滑胶囊、不调 onSelect —— 页面纹丝不动。
     *
     * 只有"先切过页、再拖回来"这个顺序才会暴露，所以单测顺序很关键。
     */
    @Test
    fun `先点过别的页再拖回首格仍然会切页`() {
        var current by mutableStateOf(AppTab.OVERVIEW)
        val picks = mutableListOf<AppTab>()

        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .width(barWidth)
                            .height(barHeight)
                            .testTag("bar")
                    ) {
                        GlassBottomBar(
                            current = current,
                            backdrop = backdrop,
                            onSelect = { current = it; picks += it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // ① 先点「记录」，把 current 从 OVERVIEW 换成 RECORDS
        composeRule.onNode(hasText("记录")).performClick()
        composeRule.waitForIdle()
        assertEquals("前置条件：应该已经切到记录页", AppTab.RECORDS, current)

        // ② 再从「记录」拖回「概览」
        composeRule.onNodeWithTag("bar").performTouchInput {
            val padPx = innerPad.toPx()
            val tabW = (width - padPx * 2) / tabCount.toFloat()
            down(Offset(padPx + tabW * 2.5f, height / 2f))
            moveBy(Offset(-tabW * 1.5f, 0f))
            moveBy(Offset(-tabW * 1.5f, 0f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals("拖回首格必须真的切页（旧实现会漏）", AppTab.OVERVIEW, current)
    }
}
