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
     * ★但**不能把旧地址直接删掉**：已经装在用户手机上的 v1.1.0.3 及更早版本，
     *   代码里硬编码的还是旧仓库。如果新版本只发新仓库，那些人的"检查更新"
     *   永远查旧仓库 → 永远"已是最新"，而且**不会报错**，静默断供。
     *   ⇒ 过渡期两个源都查，取版本更高的那条。等确认所有用户都升过一轮
     *     （自己手机 + 朋友），再删 [API_LEGACY]。
     */
    private const val API_PRIMARY =
        "https://api.github.com/repos/NiUKinGDoM/xixi-hiking-note/releases?per_page=30"

    /** 过渡期兜底：老仓库。留着是为了让还没升级的老版本能被"带上来" */
    private const val API_LEGACY =
        "https://api.github.com/repos/NiUKinGDoM/xixi-hiking/releases?per_page=30"

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

        // ① 主源（新仓库）
        val primary = query(API_PRIMARY)
        if (primary is Result.Update) return primary

        // ② 主源没有更新 / 失败 → 再问一次老仓库（过渡期兜底，见 API_LEGACY 注释）
        val legacy = query(API_LEGACY)
        return when {
            legacy is Result.Update -> legacy
            primary is Result.Failed && legacy is Result.Failed -> Result.Failed
            else -> Result.UpToDate
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
