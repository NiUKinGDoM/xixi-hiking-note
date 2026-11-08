package com.xixi.hiking.nativeapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File
import java.io.FileOutputStream

/**
 * 照片实体 —— 对齐 Web 版 photos[] 的结构（id / 宽 / 高），
 * 但**二进制存文件系统**，数据库只存相对路径（Web 版是 IndexedDB 存 dataURL）。
 * 这样：① 数据库轻 ② 图片加载走 Coil 缓存 ③ 导出时可选择是否带上原图
 */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val fileName: String,     // 相对 filesDir/photos/ 的文件名
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = 0
)

/**
 * 照片文件存取。
 * 压缩策略对齐 Web 版：长边 1600、JPEG 质量 85（可在设置里调）。
 */
object PhotoStore {
    private const val DIR = "photos"
    const val MAX_SIDE = 1600
    const val QUALITY = 85

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    /**
     * ★★文件名消毒（2026-11-02）：导入备份时文件名来自外部 JSON 的 id 字段，
     * 一旦是 `../../databases/hiking.db` 就能写到沙盒里别的目录去（越权写）。
     * 只允许字母数字与 _ - .，且不允许 .. 与开头的点。
     */
    fun safeName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.')
        return if (cleaned.isBlank() || cleaned.contains("..")) "photo.jpg" else cleaned
    }

    /**
     * 从相册拿到的 Uri 压缩后落盘，返回 (文件名, 宽, 高)。
     *
     * ★★2026-11-02 修了两个必崩/必错的问题：
     *  ① **OOM**：原来直接 `decodeStream` 解原图。手机原图 8000×6000 = 190MB 内存，
     *     低端机（正是用户那台）当场 OOM 闪退。现在先 `inJustDecodeBounds` 只读尺寸，
     *     算 `inSampleSize` 再解，峰值降到几 MB。
     *  ② **方向**：没有读 EXIF，竖着拍的照片进来全躺倒（缩略图、详情、分享卡都歪）。
     *     现在按 EXIF 旋转/镜像回来。
     */
    fun saveFromUri(context: Context, uri: Uri, id: String): Triple<String, Int, Int>? {
        return try {
            val resolver = context.contentResolver

            // ① 只读尺寸
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // ② 算采样率（2 的幂），保证解码后长边仍 ≥ MAX_SIDE，再靠 createScaledBitmap 精修
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            // ③ EXIF 方向（android.media.ExifInterface 的流构造从 API 24 起可用，正好是我们的 minSdk）
            val rotated = try {
                val orientation = resolver.openInputStream(uri)?.use {
                    android.media.ExifInterface(it)
                        .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
                } ?: 1
                val m = android.graphics.Matrix()
                when (orientation) {
                    2 -> m.setScale(-1f, 1f)
                    3 -> m.setRotate(180f)
                    4 -> m.setScale(1f, -1f)
                    5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
                    6 -> m.setRotate(90f)
                    7 -> { m.setRotate(270f); m.postScale(-1f, 1f) }
                    8 -> m.setRotate(270f)
                    else -> Unit
                }
                if (m.isIdentity) decoded
                else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                    .also { if (it !== decoded) decoded.recycle() }
            } catch (t: Throwable) {
                decoded
            }

            // ④ 等比缩到长边 <= MAX_SIDE
            val maxSide = maxOf(rotated.width, rotated.height)
            val scaled = if (maxSide > MAX_SIDE) {
                val ratio = MAX_SIDE.toFloat() / maxSide
                Bitmap.createScaledBitmap(
                    rotated,
                    (rotated.width * ratio).toInt().coerceAtLeast(1),
                    (rotated.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else rotated

            val name = "$id.jpg"
            val f = File(dir(context), name)
            FileOutputStream(f).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            val w = scaled.width
            val h = scaled.height
            if (scaled !== rotated) scaled.recycle()
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
            Triple(name, w, h)
        } catch (e: OutOfMemoryError) {
            // ★OOM 是 Error 不是 Exception，原来只 catch Exception 接不住 ⇒ 直接闪退
            android.util.Log.w("hiking", "照片太大，已跳过")
            null
        } catch (e: Throwable) {
            android.util.Log.w("hiking", "照片保存失败", e)
            null
        }
    }

    fun delete(context: Context, fileName: String) {
        try { File(dir(context), fileName).delete() } catch (e: Exception) { }
    }

    /** 记录删除时清掉它的所有照片 */
    fun deleteByRecord(context: Context, photos: List<PhotoEntity>) {
        photos.forEach { delete(context, it.fileName) }
    }

    /** 读出来转 dataURL（导出备份用） */
    fun toBase64(context: Context, fileName: String): String? {
        return try {
            val f = File(dir(context), fileName)
            if (!f.exists()) return null
            val bytes = f.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }
}
