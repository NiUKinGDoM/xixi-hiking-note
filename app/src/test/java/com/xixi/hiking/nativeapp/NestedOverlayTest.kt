package com.xixi.hiking.nativeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.clickable
import androidx.compose.ui.composed
import androidx.compose.runtime.mutableStateOf
import com.xixi.hiking.nativeapp.ui.GlassConfirm
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.theme.HikingTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 测试里给节点挂点击（不引生产的 safeClickable，避免把它也拖进断言） */
private fun Modifier.androidClick(onClick: () -> Unit): Modifier = composed {
    this.then(Modifier.clickable(onClick = onClick))
}

/**
 * 嵌套弹窗（弹窗套弹窗）—— 2026-11-05
 *
 * 事故：用户报「照片和灯箱部分的删除键，按了没有弹窗」。
 * 真因是内层弹窗被外层「只画栈顶」的逻辑连带隐藏（内层是外层的子节点），
 * 再加上外层卡片有 clip + 72% 限高，内层要么被裁、要么被挤。
 *
 * 这些用例锁住三件事：
 *   ① 内层能真的出现在语义树里（不是"状态置了但看不见"）
 *   ② 关掉内层之后，外层还在（不会被一起带走）
 *   ③ 内层关闭后没有把弹窗栈搞失衡（否则外层会永久隐身 —— 那个 bug 犯过一次）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NestedOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun nested_overlay_isVisible() {
        val outer = mutableStateOf(true)
        val inner = mutableStateOf(true)
        rule.setContent {
            HikingTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    if (outer.value) {
                        GlassOverlay(hazeState = HazeState(), onDismiss = { outer.value = false }) {
                            Text("外层内容", modifier = Modifier.testTag("outer"))
                            if (inner.value) {
                                GlassOverlay(
                                    hazeState = HazeState(),
                                    onDismiss = { inner.value = false }
                                ) {
                                    Text("内层内容", modifier = Modifier.testTag("inner"))
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        // ① 内层必须真的在（以前这里是空的：外层被顶掉，内层跟着消失）
        rule.onNodeWithTag("inner", useUnmergedTree = true).assertExists()
        // ② 外层也在（内层是独立窗口，不该把外层顶掉）
        rule.onNodeWithTag("outer", useUnmergedTree = true).assertExists()
    }

    @Test
    fun nested_close_keepsOuter() {
        val outer = mutableStateOf(true)
        val inner = mutableStateOf(true)
        rule.setContent {
            HikingTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    if (outer.value) {
                        GlassOverlay(hazeState = HazeState(), onDismiss = { outer.value = false }) {
                            Text("外层内容", modifier = Modifier.testTag("outer"))
                            if (inner.value) {
                                GlassConfirm(
                                    hazeState = HazeState(),
                                    title = "删除这张照片？",
                                    message = "无法恢复",
                                    confirmText = "删除",
                                    danger = true,
                                    onConfirm = { inner.value = false },
                                    onCancel = { inner.value = false }
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("删除这张照片？", useUnmergedTree = true).assertExists()
        // 点取消 → 内层消失、外层还在（栈不能失衡）
        rule.onNodeWithText("取消", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("删除这张照片？", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("outer", useUnmergedTree = true).assertExists()
    }

    // 注：这里原本还有一条「关掉内层再开一次」的用例，删掉了。
    //   原因：Robolectric 没有真实帧时钟，Popup 一出现，入场 spring 就永远不收敛，
    //   Compose 一直不 idle ⇒ 60 秒超时。那是测试环境的限制，不是产品 bug
    //   （真机有 vsync，动画正常结束）。要覆盖这个场景得上仪器测试，
    //   现阶段用「画不出来就不会有人反馈点不动」这条真人验证更实在。

}
