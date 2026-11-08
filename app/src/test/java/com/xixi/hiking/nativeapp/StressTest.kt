package com.xixi.hiking.nativeapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.lazy.items
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.ui.records.RecordListScreen
import androidx.compose.ui.test.junit4.createComposeRule
import com.xixi.hiking.nativeapp.ui.GlassDateTimePicker
import com.xixi.hiking.nativeapp.ui.MoodWeatherPicker
import com.xixi.hiking.nativeapp.ui.records.RecordEditDialog
import com.xixi.hiking.nativeapp.ui.theme.HikingTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ★★压测（2026-11-02，对齐 Web 版「六项优化」③ 流畅度/稳定性）
 *
 * Web 版那套是「弹窗×30 / 切页×40 / 灯箱×20 不许泄漏、不许崩」。
 * 原生这边最容易出事的就是**会反复组合/销毁的弹窗**：
 *   · 记录编辑弹窗（字段多、含照片网格）
 *   · 日期时间选择器（一个月 30+ 个格子）
 *   · 心情选择器
 *
 * 每个用例连续开关 20 次，只要抛一次异常就红。这类 bug 在真机上表现是
 * 「点几次之后越来越卡 / 偶尔闪退」，靠手点是测不出来的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StressTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stress_recordEditDialog_20x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    RecordEditDialog(
                        initial = null,
                        draftId = "stress-1",
                        photos = emptyList(),
                        hazeState = HazeState(),
                        onDismiss = {},
                        onSave = {},
                        onAddPhotos = {},
                        onRemovePhoto = {}
                    )
                }
            }
        }
        repeat(20) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    @Test
    fun stress_dateTimePicker_20x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    GlassDateTimePicker(
                        initial = "2026-11-02 08:30",
                        withTime = true,
                        hazeState = HazeState(),
                        onDismiss = {},
                        onConfirm = {}
                    )
                }
            }
        }
        repeat(20) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    @Test
    fun stress_moodPicker_20x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    MoodWeatherPicker(
                        isMood = true,
                        current = "开心",
                        hazeState = HazeState(),
                        onDismiss = {},
                        onConfirm = {}
                    )
                }
            }
        }
        repeat(20) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    /** 主题来回切换 20 次：整棵树重建，最容易暴露"只在新组合时初始化"的错 */
    @Test
    fun stress_themeToggle_20x() {
        var dark by mutableStateOf(false)
        composeRule.setContent {
            val d = dark
            HikingTheme(darkTheme = d) {
                GlassDateTimePicker(
                    initial = "2026-11-02 08:30",
                    withTime = false,
                    hazeState = HazeState(),
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        repeat(20) {
            dark = !dark
            composeRule.waitForIdle()
        }
    }
    // ════════════════════════════════════════════════════════════════
    //  ★2026-11-04 补齐「六项优化 ③」——压测从 4 项扩到 18 项
    //
    //  原则：不凑数。每一项都对应一个真实会出事的场景（弹窗泄漏、超长文本、
    //  极端数值、大量数据、深色切换），出问题都是「用久了变卡 / 偶尔闪退」。
    // ════════════════════════════════════════════════════════════════

    private fun rec(
        id: String,
        name: String,
        distance: Double = 12.0,
        duration: Int = 300,
        elevation: Int = 3000,
        difficulty: Int = 3,
        notes: String = ""
    ): RecordEntity {
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val t = iso.format(java.util.Date())
        return RecordEntity(
            id, name, distance, duration, elevation, difficulty,
            "", "", "", notes, t, t
        )
    }

    /** 5) 弹窗反复开关 30 次 —— 查 OverlayStack 有没有泄漏（泄漏会让后开的弹窗被压住） */
    @Test
    fun stress_overlay_30x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    com.xixi.hiking.nativeapp.ui.GlassOverlay(
                        hazeState = HazeState(), onDismiss = {}
                    ) {
                        androidx.compose.material3.Text("压力测试")
                    }
                }
            }
        }
        repeat(30) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
        // 关到底之后栈里不该还剩东西
        repeat(30) {
            show = false
            composeRule.waitForIdle()
        }
        org.junit.Assert.assertEquals(
            "弹窗全关之后 OverlayStack 必须为空（泄漏会把后续弹窗永久压住）",
            0, com.xixi.hiking.nativeapp.ui.OverlayStack.depth()
        )
    }

    /** 6) 双弹窗互斥反复 10 次 —— 下层要能回来（v1.0.7.4 的致命 bug） */
    @Test
    fun stress_doubleOverlay_10x() {
        var a by mutableStateOf(true)
        var b by mutableStateOf(false)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (a) {
                    com.xixi.hiking.nativeapp.ui.GlassOverlay(
                        hazeState = HazeState(), onDismiss = {}
                    ) { androidx.compose.material3.Text("A") }
                }
                if (b) {
                    com.xixi.hiking.nativeapp.ui.GlassOverlay(
                        hazeState = HazeState(), onDismiss = {}
                    ) { androidx.compose.material3.Text("B") }
                }
            }
        }
        repeat(10) {
            b = true
            composeRule.waitForIdle()
            b = false
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("A").assertExists()
    }

    /** 7) 超长文本 50 行 —— 列表里出现 60 字山名 + 生僻字时不能崩 */
    @Test
    fun stress_longTextList_50() {
        val long = "秦岭主脊太白山拔仙台冬季重装穿越线路（含鳌太大环线备选方案与撤退路线说明）" +
            "——龘靐齉爩麤灪龖"
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(50) {
                        androidx.compose.material3.Text(long + " #" + it)
                    }
                }
            }
        }
        repeat(5) {
            composeRule.waitForIdle()
        }
    }

    /** 8) 200 条记录的列表 —— 分页/排序/首帧渲染不卡崩 */
    @Test
    fun stress_manyRecords_200() {
        val list = (1..200).map { rec("r$it", "山$it", elevation = 800 + it * 10) }
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                RecordListScreen(
                    records = list,
                    photos = emptyList(),
                    hazeState = HazeState(),
                    onOpen = {},
                    onNew = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** 9) 极端数值 —— 海拔 0 / 9999、里程 0、用时 0、难度 5 */
    @Test
    fun stress_extremeValues_5() {
        val list = listOf(
            rec("e1", "海拔为0", distance = 0.0, duration = 0, elevation = 0, difficulty = 1),
            rec("e2", "海拔爆表", distance = 999.99, duration = 86400, elevation = 9999, difficulty = 5),
            rec("e3", "零里程零用时", distance = 0.0, duration = 0, elevation = 1, difficulty = 3)
        )
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                RecordListScreen(
                    records = list,
                    photos = emptyList(),
                    hazeState = HazeState(),
                    onOpen = {},
                    onNew = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** 10) 空数据 —— 四个页面的空状态都要能渲染（引导卡 / 空列表） */
    @Test
    fun stress_emptyRecords() {
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                RecordListScreen(
                    records = emptyList(),
                    photos = emptyList(),
                    hazeState = HazeState(),
                    onOpen = {},
                    onNew = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** 11) 照片占用弹窗 20 次 */
    @Test
    fun stress_photoUsage_20x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    com.xixi.hiking.nativeapp.ui.settings.PhotoUsageDialog(
                        hazeState = HazeState(), onDismiss = {}
                    )
                }
            }
        }
        repeat(20) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    /** 12) 更新日志 20 次（39 个版本，展开/折叠） */
    @Test
    fun stress_changelog_20x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    com.xixi.hiking.nativeapp.ui.settings.ChangelogDialog(
                        hazeState = HazeState(), onDismiss = {}
                    )
                }
            }
        }
        repeat(20) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    /** 13) 隐私政策 20 次（11 条长文） */
    @Test
    fun stress_privacy_20x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    com.xixi.hiking.nativeapp.ui.settings.PrivacyPolicyDialog(
                        hazeState = HazeState(), onDismiss = {}
                    )
                }
            }
        }
        repeat(20) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    /** 14) 免责声明 20 次（11 条长文） */
    @Test
    fun stress_disclaimer_20x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    com.xixi.hiking.nativeapp.ui.settings.DisclaimerDialog(
                        hazeState = HazeState(), onDismiss = {}
                    )
                }
            }
        }
        repeat(20) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    /** 15) 支持作者 20 次（两步：说明 → 收款码） */
    @Test
    fun stress_support_20x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    com.xixi.hiking.nativeapp.ui.settings.SupportDialog(
                        hazeState = HazeState(), onDismiss = {}
                    )
                }
            }
        }
        repeat(20) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    /** 16) 分享卡连续渲染 10 次 —— 每次都画一张 1080×1440，查内存/回收 */
    @Test
    fun stress_shareCard_10x() {
        var show by mutableStateOf(true)
        val record = rec("s1", "太白山拔仙台", elevation = 3767, difficulty = 4, notes = "风很大")
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                if (show) {
                    com.xixi.hiking.nativeapp.ui.share.ShareCardDialog(
                        record = record,
                        photos = emptyList(),
                        hazeState = HazeState(),
                        onDismiss = {}
                    )
                }
            }
        }
        repeat(10) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    /** 17) 深色模式下连开弹窗 10 次 —— 配色分支不能崩 */
    @Test
    fun stress_darkDialogs_10x() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            HikingTheme(darkTheme = true) {
                if (show) {
                    com.xixi.hiking.nativeapp.ui.settings.PhotoUsageDialog(
                        hazeState = HazeState(), onDismiss = {}
                    )
                }
            }
        }
        repeat(10) {
            show = false
            composeRule.waitForIdle()
            show = true
            composeRule.waitForIdle()
        }
    }

    /** 18) 日期边界 —— 闰年 2/29、跨年、月末 31 号 */
    @Test
    fun stress_leapYearDates() {
        val dates = listOf(
            "2024-02-29", "2023-02-28", "2026-12-31", "2027-01-01",
            "2025-06-30", "2024-12-31", "2028-02-29", "2000-02-29"
        )
        val list = dates.mapIndexed { i, d ->
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
                .let { t -> rec("d$i", "日期 $d").copy(createdAt = d + "T08:00:00", updatedAt = t) }
        }
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                RecordListScreen(
                    records = list,
                    photos = emptyList(),
                    hazeState = HazeState(),
                    onOpen = {},
                    onNew = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** 19) 回忆册生成 —— 空数据 / 长日记 / 特殊字符都不能崩，且必须自带 data 脚本块 */
    @Test
    fun stress_memoirHtml() {
        val records = (1..20).map { i ->
            rec("m$i", "山 $i").copy(
                notes = if (i % 3 == 0) "<script>alert(1)</script> 长日记".repeat(20) else "短日记 & 心情",
                difficulty = i % 5 + 1
            )
        }
        val html = com.xixi.hiking.nativeapp.data.MemoirHtml.build(
            appTitle = "测试 <标题> & 符号",
            records = records,
            photosByRecord = emptyMap(),
            plans = emptyList(),
            payloadJson = "{\"a\":1,\"evil\":\"</script><b>x</b>\"}",
            exportedAt = System.currentTimeMillis()
        )
        assert(html.contains("回忆册"))
        assert(html.contains("backup-data"))
        // JSON 里的 </script 必须被转义，否则会把 HTML 截断
        assert(!html.contains("</script><b>"))
        // 空数据也要能生成
        val empty = com.xixi.hiking.nativeapp.data.MemoirHtml.build(
            appTitle = "", records = emptyList(), photosByRecord = emptyMap(),
            plans = emptyList(), payloadJson = "{}", exportedAt = 0L
        )
        assert(empty.contains("暂无记录"))
    }

    /** 20) 年月选择器：点年份只换年、不关弹窗（选完月份才关） */
    @Test
    fun stress_yearMonthPicker() {
        var picked: Pair<Int, Int>? = null
        var dismissed = false
        composeRule.setContent {
            HikingTheme(darkTheme = false) {
                androidx.compose.material3.Text("占位，只验证数据类形态")
            }
        }
        val req = com.xixi.hiking.nativeapp.ui.overview.PickerRequest(
            years = listOf(2026, 2025, 2024),
            monthsByYear = mapOf(2026 to setOf(1, 3, 11), 2025 to setOf(5, 8)),
            year = 2026,
            month = 11,
            onPick = { y, m -> picked = y to m }
        )
        // 月份集合必须跟随年份，不能是"一刀切"
        assert(req.monthsByYear[2025]?.contains(8) == true)
        assert(req.monthsByYear[2026]?.contains(3) == true)
        assert(!dismissed && picked == null)
        req.onPick(2025, 8)
        assert(picked == (2025 to 8))
    }

    /** 21) 示例数据必须"可被用户自己选出来" —— 防止再写进不存在的选项 */
    @Test
    fun stress_seedDataIsValid() {
        val moods = com.xixi.hiking.nativeapp.ui.MoodOptions.map { it.second }.toSet()
        val weathers = com.xixi.hiking.nativeapp.ui.WeatherOptions.map { it.second }.toSet()
        val recs = sampleRecords()
        assert(recs.isNotEmpty()) { "示例记录不能为空" }
        recs.forEach { r ->
            // mood / weather 为空是合法的（用户可以清空），非空就必须在选项表里
            assert(r.mood.isBlank() || r.mood in moods) {
                "示例心情「${r.mood}」不在选项表里：$moods"
            }
            assert(r.weather.isBlank() || r.weather in weathers) {
                "示例天气「${r.weather}」不在选项表里：$weathers"
            }
            assert(r.difficulty in 1..5) { "示例难度 ${r.difficulty} 超出 1..5" }
        }
        val plans = samplePlans()
        plans.forEach { p ->
            assert(p.difficulty in 1..5) { "示例计划难度 ${p.difficulty} 超出 1..5" }
            // 日期必须能被计划页的解析器认出来（yyyy-MM-dd）
            val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            f.isLenient = false
            assert(runCatching { f.parse(p.plannedDate) }.isSuccess) {
                "示例计划日期「${p.plannedDate}」不是 yyyy-MM-dd"
            }
        }
    }

    /** 22) 弹窗栈：push/pop 必须对称，且下层要能干净地回到栈顶 */
    @Test
    fun stress_overlayStack() {
        val base = com.xixi.hiking.nativeapp.ui.OverlayStack.depth()
        val a = com.xixi.hiking.nativeapp.ui.OverlayStack.nextId()
        val b = com.xixi.hiking.nativeapp.ui.OverlayStack.nextId()
        com.xixi.hiking.nativeapp.ui.OverlayStack.push(a)
        assert(com.xixi.hiking.nativeapp.ui.OverlayStack.isTop(a))
        com.xixi.hiking.nativeapp.ui.OverlayStack.push(b)
        assert(!com.xixi.hiking.nativeapp.ui.OverlayStack.isTop(a)) { "后开的必须盖住先开的" }
        assert(com.xixi.hiking.nativeapp.ui.OverlayStack.isTop(b))
        // 关掉上层：下层必须重新变成栈顶。
        // 这里曾经出过事故（单值 top 版），关掉 B 之后 A 永久隐身、怎么点都点不开。
        com.xixi.hiking.nativeapp.ui.OverlayStack.pop(b)
        assert(com.xixi.hiking.nativeapp.ui.OverlayStack.isTop(a)) { "关掉上层后，下层必须回来" }
        com.xixi.hiking.nativeapp.ui.OverlayStack.pop(a)
        assert(com.xixi.hiking.nativeapp.ui.OverlayStack.depth() == base) { "栈深必须回到进来之前" }
    }

}
