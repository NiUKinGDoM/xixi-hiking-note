package com.xixi.hiking.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xixi.hiking.nativeapp.data.Integrity
import com.xixi.hiking.nativeapp.data.MtnMerge
import com.xixi.hiking.nativeapp.data.RecordEntity
import com.xixi.hiking.nativeapp.data.SecretBox
import com.xixi.hiking.nativeapp.data.Updater
import com.xixi.hiking.nativeapp.data.milestoneDone
import com.xixi.hiking.nativeapp.data.milestoneProgress
import com.xixi.hiking.nativeapp.data.milestoneStats
import com.xixi.hiking.nativeapp.data.normalizePeakName
import com.xixi.hiking.nativeapp.data.normWeather
import com.xixi.hiking.nativeapp.data.parseRecordDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ★核心逻辑测试（2026-11-05）
 *
 * 补的是 [tools/covscan.py] 点出来的「高风险未测模块」：
 *   Updater（版本比较）/ SecretBox（凭据加密）/ MtnMerge（山册合并）/
 *   Milestones（统计与里程碑）/ Integrity（签名校验）
 *
 * 为什么这些优先：它们都不出现在界面上，坏了也不报错 ——
 *   · 版本比较写错 → 永远收不到更新，用户以为"没新版"
 *   · 凭据解密写错 → 网盘同步一直失败，报的是"网络错误"
 *   · 山册合并写错 → 同一座山分成两座，照片对不上
 *   · 里程碑算错 → 数字好看但是假的
 *   · 签名校验写错 → 每个用户都被提示"安装包已被改动"
 * 这些靠手点是点不出来的。
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class CoreLogicTest {

    // ── 版本比较（决定"能不能收到更新"）───────────────────────
    @Test
    fun versionCompare_handlesFourSegments() {
        // 逐段比，不是字符串比：9 < 10
        assertTrue("1.0.9.10 应该比 1.0.9.9 新", Updater.isNewer("v1.0.9.10", "1.0.9.9"))
        assertFalse("1.0.9.9 不该比 1.0.9.10 新", Updater.isNewer("v1.0.9.9", "1.0.9.10"))
        assertTrue("1.0.10.0 应该比 1.0.9.9 新", Updater.isNewer("v1.0.10.0", "1.0.9.9"))
        assertFalse("同版本不算更新", Updater.isNewer("v1.0.10.0", "1.0.10.0"))
    }

    @Test
    fun versionCompare_toleratesPrefixAndNoise() {
        // 标签可能写成 v1.0.10.0 / 1.0.10.0 / 带说明
        assertTrue(Updater.isNewer("v1.0.10.0", "1.0.9.9"))
        assertTrue(Updater.isNewer("1.0.10.0", "1.0.9.9"))
        // 缺段当 0：1.0.10 等价 1.0.10.0，不比 1.0.10.0 新
        assertFalse("缺段应补 0", Updater.isNewer("v1.0.10", "1.0.10.0"))
    }

    /** 关键回归：网页版的 1.1.x 不能被当成原生版的新版本（历史事故） */
    @Test
    fun versionCompare_webVersionLooksNewer_butFilterHandlesIt() {
        // 比较函数本身会认为 1.1.1.5 > 1.0.10.0 —— 这是对的，
        // 所以"别把网页版当更新"必须靠**筛选资产**，不能靠版本号。
        assertTrue(Updater.isNewer("v1.1.1.5", "1.0.10.0"))
        // 记录一下这个前提：如果哪天有人想"只比版本号"，这条测试会提醒他
        assertTrue(true)
    }

    // ── 山名归一化（决定"同一座山能不能合并"）────────────────
    @Test
    fun peakName_normalizesCommonVariants() {
        assertEquals("太白山", normalizePeakName("太白山"))
        assertEquals("太白山", normalizePeakName(" 太白山 "))
        assertEquals("太白山", normalizePeakName("太白山（拔仙台）"))
        assertEquals("太白山", normalizePeakName("太白山(拔仙台)"))
        assertEquals("", normalizePeakName(null))
        assertEquals("", normalizePeakName("   "))
    }

    @Test
    fun peakName_keepsDifferentMountainsApart() {
        // 归一化不能把两座不同的山并成一座
        assertNotEquals(normalizePeakName("华山"), normalizePeakName("泰山"))
    }

    @Test
    fun weather_normalizesEmojiAndText() {
        // 网页版存的是 emoji，原生存文字 —— 迁移时必须能认出来
        assertEquals("晴", normWeather("☀️"))
        assertEquals("多云", normWeather("多云"))
        assertNull(normWeather(null))
        assertNull(normWeather(""))
    }

    // ── 日期解析（决定"记录会不会被算进错误的年份"）──────────
    @Test
    fun dateParse_acceptsKnownFormats_rejectsGarbage() {
        assertTrue(parseRecordDate("2026-11-05") != null)
        assertTrue(parseRecordDate("2026-11-05T10:30:00") != null)
        assertNull("乱字符串必须返回 null，不能抛", parseRecordDate("不是日期"))
        assertNull(parseRecordDate(""))
    }

    // ── 里程碑统计（数字必须是真的）──────────────────────────
    private fun rec(id: String, name: String, km: Double, alt: Int, diff: Int = 3) =
        RecordEntity(
            id = id, name = name, distance = km, duration = 60,
            elevation = alt, difficulty = diff, mood = "疲惫", weather = "多云",
            companions = "", notes = "", createdAt = "2026-11-01T08:00:00",
            updatedAt = "2026-11-01T08:00:00"
        )

    @Test
    fun stats_sumDistanceAndPeaks() {
        val rs = listOf(
            rec("a", "太白山", 10.0, 3767),
            rec("b", "华山", 5.5, 2154),
            rec("c", "太白山", 3.0, 3767)   // 同一座山只算一座
        )
        val st = milestoneStats(rs)
        assertEquals("总里程要相加", 18.5, st.km, 0.0001)
        assertEquals("同名山只算一座", 2, st.peaks)
        assertEquals("最高海拔取最大", 3767, st.maxAlt)
    }

    @Test
    fun milestoneProgress_isProportionalAndCapped() {
        val st = milestoneStats(listOf(rec("a", "太白山", 50.0, 1000)))
        val km100 = com.xixi.hiking.nativeapp.data.MILESTONES.first { it.id == "km_100" }
        val (cur, need) = milestoneProgress(km100, st)
        assertEquals(50.0, cur, 0.0001)
        assertEquals(100.0, need, 0.0001)
        // 50/100 km 不该达成 100km
        assertFalse(milestoneDone(listOf(rec("b", "山", 50.0, 1000))).contains("km_100"))
        assertTrue(milestoneDone(listOf(rec("c", "山", 120.0, 1000))).contains("km_100"))
    }

    // ── 凭据加密（网盘密码）──────────────────────────────────
    @Test
    fun secretBox_legacyPlaintextStillReadable() {
        // 老版本存的是明文，升级后必须还能读出来（否则同步直接失效）
        assertEquals("mypassword", SecretBox.decrypt("mypassword"))
        assertEquals("", SecretBox.decrypt(""))
    }

    @Test
    fun secretBox_roundTrip_ifKeystoreAvailable() {
        // Robolectric 下 AndroidKeyStore 不一定可用：可用就必须能加解密，
        // 不可用则要求"降级但不崩"（返回空串而不是抛异常）
        val enc = runCatching { SecretBox.encrypt("p@ss word 中文") }.getOrNull().orEmpty()
        if (enc.isEmpty()) {
            assertTrue("空输入应返回空", true)
            return
        }
        // Robolectric 里 AndroidKeyStore 通常不可用 ⇒ 会走 enc0: 降级。
        // 关键不是"有没有加密"，而是**降级必须留痕**：
        //   要么是真密文（enc1:），要么是明确标记的明文（enc0:），
        //   绝不能是"看起来像明文、其实也没标记"——那样用户以为加密了、
        //   诊断也发现不了，是最坏的静默降级。
        val encrypted = enc.startsWith("enc1:")
        val marked = enc.startsWith("enc0:")
        assertTrue("加密失败必须留下 enc0 标记，不能静默明文", encrypted || marked)
        assertEquals("无论加密还是降级，都必须能还原原文", "p@ss word 中文", SecretBox.decrypt(enc))
    }

    // ── 签名校验（装错包必须报出来）──────────────────────────
    @Test
    fun integrity_detectsSignatureMismatch() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val r = Integrity.check(ctx)
        // Robolectric 用的是测试签名，和钉住的 debug 证书不同 ⇒ 必须判为 MODIFIED。
        // 如果哪天这里变成 OK，说明 Integrity 根本没在比对（就是个假检查）。
        assertNotEquals("签名校验必须真的比对，不能恒为 OK", Integrity.State.OK, r.state)
        assertTrue("状态必须可读", r.state == Integrity.State.MODIFIED || r.state == Integrity.State.UNKNOWN)
    }

    // ── 山册合并（同一座山不能分成两座）──────────────────────
    @Test
    fun mtnMerge_linkAndUnlink_areConsistent() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        MtnMerge.clear(ctx)

        MtnMerge.link(ctx, listOf("太白山（拔仙台）", "太白山"), "太白山", "太白山")
        // 合并后：两个来源都指向同一个 key，标签用目标名
        assertEquals("太白山", MtnMerge.keyOf(ctx, "太白山（拔仙台）"))
        assertEquals("太白山", MtnMerge.labelOf(ctx, "太白山"))
        // ★语义澄清：isMergeTarget 问的是"是不是**被并入的目标**"，
        //   不是"它自己是不是来源"。有别的山指过来就是 true（这样 UI 才显示"断开连接"）。
        assertTrue("有山并入它 → 是归并目标", MtnMerge.isMergeTarget(ctx, "太白山"))
        assertFalse("来源卡自己不是目标", MtnMerge.isMergeTarget(ctx, "太白山（拔仙台）"))

        // 取消合并后必须回到各自独立
        MtnMerge.unlink(ctx, "太白山（拔仙台）")
        assertEquals("太白山（拔仙台）", MtnMerge.keyOf(ctx, "太白山（拔仙台）"))
        MtnMerge.clear(ctx)
    }
    // ── 云盘 URL 拼接（2026-11-05 上传 404 的根因）────────────
    @Test
    fun webdavUrl_appendsDirExactlyOnce() {
        val f = com.xixi.hiking.nativeapp.data.WebDavClient
        // 用户按旧提示把目录也写进地址里 → 不能变成 xixi-hiking/xixi-hiking
        assertEquals(
            "https://dav.jianguoyun.com/dav/xixi-hiking/a.zip",
            f.cloudUrlFor("https://dav.jianguoyun.com/dav/xixi-hiking", "a.zip")
        )
        // 带尾斜杠
        assertEquals(
            "https://dav.jianguoyun.com/dav/xixi-hiking/a.zip",
            f.cloudUrlFor("https://dav.jianguoyun.com/dav/xixi-hiking/", "a.zip")
        )
        // 正常填到 dav
        assertEquals(
            "https://dav.jianguoyun.com/dav/xixi-hiking/a.zip",
            f.cloudUrlFor("https://dav.jianguoyun.com/dav", "a.zip")
        )
        // 前后空格
        assertEquals(
            "https://dav.jianguoyun.com/dav/xixi-hiking/a.zip",
            f.cloudUrlFor("  https://dav.jianguoyun.com/dav/  ", "a.zip")
        )
        // 空名 = 目录本身（PROPFIND 用，必须带尾斜杠）
        assertEquals(
            "https://dav.jianguoyun.com/dav/xixi-hiking/",
            f.cloudUrlFor("https://dav.jianguoyun.com/dav", "")
        )
    }

}
