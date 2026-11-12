package com.xixi.hiking.nativeapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ══════════════════════════════════════════════════════════════
 *  跨页交互测试（2026-11-12）
 *
 *  ── 为什么要有这一层 ──
 *  之前的测试是「单页 + 单点」：`ClickSmokeTest` 点设置页每个按钮有没有崩、
 *  `UiSnapshotTest` 看每页画得对不对。但真实使用是**跨页的**：
 *
 *    计划页点「完成」→ 转成记录 → 回概览统计要跟着涨
 *
 *  这类"数据在一个页面改了、另一个页面要同步"的链路，之前**一条都没有测**。
 *  而它恰好是最容易坏的地方 —— 改个回调签名、漏传一个刷新参数，
 *  单页测试全绿，用起来就是"点了没反应 / 数字不变"。
 *
 *  ── 为什么能这么测 ──
 *  `MainActivity` 能在 Robolectric 里正常起来（探针验证过），
 *  而且首次启动会灌 sample 数据，所以有真数据可点、可断言。
 *
 *  ── 写法约定 ──
 *  · 用真实 Activity：`createAndroidComposeRule<MainActivity>()`
 *  · 切页用底栏文字（"概览"/"计划"/"记录"/"设置"）
 *  · 断言尽量用**可见文案**而不是内部状态 —— 用户看到什么就断言什么
 *  · 有异步（Room Flow）的地方用 `waitUntil`
 * ══════════════════════════════════════════════════════════════
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrossPageFlowTest {

    /**
     * ★★order = 0：这个规则必须跑在 Activity 启动**之前**。
     *
     * 踩过的坑：写在 @Before 里没用 —— @Rule 比 @Before 早，
     * Activity 那时已经起来了、数据库连接已经打开，再删库/改标记都来不及。
     *
     * 要清两样：
     *  ① 数据库文件 —— 全量跑时前面的测试类会把记录删光/改脏
     *  ② KEY_SEEDED —— 「示例数据已灌过」的标记在 SharedPreferences 里，
     *     而 SharedPreferences 是**同一个测试 run 内共享**的（数据库却按测试重建）。
     *     不清就会拿到「空库 + 已灌过=true」，示例数据永远不灌。
     */
    @get:Rule(order = 0)
    val cleanState = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val ctx = androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
            com.xixi.hiking.nativeapp.data.Prefs.setString(
                ctx,
                com.xixi.hiking.nativeapp.data.Prefs.KEY_LEGAL_AGREED,
                com.xixi.hiking.nativeapp.ui.LEGAL_VERSION
            )
            com.xixi.hiking.nativeapp.data.Prefs.setBool(
                ctx, com.xixi.hiking.nativeapp.data.Prefs.KEY_SEEDED, false
            )
            ctx.deleteDatabase("hiking.db")

            // ★★同步灌固定数据，不依赖「示例数据自动灌入」。
            //   后者是 Activity 里协程异步做的：全量跑时 CPU 忙，
            //   测试可能在写入落库之前就去查列表 → 空 → 假失败。
            //   这里先同步写死 3 条，Activity 启动时 count>0 就不会再灌 sample。
            val db = com.xixi.hiking.nativeapp.data.buildDatabase(ctx)
            kotlinx.coroutines.runBlocking {
                db.recordDao().upsertAll(
                    listOf(
                        com.xixi.hiking.nativeapp.data.RecordEntity(
                            "t1", "太白山拔仙台", 18.5, 540, 3767, 5, "累但值", "多云",
                            "老爸", "跨页测试用", "2026-11-01T08:00:00", "2026-11-01T08:00:00"
                        ),
                        com.xixi.hiking.nativeapp.data.RecordEntity(
                            "t2", "翠华山天池", 8.2, 210, 1600, 3, "开心", "晴",
                            "爸妈", "跨页测试用", "2026-10-20T08:00:00", "2026-10-20T08:00:00"
                        ),
                        com.xixi.hiking.nativeapp.data.RecordEntity(
                            "t3", "华山北峰", 15.5, 480, 1614, 5, "震撼", "晴",
                            "同事", "跨页测试用", "2026-10-10T08:00:00", "2026-10-10T08:00:00"
                        )
                    )
                )
            }
            db.close()
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    /** 点底栏切页。底栏文字在多个位置可能出现，取第一个可点的 */
    private fun go(tab: String) {
        rule.waitForIdle()
        rule.onAllNodesWithText(tab)[0].performClick()
        rule.waitForIdle()
    }

    /**
     * 1. 四个 tab 轮流切两遍，最后回到概览。
     *
     * 这条看起来"什么都没测"，但它挡住的是**最致命的一类崩溃**：
     * 某个页面的初始化代码只在第一次进入时正确，第二次进入就 NPE / 状态错乱。
     * 之前所有测试都是「每个页面单独 setContent」，**从来没有人切过页**。
     */
    @Test
    fun switchingTabsRepeatedlyDoesNotCrash() {
        listOf("记录", "计划", "设置", "概览", "记录", "概览").forEach { go(it) }
        // 回到概览，统计主卡应该在
        rule.onNodeWithText("总里程", substring = true).assertIsDisplayed()
    }

    /**
     * 2. 进记录页 → 切走 → 再回来，列表还在（不是空白）。
     *
     * 挡的是"切页后数据没重新订阅"——表现是列表空白、要手动下拉才有。
     */
    @Test
    fun recordsSurviveTabSwitch() {
        go("记录")
        val before = rule.onAllNodesWithText("太白山", substring = true).fetchSemanticsNodes().size
        go("设置")
        go("记录")
        val after = rule.onAllNodesWithText("太白山", substring = true).fetchSemanticsNodes().size
        org.junit.Assert.assertEquals("切页后记录条目数不该变", before, after)
        org.junit.Assert.assertTrue("示例数据里应该有太白山这条", after > 0)
    }


    /**
     * 3. 计划页切到日历视图 → 切去记录页 → 切回来，仍是日历。
     *
     * 这条不需要任何数据，纯 UI 状态跨页保持。
     * 挡的是「页面视图状态没提升到父级，切走就重置」——
     * 用户视角就是「我刚切到日历，去看了眼记录，回来又变成列表了」。
     */
    @Test
    fun plansViewModeSurvivesTabSwitch() {
        go("计划")
        // 切到日历视图（按钮文案随实现可能是「日历」）
        rule.onAllNodesWithText("日历", substring = true)[0].performClick()
        rule.waitForIdle()
        // 记下此刻界面上有日历特征：星期几的短名
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        val before = weekdays.sumOf { d ->
            rule.onAllNodesWithText(d).fetchSemanticsNodes().size
        }
        go("记录")
        go("计划")
        val after = weekdays.sumOf { d ->
            rule.onAllNodesWithText(d).fetchSemanticsNodes().size
        }
        org.junit.Assert.assertEquals("切页后日历视图不该被重置回列表", before, after)
    }
}
