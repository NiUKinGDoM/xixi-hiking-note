package com.xixi.hiking.nativeapp.data

import android.content.Context
import java.io.File

/**
 * ★崩溃记录器 —— 2026-11-01 新增（因为白屏/闪退时我拿不到 logcat）。
 *
 * 做什么：装一个全局未捕获异常处理器，把堆栈写到 filesDir/last_crash.txt。
 * 下次启动时 [readLast] 读到内容 → 概览页顶部显示红色横幅，点开能看完整堆栈。
 *
 * 为什么必须写这个：用户报「点很多东西都闪退」时，我在本地没有设备、
 * 模拟器 adb 又是 offline，只能靠猜。有了它，用户截一张图就能定位到具体行。
 *
 * ⚠️ 绝不能吞掉原异常处理器 —— 否则系统不知道 App 崩了，会卡在奇怪的状态。
 */
object CrashGuard {
    private const val FILE_NAME = "last_crash.txt"

    /**
     * ★★安全模式判定（2026-11-02）
     *
     * 问题：GPU 效果（AGSL 折射 / depthEffect）如果在**运行时**崩，
     * 那是 native crash，Java 层 try-catch 完全抓不住，只能靠"上次崩过"来预防。
     *
     * 做法：每次冷启动把 boot_count +1 写盘；正常启动 2.5 秒后清零。
     *   · 连续两次没走完启动（count >= 2）→ 判定上次是启动期崩溃
     *   · 打开 LiquidGlass.forceStatic，全站玻璃退回静态绘制（无 GPU 效果）
     *   · 计数归零，App 恢复可用
     *
     * 这样最坏情况是"玻璃变简单"，而不是"一点就崩"。
     */
    fun enterSafeModeIfNeeded(ctx: Context) {
        try {
            val f = File(ctx.filesDir, "boot_count")
            val n = try { f.readText().trim().toInt() } catch (e: Throwable) { 0 }
            if (n >= 2) {
                com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass.forceStatic = true
                f.writeText("0")
            } else {
                f.writeText((n + 1).toString())
            }
        } catch (ignored: Throwable) {
        }
    }

    /** 启动成功（界面已经稳定）→ 清零计数，下次不再进安全模式 */
    fun markStartupOk(ctx: Context) {
        try {
            File(ctx.filesDir, "boot_count").writeText("0")
            com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass.forceStatic = false
        } catch (ignored: Throwable) {
        }
    }

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        // ★把 applicationContext 交给点击兜底（SafeTap），它要在 handler 抛异常时弹一句提示
        com.xixi.hiking.nativeapp.ui.SafeTap.appContext = app
        com.xixi.hiking.nativeapp.data.Trace.attach(app)
        // 每次冷启动清掉上一次的轨迹，只保留本次的
        com.xixi.hiking.nativeapp.data.Trace.clear()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val body = buildString {
                    append("时间：").append(java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
                    ).format(java.util.Date()))
                    append("\n线程：").append(t.name)
                    append("\n\n").append(e.stackTraceToString())
                }
                File(app.filesDir, FILE_NAME).writeText(body)
            } catch (ignored: Throwable) {
                // 连崩溃日志都写不进去就算了，别在崩溃路径上再抛
            }
            prev?.uncaughtException(t, e)
        }
    }

    /**
     * ★非致命的「软错误」记录（2026-11-02）。
     * 点击 handler 被 SafeTap 拦下来的异常走这里 —— 不杀进程，但必须留证据，
     * 否则用户只说"按了没反应"，我永远查不到是哪个 handler。
     * 只留最近 30 条，避免无限增长。
     */
    fun recordSoft(where: String, t: Throwable) {
        try {
            val ctx = com.xixi.hiking.nativeapp.ui.SafeTap.appContext ?: return
            val f = File(ctx.filesDir, "soft_error.txt")
            val line = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date()) + "  [" + where + "] " +
                t.javaClass.simpleName + ": " + (t.message ?: "") + "\n"
            val old = if (f.exists()) f.readText() else ""
            val merged = (line + old).lines().take(60).joinToString("\n")
            f.writeText(merged)
        } catch (ignored: Throwable) {
        }
    }

    /** 软错误文件（被 SafeTap 拦下来的异常） */
    private const val SOFT_FILE = "soft_error.txt"

    /**
     * ★2026-11-04 诊断卡要用的两个接口（对齐 Web 的「出错自动出现 + 导出 + 清除」）。
     *
     * 之前软错误只写文件、界面上什么都不显示 —— 用户只会偶尔看到一条 toast
     * 「操作出错了：xxx」，一秒钟就没了，既不知道累计了几次，也没法把它导出来。
     */
    fun softLines(ctx: Context): List<String> = try {
        val f = File(ctx.filesDir, SOFT_FILE)
        if (!f.exists()) emptyList()
        else f.readLines().filter { it.isNotBlank() }
    } catch (t: Throwable) {
        emptyList()
    }

    fun clearSoft(ctx: Context) {
        try {
            File(ctx.filesDir, SOFT_FILE).delete()
        } catch (ignored: Throwable) {
        }
    }

    fun readLast(ctx: Context): String? {
        return try {
            val f = File(ctx.filesDir, FILE_NAME)
            if (!f.exists()) null else f.readText().ifBlank { null }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * ★2026-11-11 「上次崩溃过」只提示一次。
     *
     * 为什么需要：崩溃日志写在本地，但界面上没有任何地方会主动告诉你 ——
     * 用户只会觉得"刚才好像闪了一下"，然后什么都不发生。设置页里的诊断卡
     * 埋得太深，不会有人主动去翻。
     *
     * 用崩溃内容的 hashCode 当 key：同一份崩溃只提示一次（不骚扰），
     * 下次再崩（内容不同）会重新提示。
     */
    fun shouldNotifyLast(ctx: Context): Boolean {
        return try {
            val txt = readLast(ctx) ?: return false
            val key = "crash_notice_%d".format(txt.hashCode())
            if (Prefs.getBool(ctx, key, false)) return false
            Prefs.setBool(ctx, key, true)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * ★导出诊断包（2026-11-02）—— 对齐 Web 版 `exportDiagnostics`。
     *
     * 崩溃记录器只能「看」，用户没法把堆栈发出来。这个函数把定位问题需要的
     * 全部信息拼成一段纯文本：版本、机型、系统、数据量、崩溃堆栈。
     * 用户复制或分享出去，我就能直接对上号，不用来回猜。
     *
     * ⚠️ 绝不包含记录内容、照片、网盘密码 —— 只发"环境"，不发"数据"。
     */
    fun buildDiagnostics(ctx: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== XiXi の徒步小记 · 诊断信息 ===")
        sb.appendLine("生成时间：" + java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
        ).format(java.util.Date()))
        sb.appendLine()
        sb.appendLine("[应用]")
        sb.appendLine("版本：" + com.xixi.hiking.nativeapp.BuildConfig.VERSION_NAME +
            "（vc" + com.xixi.hiking.nativeapp.BuildConfig.VERSION_CODE + "）")
        sb.appendLine("包名：" + ctx.packageName)
        sb.appendLine()
        sb.appendLine("[设备]")
        sb.appendLine("机型：" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
        sb.appendLine("系统：Android " + android.os.Build.VERSION.RELEASE +
            "（API " + android.os.Build.VERSION.SDK_INT + "）")
        sb.appendLine("ABI：" + android.os.Build.SUPPORTED_ABIS.joinToString(", "))
        val dm = ctx.resources.displayMetrics
        sb.appendLine("屏幕：${dm.widthPixels}×${dm.heightPixels} @" + dm.density + "x")
        sb.appendLine()
        sb.appendLine("[安装包]")
        sb.appendLine("签名校验：" + com.xixi.hiking.nativeapp.data.Integrity.summary(ctx))
        sb.appendLine()
        sb.appendLine("[数据量]")
        try {
            val db = com.xixi.hiking.nativeapp.ui.settings.AppDatabaseRef.get(ctx)
            // smellscan:ok 这里整段跑在 shareDiagnostics 的后台 Thread 里，不在主线程
            sb.appendLine("记录：" + kotlinx.coroutines.runBlocking { db.recordDao().count() } + " 条")
            // smellscan:ok 同上，后台线程
            sb.appendLine("照片：" + kotlinx.coroutines.runBlocking { db.photoDao().allOnce().size } + " 张")
        } catch (e: Throwable) {
            sb.appendLine("记录：读取失败（" + e.javaClass.simpleName + "）")
        }
        sb.appendLine("WebDAV：" + if (com.xixi.hiking.nativeapp.data.WebDavClient.load(ctx).configured) "已配置" else "未配置")
        sb.appendLine("本地备份：" + com.xixi.hiking.nativeapp.data.LocalBackup.list(ctx).size + " 份")
        sb.appendLine("液态玻璃：" + if (com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass.enabled) {
            if (com.xixi.hiking.nativeapp.ui.liquid.LiquidGlass.shaderSupported) "开启（真实折射）" else "开启（静态玻璃）"
        } else "已关闭")
        sb.appendLine()
        sb.appendLine("[上次崩溃]")
        val crash = readLast(ctx)
        sb.appendLine(crash ?: "（没有崩溃记录）")
        sb.appendLine()
        // textcheck:ok 诊断 txt 的正文，不是界面说明文字
        sb.appendLine("=== 以上信息不含任何记录内容 ===")
        return sb.toString()
    }

    /**
     * 把诊断写成一个 txt 并走系统分享（用户直接发给开发者）。
     *
     * ★★2026-11-02：必须放到工作线程。里面要 `runBlocking` 查 Room 的记录数/照片数，
     * 而这条路径是从按钮 onClick（主线程）进来的 —— 数据一多就是几秒白屏 + ANR，
     * 系统弹出的「应用无响应」用户会当成闪退。现在整段挪到子线程，主线程立刻返回。
     */
    fun shareDiagnostics(ctx: Context) {
        val app = ctx.applicationContext
        Thread {  // smellscan:ok 诊断只点一次，用完即走；这里就是要脱离生命周期（见下方注释）
            try {
                val text = buildDiagnostics(app)
                val f = File(app.cacheDir, "xixi-diagnostics.txt")
                f.writeText(text)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    app, app.packageName + ".fileprovider", f
                )
                val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                app.startActivity(
                    android.content.Intent.createChooser(i, "导出诊断信息").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Throwable) {
                android.util.Log.w("hiking", "导出诊断失败", e)
            }
        }.start()
    }

    fun clear(ctx: Context) {
        try {
            File(ctx.filesDir, FILE_NAME).delete()
        } catch (e: Throwable) {
            // 忽略
        }
    }
}
