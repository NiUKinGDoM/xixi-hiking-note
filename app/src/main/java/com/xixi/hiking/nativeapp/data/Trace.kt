package com.xixi.hiking.nativeapp.data

import android.content.Context
import java.io.File

/**
 * ★★执行轨迹（2026-11-02）—— 给"弹窗一打开就闪退"用的定位器
 *
 * 背景：用户报「点带弹窗的选项 → 弹窗不显示 → 直接闪退」。
 * 这种崩在**组合/绘制阶段**，onClick 的 try-catch 完全拦不住，
 * 而 Robolectric 跑的是软件渲染，真机的 GPU 路径它根本走不到 —— 测不出来。
 *
 * 做法：在关键节点用 [step] 落一行到 filesDir/trace.txt（只留最近 40 行）。
 *   · 崩了重启 → 诊断报告（设置 → 导出数据 → 导出诊断报告）里能看到「上次运行最后一步」
 *     （★2026-11-04 设置页那行红字已按用户要求去掉，不占用户界面）
 *   · 只写文件、不写日志流，开销可忽略
 *   · **每条都包 try** —— 诊断工具自己绝不能成为新的崩溃源
 */
object Trace {

    private const val FILE = "trace.txt"
    private const val MAX_LINES = 40

    @Volatile
    private var app: Context? = null

    /**
     * ★上次进程最后执行到哪一步 —— 必须在 [attach] 里"先读后清"，
     * 否则本次启动把文件清掉了，诊断报告里的"上次运行最后一步"永远是空（这是第一版的 bug）。
     */
    @Volatile
    private var previousStep: String? = null

    fun attach(ctx: Context) {
        app = ctx.applicationContext
        previousStep = try {
            val f = File(ctx.filesDir, FILE)
            if (f.exists()) f.readLines().lastOrNull { it.isNotBlank() } else null
        } catch (t: Throwable) {
            null
        }
        clear()
    }

    /** 上一次进程的最后一个轨迹点（崩溃后重启用来看死在哪） */
    fun lastOfPreviousRun(): String? = previousStep

    fun step(name: String) {
        val ctx = app ?: return
        try {
            val f = File(ctx.filesDir, FILE)
            val old = if (f.exists()) f.readLines() else emptyList()
            val stamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date())
            val lines = (old + "$stamp  $name").takeLast(MAX_LINES)
            f.writeText(lines.joinToString("\n"))
        } catch (ignored: Throwable) {
        }
    }

    /** 读最后一步（界面上的"上次崩溃点"就是它） */
    fun last(): String? = try {
        val ctx = app ?: return null
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) f.readLines().lastOrNull { it.isNotBlank() } else null
    } catch (t: Throwable) {
        null
    }

    fun clear() {
        try {
            app?.let { File(it.filesDir, FILE).delete() }
        } catch (ignored: Throwable) {
        }
    }
}
