package com.xixi.hiking.nativeapp.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * 安装包完整性校验（2026-11-05）
 *
 * 对应网页版的 `ResGuard`（资源 SHA-256 清单 + 启动软校验），但换成
 * **Android 上真正有效的那一层**：签名证书指纹。
 *
 * 为什么不是"资源哈希"
 * ────────────────────
 * 网页版的资源是外置文件，改一个 JS 文件不影响"安装"，所以它必须校验内容哈希。
 * 原生版的资源全编进 APK：动任何一个字节，原签名立刻失效 ——
 * 系统会拒绝安装，或者必须用另一把密钥重新签名。所以这里只要盯住
 * **签名证书有没有变**，就等价于"这个包是不是我用这套代码构建的"。
 *
 * 三种结果，只有一种要提示
 * ────────────────────────
 *   OK        指纹和 [IntegrityPin] 一致
 *   MODIFIED  指纹不一致（重打包 / 换了签名）
 *   UNKNOWN   读不到（极老的系统、企业设备策略限制）—— 不提示，免得误伤
 *
 * ★和网页版一样是**软校验**：只提示，不退出、不禁用功能。
 *   理由很现实：万一以后换签名（上架换正式密钥）而忘了更新 pin，
 *   硬拦截会把所有老用户锁在门外。提示一句"去官方渠道装"就够了。
 */
object Integrity {

    enum class State { OK, MODIFIED, UNKNOWN }

    data class Result(
        val state: State,
        /** 实际读到的指纹（小写无冒号），读不到为空 */
        val actual: String = "",
        /** 有多个签名者时全部列出，便于排查 */
        val all: List<String> = emptyList()
    )

    /** 每个进程只算一次 —— PackageManager 调用不便宜，而且签名不会中途变。 */
    @Volatile
    private var cached: Result? = null

    fun check(ctx: Context): Result {
        cached?.let { return it }
        val r = runCatching { readSignatures(ctx) }.getOrElse {
            Result(State.UNKNOWN)
        }
        cached = r
        return r
    }

    private fun readSignatures(ctx: Context): Result {
        val pm = ctx.packageManager
        val pkg = ctx.packageName

        val certs: List<ByteArray> = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val si = info.signingInfo ?: return Result(State.UNKNOWN)
            // apkContentsSigners = 当前实际签名者（不用 historical，历史签名是轮换前的旧证书）
            si.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                .signatures?.map { it.toByteArray() }.orEmpty()
        }
        if (certs.isEmpty()) return Result(State.UNKNOWN)

        val digests = certs.map { sha256Hex(it) }
        val expect = IntegrityPin.CERT_SHA256.lowercase()
        val state = if (digests.any { it == expect }) State.OK else State.MODIFIED
        return Result(state, digests.firstOrNull().orEmpty(), digests)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val d = md.digest(bytes)
        val sb = StringBuilder(d.size * 2)
        for (b in d) {
            sb.append("0123456789abcdef"[(b.toInt() shr 4) and 0x0f])
            sb.append("0123456789abcdef"[b.toInt() and 0x0f])
        }
        return sb.toString()
    }

    /** 给诊断报告用的一行（不含敏感信息，只有结论 + 前 16 位指纹） */
    fun summary(ctx: Context): String {
        val r = check(ctx)
        return when (r.state) {
            State.OK -> "正常"
            State.MODIFIED -> "不一致（${r.actual.take(16)}…）"
            State.UNKNOWN -> "读不到"
        }
    }
}
