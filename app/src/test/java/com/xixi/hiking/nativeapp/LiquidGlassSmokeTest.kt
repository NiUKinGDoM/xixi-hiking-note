package com.xixi.hiking.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xixi.hiking.nativeapp.ui.liquid.LiquidBackdropHost
import com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass
import com.xixi.hiking.nativeapp.ui.liquid.LocalLiquidBackdrop
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassSurface
import com.xixi.hiking.nativeapp.ui.theme.HikingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ★无设备自测：在 JVM 上真跑一遍「液态玻璃」的完整结构。
 *
 * 目的不是像素级校验，而是保证两件致命的事不出错：
 *   ① 组合 / 测量 / 绘制三个阶段都不抛异常（抛了在真机上就是白屏或闪退）
 *   ② 内容文字确实被画出来了（`assertIsDisplayed` 走的是语义树，能证明节点真实布局并可见）
 *
 * 教训（2026-11-01 白屏事故）：上一版把 `drawContent()` 塞进 `record` 的 lambda 里，
 * 录完直接 return —— 一旦 layer 是空的，屏幕上什么都不会画。这个测试就是防止这类回归。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiquidGlassSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backdropHost_drawsContent_andDoesNotCrash() {
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                // ★backdrop 库的 LayerBackdrop 必须由 @Composable 创建（rememberLayerBackdrop）
                val backdrop = rememberLayerBackdrop()
                CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
                    Box(Modifier.fillMaxSize()) {
                        // 内容层（被录成纹理的那层）
                        LiquidBackdropHost(state = backdrop, modifier = Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().background(Color.White)) {
                                Text("内容层文字")
                            }
                        }
                        // 浮层（采样内容层做折射）
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .liquidGlassSurface(
                                    backdrop = backdrop,
                                    corner = 26,
                                    refract = 11f
                                )
                                .padding(20.dp)
                        ) {
                            Text("底栏文字")
                        }
                    }
                }
            }
        }

        // ① 不抛异常已经由上面 setContent 走到绘制证明（Compose 测试会执行 measure/layout/draw）
        // ② 两层文字都要真实可见 —— 内容层被吞掉就是这次白屏事故的特征
        composeRule.onNodeWithText("内容层文字").assertIsDisplayed()
        composeRule.onNodeWithText("底栏文字").assertIsDisplayed()
    }

    @Test
    fun glassSurface_worksWithoutBackdrop() {
        // 背景纹理还没准备好（或低版本不支持）时，玻璃表面必须自己画出来，不能留空白
        composeRule.setContent {
            HikingTheme(darkTheme = true) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .liquidGlassSurface(backdrop = null, corner = 24)
                        .padding(24.dp)
                ) {
                    Text("无背景时的玻璃")
                }
            }
        }
        composeRule.onNodeWithText("无背景时的玻璃").assertIsDisplayed()
    }

    @Test
    fun shaderFlag_isBooleanAndStable() {
        // 仅确保取这个值不会崩（API 33 的 Robolectric 通常不支持 AGSL，会返回 false，
        // 那就走静态近似路径 —— 真机 API 33+ 才会用真折射）
        val a = LiquidGlass.shaderSupported
        val b = LiquidGlass.shaderSupported
        assert(a == b) { "shaderSupported 不应每次变化" }
    }
}
