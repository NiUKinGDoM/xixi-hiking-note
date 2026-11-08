package com.xixi.hiking.nativeapp.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.xixi.hiking.nativeapp.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 检查更新 —— **只认原生版**，绝不把网页版的版本当成新版本。
 *
 * ★★★2026-11-03 用户：「检测更新不允许检测到 web 版的更新」
 *
 *   真因：`/releases/latest` 指向的是 **网页版仓库**（xixi-hiking）。
 *   网页版走到 1.1.1.x，原生版还在 1.0.7.x —— 4 段版本逐段比，
 *   1.1.x 恒大于 1.0.x，于是原生版**永远**被告知"有新版本"，
 *   点"去下载"还会下到网页版那份 APK。这是最容易把人坑死的一处。
 *
 *   解法：不再取 `/latest`（那是全仓库最新，无法区分平台），改成拉**发布列表**，
 *   逐条筛「带原生版安装包」的那一条：
 *     · 资产名必须含「原生」或 native（原生版发版脚本就是这么命名的）
 *     · 版本号再按 4 段逐段比较，只有真比本机新才提示
 *   一条都不匹配 ⇒ 明确返回 [Result.UpToDate]，提示"已是最新"，
 *   而不是像以前那样把网页版当更新推给用户。
 */
object Updater {

    /**
     * 发布列表（不是 /latest —— 要自己挑哪一条属于本应用）。
     *
     * ★★2026-11-09 应用搬进独立仓库 `NiUKinGDoM/xixi-hiking-note`。
     *   以前和网页版混在 `xixi-hiking` 里，只能拉列表再按资产名筛，
     *   还踩过"把网页版更新推给 App 用户"的坑。现在独立了，列表里全是自己人。
     *
     * ★★2026-11-09 用户：「不需要双仓库更新，只需要更新本仓库就行了」
     *   ⇒ 只查这一个源。之前留的"旧仓库兜底"（API_LEGACY）已删除，
     *     旧仓库里的 App 发布也一并清理了 —— 那边只放网页版。
     */
    private const val RELEASES_API =
        "https://api.github.com/repos/NiUKinGDoM/xixi-hiking-note/releases?per_page=30"

    /**
     * ★★★2026-11-09 新增：版本清单（**主**检查源）
     *
     * 实测结论：手机直连 `api.github.com` 在国内基本不通 ——
     * 本机 curl 三次全是 1ms 断连（HTTP=000），只有挂代理才通。
     * 这就是"检测更新老是失败"的真正原因，不是代码问题。
     *
     * ⇒ 改读 jsDelivr 上的 `version.json`（仓库根的一个 1KB 静态文件）：
     *    · 国内可直连（实测 1.3s），海外也快
     *    · 发布脚本发完版会自动改写这个文件并 push，再调 purge 清缓存
     *    · 拿不到时（CDN 故障/文件缺失）自动回退 GitHub API
     *
     * 文件格式见 `tools/release.py` 的 write_version_json()。
     */
    private const val VERSION_JSON =
        "https://cdn.jsdelivr.net/gh/NiUKinGDoM/xixi-hiking-note@main/version.json"

    /**
     * 资产名里出现这些词才算「本应用的安装包」。
     *
     * ★★★2026-11-09 改成**英文资产名**（`tuxiaoji-v1.1.0.5.apk`），原因很硬：
     *   GitHub 上传时会把中文资产名里的中文字符**整段剥掉** ——
     *   `徒步小记-v1.1.0.5-更新.apk` 传上去变成 `-v1.1.0.5-.apk`（不是乱码，是消失）。
     *   而这里正是靠资产名认安装包的 ⇒ 名字一坏，用户永远收不到更新，
     *   界面上还显示"已是最新"，且**不报任何错**。
     *   ⇒ 上传用纯 ASCII（release.py 的 --asset-name），桌面上的中文文件名不受影响。
     *
     * 旧的三个标记保留：GitHub 上历史 release 的资产名还是中文/原生格式，
     * 删了那些版本就再也找不到了。
     */
    private val NATIVE_MARKERS = listOf("tuxiaoji", "徒步小记", "原生", "native")

    data class Release(
        val tag: String,
        val apkUrl: String,
        val name: String,
        val body: String,
        /** 是否来自 pre-release（测试版）。UI 上要明确标出来，不能让人装得不明不白 */
        val isBeta: Boolean = false
    )

    /** 检查结果：三种状态要分清楚，不能都当"检查失败" */
    sealed interface Result {
        /** 找到比本机新的原生版 */
        data class Update(val release: Release) : Result
        /** 没有原生版更新（含"GitHub 上最新的一条是网页版发布"这种情况） */
        data object UpToDate : Result
        /** 离线：不该算"失败"，这是正常状态，提示要中性 */
        data object Offline : Result
        /** 网络/接口失败 */
        data object Failed : Result
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 检查原生版更新。
     *
     * 返回 [Result.UpToDate] 的三种情形都算正常：
     *   ① 发布列表里没有任何原生版安装包
     *   ② 有，但版本不比本机新
     *   ③ 接口通了但字段缺
     * 只有真正拿不到数据时才返回 [Result.Failed]。
     */
    fun check(ctx: Context): Result {
        // ★2026-11-05 用户：「测试版通道开关删掉，你当我没说」
        //   ⇒ App 侧**只认正式版**，预发布永远不推（哪怕它版本号更高）。
        //   ghrelease.py 仍然可以发 pre-release：那是"先传上去但先不推"，
        //   想验就自己去 GitHub 下载，验完 promote 成正式版，所有人才会收到。
        //
        // ★2026-11-05 用户：「保证离线时候不出现任何报错」——
        //   离线时直接返回 Offline（中性状态），不发请求、不报"检查失败"。
        if (!Net.isOnline(ctx)) return Result.Offline

        // ★2026-11-09 单一源（用户要求不搞双仓库，只看本仓库）
        //
        // ① 先读 jsDelivr 上的版本清单 —— 国内唯一稳定的一条路
        jsonCheck()?.let { return it }
        // ② CDN 不可用（网络/DNS/文件缺失）→ 退回 GitHub API
        return query(RELEASES_API)
    }

    /**
     * 读 jsDelivr 上的 version.json。
     *
     * @return null = 这个源本身不可用（调用方去试下一个）；
     *         UpToDate / Update = 源可用且给出了明确结论。
     */
    private fun jsonCheck(): Result? {
        return try {
            // 带上小时级时间戳：jsDelivr 对 @main 分支有最长 12h 缓存，
            // 换 URL 能让它更倾向于回源（发布脚本还会主动 purge，双保险）。
            val url = VERSION_JSON + "?t=" + (System.currentTimeMillis() / 3_600_000L)
            val req = Request.Builder().url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val txt = res.body?.string() ?: return null
                val o = JSONObject(txt)
                val tag = o.optString("tag", "")
                val apkUrl = o.optString("url", "")
                if (tag.isBlank() || apkUrl.isBlank()) return null
                if (!isNewer(tag, currentVersion())) return Result.UpToDate
                Result.Update(
                    Release(
                        tag = tag,
                        apkUrl = apkUrl,
                        name = o.optString("version", ""),
                        body = o.optString("notes", ""),
                        isBeta = false
                    )
                )
            }
        } catch (e: Throwable) {
            Log.i("hiking", "版本清单不可用，改用 GitHub API：${e.message}")
            null
        }
    }

    /** 查一个仓库的发布列表，找出版本最高且比本机新的那条 */
    private fun query(apiUrl: String): Result {
        val allowBeta = false
        val releases: JSONArray = try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return Result.Failed
                val txt = res.body?.string() ?: return Result.Failed
                JSONArray(txt)
            }
        } catch (e: Throwable) {
            Log.i("hiking", "检查更新失败（已忽略）：${e.message}")
            return Result.Failed
        }

        for (i in 0 until releases.length()) {
            val o = releases.optJSONObject(i) ?: continue
            // 草稿永远不推（GitHub 上连列表都不该出现）
            if (o.optBoolean("draft", false)) continue
            // 预发布 = 测试版：只有开了通道的人才看得到
            val isBeta = o.optBoolean("prerelease", false)
            if (isBeta && !allowBeta) continue

            val tag = o.optString("tag_name", "")
            val name = o.optString("name", "")
            val apkUrl = nativeApkUrl(o) ?: continue   // ← 没有安装包就跳过
            if (tag.isBlank()) continue

            // 版本不比本机新就继续往上找（列表是按时间倒序的，理论上第一条就结束）
            if (!isNewer(tag, currentVersion())) continue

            return Result.Update(
                Release(
                    tag = tag,
                    apkUrl = apkUrl,
                    name = name,
                    body = o.optString("body", ""),
                    isBeta = isBeta
                )
            )
        }
        return Result.UpToDate
    }

    /** 在这条 release 里找原生版安装包；没有就返回 null（= 这是网页版发布） */
    private fun nativeApkUrl(o: JSONObject): String? {
        val assets = o.optJSONArray("assets") ?: return null
        var fallback: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val assetName = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (!assetName.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
            val lower = assetName.lowercase()
            if (NATIVE_MARKERS.any { lower.contains(it) }) return url
            // 兜底：释放标题里写了「原生」也算
            if (fallback == null &&
                NATIVE_MARKERS.any { o.optString("name", "").lowercase().contains(it) }
            ) fallback = url
        }
        return fallback
    }

    /** 兼容旧调用点（只想知道"有没有更新"） */
    fun fetchLatest(ctx: Context): Release? = (check(ctx) as? Result.Update)?.release

    /** 当前版本名，比如 "1.0.7.4" */
    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /**
     * 远端版本是否比本机新。
     *
     * 只比「数字段」，忽略前缀 v 和后面的说明字。逐段比较，缺的段当 0：
     *   1.0.7.9 vs 1.0.7.10 → 前 3 段相同，9 < 10 ⇒ 远端更新（字符串比较会判错）
     */
    fun isNewer(remoteTag: String, localVersion: String = currentVersion()): Boolean {
        val a = nums(remoteTag)
        val b = nums(localVersion)
        if (a.isEmpty()) return false
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun nums(s: String): List<Int> =
        Regex("\\d+").findAll(s).mapNotNull { it.value.toIntOrNull() }.toList()

    /** 打开下载页（浏览器）—— 只在应用内下载失败时兜底用 */
    fun openDownload(ctx: Context, url: String) {
        try {
            val u = url.ifBlank { "https://github.com/NiUKinGDoM/xixi-hiking-note/releases" }
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Throwable) {
            Log.w("hiking", "打不开下载页", e)
        }
    }
}
