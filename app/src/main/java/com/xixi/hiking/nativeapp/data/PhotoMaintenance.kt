package com.xixi.hiking.nativeapp.data

import android.content.Context
import android.util.Log
import com.xixi.hiking.nativeapp.ui.settings.AppDatabaseRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 照片存储维护 —— 对齐 Web 版 `photoCollectOrphans` / `photoGetUsage`。
 *
 * 照片二进制在 filesDir/photos/，数据库只存文件名。两边可能对不上：
 * - **孤儿文件**：文件还在，但数据库里没有对应行（删记录时崩了、导入失败、重复导入）
 *   → 白占空间，而且永远删不掉，因为界面上看不到
 * - **丢失文件**：数据库有行但文件没了（被系统清理、恢复备份时漏了）
 *   → 界面上一片灰块
 *
 * 这两类问题都不会报错，只会悄悄吃存储，所以需要一个能显式检查、显式清理的入口。
 */
object PhotoMaintenance {

    data class Usage(
        /** 数据库里的照片条数 */
        val dbCount: Int,
        /** 磁盘上的文件数 */
        val fileCount: Int,
        /** 磁盘占用字节 */
        val bytes: Long,
        /** 孤儿文件（有文件没记录） */
        val orphans: List<File>,
        /** 丢失文件（有记录没文件）的文件名 */
        val missing: List<String>
    ) {
        val orphanBytes: Long get() = orphans.sumOf { runCatching { it.length() }.getOrDefault(0L) }
        val totalMb: String get() = String.format(java.util.Locale.US, "%.1f", bytes / 1048576.0)
        val orphanMb: String get() = String.format(java.util.Locale.US, "%.1f", orphanBytes / 1048576.0)
    }

    /** 扫一遍（IO 线程调用；几百张照片也是毫秒级，别在主线程跑） */
    suspend fun scan(ctx: Context): Usage = withContext(Dispatchers.IO) {
        val photosDir = File(ctx.filesDir, "photos")
        val files = photosDir.listFiles { f -> f.isFile }?.toList() ?: emptyList()

        val dbRows = try {
            AppDatabaseRef.get(ctx).photoDao().allOnce()
        } catch (e: Throwable) {
            Log.w("hiking", "读照片表失败", e)
            emptyList()
        }

        val names = dbRows.map { it.fileName }.toSet()
        val fileNames = files.map { it.name }.toSet()

        Usage(
            dbCount = dbRows.size,
            fileCount = files.size,
            bytes = files.sumOf { runCatching { it.length() }.getOrDefault(0L) },
            orphans = files.filter { it.name !in names },
            missing = dbRows.map { it.fileName }.filter { it !in fileNames }
        )
    }

    /** 删掉孤儿文件，返回释放的字节数 */
    suspend fun cleanOrphans(orphans: List<File>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        orphans.forEach { f ->
            val len = runCatching { f.length() }.getOrDefault(0L)
            if (runCatching { f.delete() }.getOrDefault(false)) freed += len
        }
        Log.i("hiking", "清理孤儿照片 ${orphans.size} 个，释放 ${freed / 1024} KB")
        freed
    }

    /** 删掉「有记录没文件」的空行（否则详情页永远有一块灰） */
    suspend fun purgeMissing(ctx: Context, missing: List<String>): Int = withContext(Dispatchers.IO) {
        if (missing.isEmpty()) return@withContext 0
        try {
            val dao = AppDatabaseRef.get(ctx).photoDao()
            // PhotoDao 没有「按文件名删」，走 allOnce + 精确匹配（量级是几百，够用）
            val dead = dao.allOnce().filter { it.fileName in missing }
            dead.forEach { dao.deleteById(it.id) }
            dead.size
        } catch (e: Throwable) {
            Log.w("hiking", "清理空照片行失败", e)
            0
        }
    }
}
