package com.xixi.hiking.nativeapp.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 应用内更新：下载 → 校验 → 拉起系统安装器。
 *
 * ── 为什么必须校验签名 ─────────────────────────────────────
 * 「应用内升级」意味着**用网络上的一个文件替换正在运行的程序**。
 * 这条路一旦被利用，用户装进去的就是木马，而且它拥有你 App 的全部权限
 * （读取所有徒步记录、照片、网盘凭据）。
 *
 * 三道闸，缺一不可：
 *   ① HTTPS：GitHub 是，但链路中间任何一跳都不保证
 *   ② **证书指纹**：下载完读出 APK 的签名证书 SHA-256，必须等于
 *      [IntegrityPin.CERT_SHA256]（就是本机自己这个包的指纹）。
 *      不相等 = 这个包不是用同一把私钥签的，装上会"签名冲突"装不进去；
 *      更糟的情况是有人骗你先卸载再装他的包 —— 那一步绝不能引导用户做。
 *   ③ 包名：必须还是本应用的 applicationId，防止被换成别的 App。
 *
 * ── 为什么下载状态是 StateFlow 而不是 Composable 里的 remember ──
 *   用户点了下载可能立刻切到别的页面，甚至锁屏。下载必须在**应用级**继续，
 *   回来再看还在跑。存在 composition 里的话，一离开页面就断了。
 */
object ApkDownloader {

    /** 下载/安装的全过程状态 —— UI 只认这个 */
    sealed class State {
        /** 空闲（没在下、也没有可装的包） */
        object Idle : State()

        /** 下载中。total <= 0 表示服务端没给长度（chunked），UI 只能显示已下载大小 */
        data class Downloading(val done: Long, val total: Long) : State()

        /** 下载完 + 校验通过，正在拉起安装器 */
        data class Installing(val version: String) : State()

        /** 下载完但还没安装（用户拒了安装界面 / 需要先授权未知来源） */
        data class Ready(val path: String, val version: String) : State()

        /** 失败。reason 是给用户看的中文原因，不是异常栈 */
        data class Failed(val reason: String) : State()
    }

    /** 下载目录：cacheDir/downloads —— 系统空间紧张时会自己清，正合适 */
    private const val DIR = "downloads"
    private const val FILE_NAME = "tuxiaoji_update.apk"

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 应用级作用域：不随页面销毁而停。
     * 用 SupervisorJob + 自己的实例，而不是无生命周期的全局作用域 ——
     * 这里确实需要"跨页面活着"，但至少要能 [reset] 掉，不能无限堆积协程。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** 记住下载好的是哪个版本，用来判断"能不能直装不用重下" */
    private var readyVersion: String = ""

    /**
     * ★★2026-11-09 国内加速镜像（用户：「软件内自动更新的国内镜像做了吗？不然会下的很慢」）
     *
     * 直连 GitHub Releases 在国内经常几十 KB/s，6.5MB 的包要等好几分钟。
     * 这两个公共代理是 Web 版实测出来的（2026-09-29，各源 3 次字节数 +
     * sha256 完全一致才敢用）：gh-proxy.com 最快 0.9~3.5s，gh.xmly.dev 备用。
     *
     * 用法：`镜像前缀 + 去掉协议的 GitHub 直链`，例
     *   https://gh-proxy.com/github.com/NiUKinGDoM/xixi-hiking-note/releases/download/v1.1.0.5/tuxiaoji-v1.1.0.5.apk
     *
     * ⚠️ 权衡：这是**第三方服务器**，它能看到你下载了哪个文件（公开内容，不含任何
     *   个人数据）。所以顺序是**镜像先、官方直链永远兜底** —— 代理挂了也能下。
     *   文件完整性不靠代理保证，靠下载完的签名指纹校验（[verify]）。
     */
    private val MIRRORS = listOf(
        "https://gh-proxy.com/",
        "https://gh.xmly.dev/"
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            // 下载大文件不设 writeTimeout，但要能感知"卡死"：靠 readTimeout 兜
            .retryOnConnectionFailure(true)
            .build()
    }

    // ══════════════════════════════════════════════════════════
    //  对外接口
    // ══════════════════════════════════════════════════════════

    /** 开始下载并（校验通过后）自动拉起安装器 */
    fun start(ctx: Context, release: Updater.Release) {
        // 同一个版本已经下好了 → 直装，不重下（省流量，也省得重复校验）
        val cached = cachedApk(ctx)
        if (cached != null && readyVersion == release.tag && _state.value is State.Ready) {
            install(ctx, cached, release.tag)
            return
        }
        if (job?.isActive == true) return   // 正在下，别重入

        val app = ctx.applicationContext
        _state.value = State.Downloading(0, -1)
        job = scope.launch {
            try {
                val file = download(app, release.apkUrl)
                if (file == null) {
                    _state.value = State.Failed("下载失败，请检查网络后重试")
                    return@launch
                }
                // ① 包名 ② 证书指纹 —— 任何一项对不上都不许装
                val problem = verify(app, file)
                if (problem != null) {
                    Log.w("hiking", "更新包校验未通过：$problem")
                    runCatching { file.delete() }
                    _state.value = State.Failed(problem)
                    return@launch
                }
                readyVersion = release.tag
                _state.value = State.Ready(file.absolutePath, release.tag)
                withContext(Dispatchers.Main) { install(app, file, release.tag) }
            } catch (e: Throwable) {
                Log.e("hiking", "下载更新失败", e)
                _state.value = State.Failed("下载失败：${e.message ?: "未知原因"}")
            }
        }
    }

    /** 已下载好就直接装（用户退出安装界面后再点一次） */
    fun installCached(ctx: Context, version: String) {
        val f = cachedApk(ctx)
        if (f == null) {
            _state.value = State.Failed("安装包已失效，请重新下载")
            return
        }
        install(ctx, f, version)
    }

    /** 放弃/重置（用户点"取消"） */
    fun reset() {
        job?.cancel()
        job = null
        readyVersion = ""
        _state.value = State.Idle
    }

    /** 清掉缓存包（装完新版本后旧包没用了） */
    fun clearCache(ctx: Context) {
        runCatching { cachedApk(ctx)?.delete() }
        readyVersion = ""
    }

    fun cachedApk(ctx: Context): File? {
        val f = File(File(ctx.cacheDir, DIR), FILE_NAME)
        return if (f.exists() && f.length() > 1024 * 1024) f else null
    }

    // ══════════════════════════════════════════════════════════
    //  内部实现
    // ══════════════════════════════════════════════════════════

    /**
     * 多源下载：镜像优先 → 官方直链兜底。任一成功即返回，全失败才 null。
     *
     * 为什么不是"只用一个最快的"：公共代理随时可能挂、限流或返回错误页，
     * 而更新是**用户主动点了必须完成**的操作 —— 卡在这里用户只能去浏览器手动下。
     */
    private fun download(ctx: Context, url: String): File? {
        if (url.isBlank()) return null
        val path = url.removePrefix("https://").removePrefix("http://")
        val candidates = ArrayList<String>(MIRRORS.size + 1)
        MIRRORS.forEach { candidates.add(it + path) }
        candidates.add(url)          // ★官方直链永远排最后，兜底

        for ((i, c) in candidates.withIndex()) {
            val tag = if (i < MIRRORS.size) "镜像${i + 1}" else "官方直链"
            Log.i("hiking", "尝试下载（$tag）")
            val f = tryDownload(ctx, c)
            if (f != null) {
                Log.i("hiking", "下载成功（$tag）")
                return f
            }
            Log.w("hiking", "$tag 失败，换下一个源")
        }
        return null
    }

    /** 从单个源下载。失败返回 null（不抛，异常在这里吞掉并记日志） */
    private fun tryDownload(ctx: Context, url: String): File? {
        val dir = File(ctx.cacheDir, DIR).apply { if (!exists()) mkdirs() }
        // 先写 .part，下完再改名 —— 中途失败时不会留下一个"看起来完整"的坏包
        val part = File(dir, "$FILE_NAME.part")
        val target = File(dir, FILE_NAME)
        runCatching { target.delete() }
        runCatching { part.delete() }        // 上一个源留下的半截包要清掉

        var resp: okhttp3.Response? = null
        try {
            resp = client.newCall(
                Request.Builder().url(url)
                    // GitHub 的资产直链会 302 到 objects.githubusercontent.com，OkHttp 自动跟
                    .header("User-Agent", "XiXiHiking/${Updater.currentVersion()}")
                    .build()
            ).execute()
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val total = body.contentLength()
            var done = 0L
            var lastNotify = 0L
            body.byteStream().use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        // 300ms 限频：每帧都发 StateFlow 会让 UI 疯狂重组，反而卡
                        if (now - lastNotify > 300) {
                            lastNotify = now
                            _state.value = State.Downloading(done, total)
                        }
                    }
                    out.flush()
                }
            }
            _state.value = State.Downloading(done, total)
            // 小于 1MB 基本是错误页/半截包（正常包 6~7MB）—— 换下一个源
            if (part.length() < 1024 * 1024) {
                runCatching { part.delete() }
                return null
            }
            if (!part.renameTo(target)) {
                runCatching { part.delete() }
                return null
            }
            return target
        } catch (e: Throwable) {
            Log.w("hiking", "下载中断：${e.message}")
            runCatching { part.delete() }
            return null
        } finally {
            runCatching { resp?.close() }
        }
    }

    /**
     * 校验下载到的包：包名 + 签名证书指纹。
     * 返回 null = 通过；否则返回**给用户看**的中文原因。
     */
    private fun verify(ctx: Context, apk: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            android.content.pm.PackageManager.GET_SIGNATURES
        }
        val info = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return "安装包已损坏，请重新下载"

        // ① 包名必须还是自己 —— 防止被换成同名图标的其他 App
        if (info.packageName != ctx.packageName) {
            return "安装包不是本应用，已丢弃"
        }

        // ② 证书指纹必须一致 —— 这是"有没有人换过包"的最终判据
        val sig = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        } ?: return "安装包没有签名，已丢弃"

        val sha = try {
            MessageDigest.getInstance("SHA-256")
                .digest(sig.toByteArray())
                .joinToString("") { b -> "%02x".format(b) }
        } catch (e: Throwable) {
            return "安装包签名无法校验"
        }

        return if (sha.equals(IntegrityPin.CERT_SHA256, ignoreCase = true)) {
            null
        } else {
            // ★不引导"先卸载再装" —— 那正是一步步把人骗进坑的流程
            "安装包签名与本应用不一致，已丢弃"
        }
    }

    /** 拉起系统安装器（Android 不允许静默安装，这一步必须用户手动点"安装"） */
    private fun install(ctx: Context, apk: File, version: String) {
        try {
            // Android 8+ 首次必须授权"安装未知应用"，没有它 startActivity 会被系统直接拒
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                _state.value = State.Ready(apk.absolutePath, version)
                try {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + ctx.packageName)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (t: Throwable) {
                    Log.w("hiking", "打不开未知来源设置页", t)
                }
                return
            }
            val uri = FileProvider.getUriForFile(
                ctx, ctx.packageName + ".fileprovider", apk
            )
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            _state.value = State.Installing(version)
        } catch (e: Throwable) {
            Log.e("hiking", "拉起安装器失败", e)
            _state.value = State.Failed("无法打开安装界面，请检查系统权限")
        }
    }
}
