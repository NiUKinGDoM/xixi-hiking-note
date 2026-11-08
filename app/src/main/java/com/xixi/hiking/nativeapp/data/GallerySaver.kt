package com.xixi.hiking.nativeapp.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import java.io.File

/**
 * 保存图片到**系统相册**（Web 版 `saveQrToGallery` / 灯箱「保存」的等价物）。
 *
 * ★2026-11-03 抽出来共用三个地方：
 *   ① 分享卡「保存到相册」（用户：「所有分享卡改为保存，直接保存到相册」）
 *   ② 支持作者的收款码（Web 版 step2 的「保存二维码」）
 *   ③ 灯箱的「保存」（它自己有一套，行为一致，暂不动）
 *
 * 为什么走 MediaStore 而不是直接写文件：Android 10+ 公共目录受分区存储限制，
 * MediaStore.insert 会替我们拿到合法的 Uri 并自动归到 Pictures/XiXi徒步 下，
 * 用户打开相册就能看到，不需要任何存储权限。
 */
object GallerySaver {

    private const val ALBUM = "XiXi徒步"

    /** 存一张 Bitmap（JPEG，质量 92） */
    fun saveBitmap(ctx: Context, bmp: Bitmap, baseName: String): Boolean {
        return try {
        if (Build.VERSION.SDK_INT >= 29) {
            val name = "${baseName}_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            // Android 10 以下：写公共 Pictures 目录 + 通知相册扫描
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                ALBUM
            )
            if (!dir.exists()) dir.mkdirs()
            val dst = File(dir, "${baseName}_${System.currentTimeMillis()}.jpg")
            dst.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            android.media.MediaScannerConnection.scanFile(
                ctx, arrayOf(dst.absolutePath), arrayOf("image/jpeg"), null
            )
            true
        }
        } catch (t: Throwable) {
            android.util.Log.w("hiking", "保存到相册失败", t)
            false
        }
    }

    /** 存一个已有的图片文件（分享卡就是先画到 cacheDir 再存） */
    fun saveFile(ctx: Context, src: File, baseName: String): Boolean = try {
        val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: return false
        val ok = saveBitmap(ctx, bmp, baseName)
        bmp.recycle()
        ok
    } catch (t: Throwable) {
        android.util.Log.w("hiking", "保存文件到相册失败", t)
        false
    }

    /**
     * 存一张 res 里的图（收款码）。
     *
     * ★必须 `inScaled = false` + 走 openRawResource：
     *   这几个 jpg 放在 `drawable-nodpi`，但某些 ROM 仍会按密度换算，
     *   放大后的位图既糊又多占内存；直接按原字节解码最稳。
     */
    @android.annotation.SuppressLint("ResourceType")  // 见下方注释：openRawResource 传 drawable id 是刻意的
    fun saveDrawable(ctx: Context, @DrawableRes resId: Int, baseName: String): Boolean = try {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = ctx.resources.openRawResource(resId).use { ins ->
            BitmapFactory.decodeStream(ins, null, opts)
        } ?: return false
        val ok = saveBitmap(ctx, bmp, baseName)
        bmp.recycle()
        ok
    } catch (t: Throwable) {
        android.util.Log.w("hiking", "保存资源图到相册失败", t)
        false
    }
}
