package com.xixi.hiking.nativeapp

import com.xixi.hiking.nativeapp.data.WebDavClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ★2026-11-16 三态判定测试
 *
 * 用户 bug：「导入不带密码的云同步之后，设置里显示已绑定，但没有密码」。
 *
 * 根因是 Config 只有 `configured`（url+user）一个布尔值，导入的无密码备份
 * 落进"已绑定"分支，绿点 + 已绑定，同步却次次 401。
 *
 * 现在拆成三态：
 *   · 未绑定     : !configured
 *   · 待补全密码 : needsPassword（有地址账号、没密码）
 *   · 真能同步   : ready（三样齐全）
 *
 * 这组断言看着简单，但它是 UI 三态分支的唯一依据 ——
 * 以后谁改了 configured 的定义，这里会立刻红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebDavConfigTest {

    private val url = "https://dav.jianguoyun.com/dav/xixi-hiking"
    private val user = "xixi"

    @Test
    fun emptyConfigIsNotBound() {
        val c = WebDavClient.Config()
        assertFalse("空白配置不该算已绑定", c.configured)
        assertFalse("空白配置不该算缺密码（它连账号都没有）", c.needsPassword)
        assertFalse("空白配置不能同步", c.ready)
    }

    @Test
    fun importedWithoutPasswordNeedsPassword() {
        // ★这就是用户报的场景：从备份导入了地址+账号，密码是空的
        val c = WebDavClient.Config(url = url, user = user, pass = "")
        assertTrue("地址账号在，configured 仍为真（语义是「有配置」）", c.configured)
        assertTrue("必须识别为待补全密码", c.needsPassword)
        assertFalse("缺密码绝不能算能同步", c.ready)
    }

    @Test
    fun fullConfigIsReady() {
        val c = WebDavClient.Config(url = url, user = user, pass = "app-password")
        assertTrue(c.configured)
        assertFalse("有密码就不该再提示补全", c.needsPassword)
        assertTrue("三样齐全才算能同步", c.ready)
    }

    @Test
    fun userOnlyWithoutUrlIsNotConfigured() {
        // 只有账号、没地址：不算配置，也不算"缺密码"（连往哪同步都不知道）
        val c = WebDavClient.Config(url = "", user = user, pass = "")
        assertFalse(c.configured)
        assertFalse(c.needsPassword)
        assertFalse(c.ready)
    }
}
