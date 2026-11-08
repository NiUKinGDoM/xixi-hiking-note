package com.xixi.hiking.nativeapp

import com.xixi.hiking.nativeapp.ui.pillScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏胶囊的 squash & stretch（2026-11-06）。
 *
 * 为什么值得单测：这段公式在图形层里，肉眼只能看出"动没动"，
 * 看不出"动得对不对"。而且上一版正是**输入恒为 0**（拖动走 snapTo），
 * 公式永远乘 1 —— 界面上完全正常，只是没有效果，没人能发现。
 * 把公式提成纯函数后，这组用例就是"形变真的会随速度发生"的证据。
 *
 * ★2026-11-06 三轮校准：官方 ±20% 太夸张 → ±3.5% 又太不明显 → 8% 再调到 12%。
 *   上限断言钉死 "不许超过 ±14%"，防止又滑回演示级的夸张量。
 */
class BottomBarShapeTest {

    private val eps = 1e-4f

    @Test
    fun `静止不动时完全不形变`() {
        val (sx, sy) = pillScale(velocity = 0f, press = 0f, bounce = 1f)
        assertEquals(1f, sx, eps)
        assertEquals(1f, sy, eps)
    }

    @Test
    fun `向右快滑时横向拉长且纵向压扁`() {
        val (sx, sy) = pillScale(velocity = 2f, press = 0f, bounce = 1f)
        // 2 × 0.09 = 0.18 → clamp 到 0.12 ⇒ 1 / 0.88
        assertEquals(1f / 0.88f, sx, eps)
        // 2 × 0.035 = 0.07 → clamp 到 0.05 ⇒ 1 − 0.05
        assertEquals(0.95f, sy, eps)
        assertTrue("横向必须比纵向长", sx > sy)
    }

    @Test
    fun `向左快滑是镜像结果`() {
        val (sx, sy) = pillScale(velocity = -2f, press = 0f, bounce = 1f)
        assertEquals(1f / 1.12f, sx, eps)
        assertEquals(1.05f, sy, eps)
        assertTrue("反向时纵向被拉长", sy > sx)
    }

    @Test
    fun `速度再大也不会无限变形`() {
        val (sx, sy) = pillScale(velocity = 999f, press = 0f, bounce = 1f)
        assertEquals("clamp 上限 1 / 0.88", 1f / 0.88f, sx, eps)
        assertEquals("clamp 下限 0.95", 0.95f, sy, eps)
    }

    // ══════════════════════════════════════════════════════════════
    //  ★红线：形变幅度必须克制
    //  用户来回校准过三次（25% 太夸张 / 3.5% 太不明显 / 现在 12%）。
    //  这两条断言防止再往 20% 那种演示级夸张跑。
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `横向形变永远不超过 14 个百分点`() {
        for (v in listOf(-999f, -10f, -2f, -0.5f, 0f, 0.5f, 2f, 10f, 999f)) {
            val sx = pillScale(velocity = v, press = 0f, bounce = 1f).first
            assertTrue(
                "v=$v 时 scaleX=$sx 超出 [0.87, 1.15]",
                sx in 0.87f..1.15f
            )
        }
    }

    @Test
    fun `纵向形变永远不超过 6 个百分点`() {
        for (v in listOf(-999f, -10f, -2f, -0.5f, 0f, 0.5f, 2f, 10f, 999f)) {
            val sy = pillScale(velocity = v, press = 0f, bounce = 1f).second
            assertTrue(
                "v=$v 时 scaleY=$sy 超出 [0.94, 1.06]",
                sy in 0.94f..1.06f
            )
        }
    }

    @Test
    fun `按住时整体放大到 1_39 倍`() {
        val (sx, sy) = pillScale(velocity = 0f, press = 1f, bounce = 1f)
        assertEquals(1.39f, sx, eps)
        assertEquals(1.39f, sy, eps)
    }

    @Test
    fun `额外缩放系数是整体相乘`() {
        val (sx, sy) = pillScale(velocity = 0f, press = 0f, bounce = 0.86f)
        assertEquals(0.86f, sx, eps)
        assertEquals(0.86f, sy, eps)
    }

    @Test
    fun `速度方向改变时形变也会翻转`() {
        val right = pillScale(velocity = 1f, press = 0f, bounce = 1f).first
        val left = pillScale(velocity = -1f, press = 0f, bounce = 1f).first
        assertTrue("向右拉长", right > 1f)
        assertTrue("向左压短", left < 1f)
    }

    @Test
    fun `慢滑几乎不形变`() {
        // 0.2 格/秒 —— 手指慢慢挪的那种：只应该有 1% 出头的形变
        val sx = pillScale(velocity = 0.2f, press = 0f, bounce = 1f).first
        assertTrue("慢滑形变应小于 2%，实际 ${(sx - 1f) * 100}%", sx < 1.02f)
    }
}
