package com.xixi.hiking.nativeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xixi.hiking.nativeapp.data.PhotoEntity
import com.xixi.hiking.nativeapp.ui.photos.PhotoLightbox
import com.xixi.hiking.nativeapp.ui.theme.HikingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 灯箱两段式删除（2026-11-05）
 *
 * 用户提的方案：「删除键就是确认键，第一次点删除，删除键变为确认？，再点一次才删除」。
 * 比弹窗好在：灯箱本身是 Dialog（独立窗口），在它上面再套弹窗要么被挡、要么层级不稳
 * （前两版就卡在这）；两段式不需要第二个窗口，也不打断看图。
 *
 * 这里锁住最关键的一点：**第一下什么都不能删**。
 * 那一瞬间按钮从「删除」变成「确认删除？」—— 如果第一下就删了，等于没有确认。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LightboxDeleteTest {

    @get:Rule
    val rule = createComposeRule()

    private fun photo(id: String) = PhotoEntity(
        id = id, recordId = "r1", fileName = "$id.jpg",
        width = 1200, height = 1600, createdAt = 0L
    )

    @Test
    fun firstTap_onlyArms_secondTap_deletes() {
        var deleted: String? = null
        rule.setContent {
            HikingTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    PhotoLightbox(
                        photos = listOf(photo("a"), photo("b")),
                        startIndex = 0,
                        onDismiss = {},
                        editing = true,
                        onDelete = { p -> deleted = p.id }
                    )
                }
            }
        }
        rule.waitForIdle()

        // 第一下：只是"上膛"，不能删
        rule.onNodeWithText("删除", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assert(deleted == null) { "第一下就删了 —— 那两段式等于没做" }
        // 按钮应变成询问态
        rule.onNodeWithText("确认删除？", useUnmergedTree = true).assertExists()

        // 第二下：真删
        rule.onNodeWithText("确认删除？", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assert(deleted == "a") { "第二下应该删掉当前这张，实际：$deleted" }
    }
}
