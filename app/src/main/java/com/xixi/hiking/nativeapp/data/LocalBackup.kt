package com.xixi.hiking.nativeapp.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地自动备份 —— 对齐 Web 版 `autoLocalBackupIfDue`。
 *
 * 为什么必须做：这是个日记本，数据全在本机 SQLite。手机丢了 / 应用被清数据 /
 * 手滑点了「抹掉所有足迹」，就真的没了。WebDAV 是可选配置，大多数人不会配。
 *
 * 策略：
 * - 每天最多一份（按 yyyy-MM-dd 命名，同一天覆盖）
 * - 只保留最近 [KEEP] 份，超出按时间删最旧的
 * - 备份目录在应用私有 filesDir/backups —— 不进相册、不被其他应用看到
 * - **失败绝不能崩**：整个流程包在 try 里，最坏情况就是没备份成功
 */
object LocalBackup {

    private const val DIR = "backups"
    private const val KEEP = 7
    private const val PREF_LAST = "local_backup_last_day"

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** 上一次自动备份是哪天（yyyy-MM-dd），没有返回 null */
    fun lastDay(ctx: Context): String? =
        Prefs.getString(ctx, PREF_LAST, "").takeIf { it.isNotBlank() }

    /** 所有本地备份，按时间从新到旧 */
    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /**
     * 到期才备份。返回新生成的文件；今天已经备份过则返回 null。
     * ★ 调用方应该在 IO 线程执行（会读全库 + 写文件）。
     */
    suspend fun backupIfDue(ctx: Context, buildJson: suspend () -> String): File? {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (lastDay(ctx) == today) return null
            val json = buildJson()
            val f = File(dir(ctx), "auto-$today.json")
            f.writeText(json)
            Prefs.setString(ctx, PREF_LAST, today)
            prune(ctx)
            Log.i("hiking", "本地自动备份完成：${f.name}（${f.length() / 1024} KB）")
            f
        } catch (e: Throwable) {
            // 备份失败不能影响正常使用
            Log.w("hiking", "本地自动备份失败，本次已跳过", e)
            null
        }
    }

    /** 手动立刻备份一份（不等到期） */
    suspend fun backupNow(ctx: Context, buildJson: suspend () -> String): File? {
        return try {
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
            val f = File(dir(ctx), "manual-$stamp.json")
            f.writeText(buildJson())
            prune(ctx)
            f
        } catch (e: Throwable) {
            Log.w("hiking", "手动备份失败", e)
            null
        }
    }

    /** 只留最近 KEEP 份（手动备份也算在内，但至少保住最新那份自动的） */
    private fun prune(ctx: Context) {
        try {
            val all = list(ctx)
            if (all.size <= KEEP) return
            all.drop(KEEP).forEach { it.delete() }
        } catch (e: Throwable) {
            Log.w("hiking", "清理旧备份失败", e)
        }
    }

    /** 读取某份备份的正文 */
    fun read(f: File): String? = try {
        if (f.exists()) f.readText() else null
    } catch (e: Throwable) {
        null
    }

    /** 备份文件的显示信息：日期 + 大小 + 记录条数（条数读不到就显示 —） */
    fun describe(f: File): Triple<String, String, String> {
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
        val size = when {
            f.length() > 1024 * 1024 -> String.format(Locale.US, "%.1f MB", f.length() / 1048576.0)
            else -> "${f.length() / 1024} KB"
        }
        var count = "—"
        try {
            val txt = f.readText()
            val o = JSONObject(txt)
            count = "${o.optJSONArray("records")?.length() ?: 0} 条记录"
        } catch (e: Throwable) {
            // 文件损坏就只显示大小
        }
        return Triple(date, size, count)
    }

    /**
     * 备份是否「太久没做」—— 设置页和概览的健康提示用。
     * 没有任何备份、或最近一份超过 [days] 天，返回 true。
     */
    fun isStale(ctx: Context, days: Int = 7): Boolean {
        val all = list(ctx)
        if (all.isEmpty()) return true
        val newest = all.first().lastModified()
        return System.currentTimeMillis() - newest > days * 86_400_000L
    }
}
