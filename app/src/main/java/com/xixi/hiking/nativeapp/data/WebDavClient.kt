package com.xixi.hiking.nativeapp.data

import android.content.Context
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * WebDAV 同步 —— 对齐 Web 版的坚果云方案。
 *
 * Web 版走 fetch + BasicAuth；原生用 OkHttp。
 * 协议完全一样：PUT 上传 / GET 下载 / MKCOL 建目录，所以**两边可以互相读备份**。
 */
object WebDavClient {
    private val PREFS = "hiking_sync"

    data class Config(
        val url: String = "",        // 例：https://dav.jianguoyun.com/dav/xixi-hiking
        val user: String = "",
        val pass: String = ""
    ) {
        val configured: Boolean get() = url.isNotBlank() && user.isNotBlank()
    }

    fun load(ctx: Context): Config {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            url = sp.getString("url", "") ?: "",
            user = sp.getString("user", "") ?: "",
            pass = SecretBox.decrypt(sp.getString("pass", "") ?: "")
        )
    }

    fun save(ctx: Context, c: Config) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("url", c.url.trim())
            .putString("user", c.user.trim())
            .putString("pass", SecretBox.encrypt(c.pass))
            .apply()
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * ★★★2026-11-05 修「上传 404」的真正根因之一：
     *
     *   输入框的提示写着「服务器地址（含目录）」，占位符还给了
     *   `.../dav/xixi-hiking` 这种**带目录**的例子 —— 用户照着填，
     *   而我们这里又自动拼了一次 `xixi-hiking` ⇒ 实际 PUT 到
     *   `.../xixi-hiking/xixi-hiking/xxx.zip`，那个嵌套目录不存在 ⇒ 404。
     *
     *   现在两头都容错：地址里已经带了 `/xixi-hiking` 就先剥掉，只拼一次。
     *   老用户（按旧提示填的）不用改配置，新用户按新提示填也对。
     */
    internal fun stripDir(raw: String): String {
        var b = raw.trim().trimEnd('/')
        while (b.endsWith("/xixi-hiking")) b = b.removeSuffix("/xixi-hiking").trimEnd('/')
        return b
    }

    private fun baseOf(c: Config): String = stripDir(c.url)

    /** 给测试用的拼接（和线上 urlOf 走同一条逻辑） */
    internal fun cloudUrlFor(rawBase: String, name: String): String =
        stripDir(rawBase) + "/xixi-hiking/" + name

    private fun urlOf(c: Config, name: String): String {
        val base = baseOf(c)
        // ★★★2026-11-05 目录对齐 Web 版：Web 一直把备份放在 `服务器/xixi-hiking/` 下，
        //   原生以前直接扔在服务器根目录 ⇒ **两边的云端备份互相看不见**
        //   （Web 的列表只扫 xixi-hiking/，原生扫根目录）。
        //   统一到子目录之后，同一个网盘账号下两边能看到同一批文件。
        return "$base/xixi-hiking/$name"
    }

    /**
     * ★★★2026-11-05 修「上传 404」（用户："云盘 http404 了，但检测显示能连通"）
     *
     *   根因：`urlOf` 把所有文件都放进 `服务器/xixi-hiking/` 子目录（为了和网页版对齐），
     *   但**从来没有创建过这个目录**。WebDAV 的 PUT 到一个不存在的集合 ⇒ 404。
     *
     *   为什么"检测连通"显示正常：它只 PROPFIND 了**根目录**（`c.url`），当然 200。
     *   于是出现最难受的一种状态 —— 测试通过、一上传就 404。
     *
     *   现在：上传和列目录前都先 MKCOL 建这个目录。
     *   405 = 已存在（WebDAV 规范），200/201 = 建好了，都算成功。
     */
    private fun ensureDir(c: Config): Boolean {
        return try {
            val dir = baseOf(c) + "/xixi-hiking"
            val req = Request.Builder()
                .url(dir)
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .method("MKCOL", "".toRequestBody(null))
                .build()
            client().newCall(req).execute().use { resp ->
                // 201 新建成功 / 200 已存在（部分实现）/ 405 已存在（标准）/ 301 有些服务端直接 301
                resp.isSuccessful || resp.code == 405 || resp.code == 301
            }
        } catch (t: Throwable) {
            false
        }
    }

    /** 上传（PUT） */
    fun upload(ctx: Context, name: String, content: String): Pair<Boolean, String> {
        val c = load(ctx)
        if (!c.configured) return false to "还没配置同步"
        if (!ensureDir(c)) return false to "建不了目录 xixi-hiking"
        return try {
            val req = Request.Builder()
                .url(urlOf(c, name))
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .put(content.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client().newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 201 || resp.code == 204) true to "已上传"
                else false to "HTTP ${resp.code}"
            }
        } catch (e: Exception) {
            false to (e.message ?: "网络错误")
        }
    }

    /**
     * ★2026-11-05 上传字节流 —— 完整备份是 zip（含照片 + 回忆册），
     * 当字符串处理会直接损坏二进制。
     */
    fun uploadBytes(ctx: Context, name: String, bytes: ByteArray): Pair<Boolean, String> {
        val c = load(ctx)
        if (!c.configured) return false to "还没配置同步"
        // ★目标目录不存在时 PUT 会 404 —— 先建（已存在返回 405，也算成功）
        if (!ensureDir(c)) return false to "建不了目录 xixi-hiking"
        return try {
            val req = Request.Builder()
                .url(urlOf(c, name))
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .put(bytes.toRequestBody("application/zip".toMediaType()))
                .build()
            client().newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 201 || resp.code == 204) true to "已上传"
                else false to "HTTP ${resp.code}"
            }
        } catch (e: Exception) {
            false to (e.message ?: "网络错误")
        }
    }

    /** ★2026-11-05 下载字节流（zip / json 都能拿，交给导入器自己识别） */
    fun downloadBytes(ctx: Context, name: String): Pair<ByteArray?, String> {
        val c = load(ctx)
        if (!c.configured) return null to "还没配置同步"
        return try {
            val req = Request.Builder()
                .url(urlOf(c, name))
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .get()
                .build()
            client().newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> null to "云端还没有备份"
                    !resp.isSuccessful -> null to "HTTP ${resp.code}"
                    else -> resp.body?.bytes() to "已下载"
                }
            }
        } catch (e: Exception) {
            null to (e.message ?: "网络错误")
        }
    }

    /** 下载（GET）；不存在返回 null */
    fun download(ctx: Context, name: String): Pair<String?, String> {
        val c = load(ctx)
        if (!c.configured) return null to "还没配置同步"
        return try {
            val req = Request.Builder()
                .url(urlOf(c, name))
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .get()
                .build()
            client().newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> null to "云端还没有备份"
                    !resp.isSuccessful -> null to "HTTP ${resp.code}"
                    else -> resp.body?.string() to "已下载"
                }
            }
        } catch (e: Exception) {
            null to (e.message ?: "网络错误")
        }
    }

    /** 连通性测试（对配置地址发 PROPFIND 的简化版：直接取根） */
    fun test(ctx: Context): Pair<Boolean, String> {
        val c = load(ctx)
        if (!c.configured) return false to "先填地址和账号"
        return try {
            val req = Request.Builder()
                .url(baseOf(c) + "/")
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .header("Depth", "0")
                .method("PROPFIND", "".toRequestBody(null))
                .build()
            client().newCall(req).execute().use { resp ->
                when {
                    // ★★★2026-11-05 修「测试通过、一上传就 404」
                    //   以前只要根目录 PROPFIND 通过就报"连通"，但真正写文件的是
                    //   `xixi-hiking/` 子目录 —— 那个目录不存在时上传必 404。
                    //   现在测试分两步：根目录通 → 建/查子目录 → 才算真的能用。
                    resp.isSuccessful -> {
                        if (!ensureDir(c)) return@use false to "能连上，但建不了 xixi-hiking 目录"
                        val dir = baseOf(c) + "/xixi-hiking/"
                        val req2 = Request.Builder()
                            .url(dir)
                            .header("Authorization", Credentials.basic(c.user, c.pass))
                            .header("Depth", "0")
                            .method("PROPFIND", "".toRequestBody(null))
                            .build()
                        client().newCall(req2).execute().use { r2 ->
                            if (r2.isSuccessful) true to "连通，可上传"
                            else false to "能连上，但备份目录不可用（HTTP ${r2.code}）"
                        }
                    }
                    resp.code == 401 -> false to "账号或密码不对"
                    resp.code == 404 -> false to "地址填错了？根目录不存在"
                    else -> false to "HTTP ${resp.code}"
                }
            }
        } catch (e: Exception) {
            false to (e.message ?: "连不上")
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ★★云端备份文件名与 Web 版统一（2026-11-03）
    //
    //  之前原生版固定传一个 `backup.json`，Web 版传的是
    //  `xixi-backup-20261103_001230.json` 这种带时间戳的文件，并且靠
    //  「PROPFIND 列目录 + 按时间取最新」来恢复。
    //  两边命名不一样 ⇒ App 传的 Web 版看不见、Web 版传的 App 也看不见，
    //  「管理云端备份」永远是空的。现在按 Web 版规则来。
    // ══════════════════════════════════════════════════════════════

    /**
     * ★★★2026-11-05 与 Web 版 `SYNC_FILE_PREFIX` / `SYNC_FILE_EXT` **逐字一致**：
     *   `xixi_hiking_backup_<yyyyMMdd_HHmmss>.zip`
     * 原来原生是 `xixi-backup-*.json`（连字符 + json），和 Web 的命名对不上，
     * 于是两边的「云端备份」列表永远只看到自己那一半。
     */
    const val BACKUP_PREFIX = "xixi_hiking_backup_"

    fun backupName(time: Long = System.currentTimeMillis()): String =
        BACKUP_PREFIX + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date(time)) + ".zip"

    data class CloudFile(val name: String, val lastModified: Long, val size: Long)

    /** 云端备份文件（PROPFIND Depth:1 列目录，按时间新→旧） */
    fun listBackups(ctx: Context, includeLegacy: Boolean = true): Pair<List<CloudFile>, String> {
        val c = load(ctx)
        if (!c.configured) return emptyList<CloudFile>() to "还没配置同步"
        // ★2026-11-05 目录不存在时 PROPFIND 会 404 ⇒ 列表永远空，用户以为"云端没备份"。
        //   先建目录（已存在 405 也算成功），保证第一次使用时列表至少能正常返回空数组。
        ensureDir(c)
        return try {
            val body = """<?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:"><d:prop>
                <d:getlastmodified/><d:getcontentlength/>
                </d:prop></d:propfind>"""
            val req = Request.Builder()
                .url(urlOf(c, ""))
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .header("Depth", "1")
                .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            client().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList<CloudFile>() to "HTTP ${resp.code}"
                val xml = resp.body?.string().orEmpty()
                val hrefRe = Regex("<(?:\\w+:)?href>(.*?)</(?:\\w+:)?href>", RegexOption.DOT_MATCHES_ALL)
                val modRe = Regex("<(?:\\w+:)?getlastmodified>(.*?)</(?:\\w+:)?getlastmodified>", RegexOption.DOT_MATCHES_ALL)
                val sizeRe = Regex("<(?:\\w+:)?getcontentlength>(.*?)</(?:\\w+:)?getcontentlength>", RegexOption.DOT_MATCHES_ALL)
                // 每段 <d:response> 单独切，才能把 href 和它的时间对上
                val responses = Regex("<(?:\\w+:)?response>(.*?)</(?:\\w+:)?response>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(xml).map { it.groupValues[1] }.toList()
                val out = mutableListOf<CloudFile>()
                responses.forEach { r ->
                    val rawHref = hrefRe.find(r)?.groupValues?.get(1)?.trim().orEmpty()
                    if (rawHref.isBlank() || rawHref.endsWith("/")) return@forEach
                    val name = try {
                        java.net.URLDecoder.decode(rawHref.substringAfterLast('/'), "UTF-8")
                    } catch (t: Throwable) {
                        rawHref.substringAfterLast('/')
                    }
                    // ★2026-11-05 同时认 zip（Web 版 / 新版原生）和 json（老原生），过渡期都能列出来
                    if (!name.endsWith(".zip", true) && !name.endsWith(".json", true)) return@forEach
                    val mod = parseHttpDate(modRe.find(r)?.groupValues?.get(1)?.trim().orEmpty())
                    val size = sizeRe.find(r)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L
                    out += CloudFile(name, mod, size)
                }
                // ★★2026-11-05 迁移兜底：
                //   老版本原生把备份直接放在服务器**根目录**（xixi-backup-*.json）。
                //   统一到 xixi-hiking/ 之后它们会"凭空消失"，用户以为数据丢了。
                //   子目录里一份都没有时，回根目录捞一次（只认老前缀）。
                if (out.isEmpty() && includeLegacy) out += listLegacyRoot(c)
                // 目录里可能还躺着老版本传的 backup.json，一起列出来，排序仍然按时间
                out.sortedByDescending { it.lastModified } to "共 ${out.size} 份"
            }
        } catch (e: Exception) {
            emptyList<CloudFile>() to (e.message ?: "网络错误")
        }
    }

    /**
     * 老版本备份兜底：列服务器**根目录**里 `xixi-backup-*.json`。
     * 只在 xixi-hiking/ 子目录为空时调用，避免和别人的文件混在一起。
     */
    private fun listLegacyRoot(c: Config): List<CloudFile> {
        return try {
            val body = """<?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:"><d:prop>
                <d:getlastmodified/><d:getcontentlength/>
                </d:prop></d:propfind>"""
            val req = Request.Builder()
                .url(c.url.trim().trimEnd('/') + "/")
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .header("Depth", "1")
                .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            client().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val xml = resp.body?.string().orEmpty()
                val hrefRe = Regex("<(?:\\w+:)?href>(.*?)</(?:\\w+:)?href>", RegexOption.DOT_MATCHES_ALL)
                val modRe = Regex("<(?:\\w+:)?getlastmodified>(.*?)</(?:\\w+:)?getlastmodified>", RegexOption.DOT_MATCHES_ALL)
                val sizeRe = Regex("<(?:\\w+:)?getcontentlength>(.*?)</(?:\\w+:)?getcontentlength>", RegexOption.DOT_MATCHES_ALL)
                Regex("<(?:\\w+:)?response>(.*?)</(?:\\w+:)?response>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(xml).map { it.groupValues[1] }.toList().mapNotNull { r ->
                        val href = hrefRe.find(r)?.groupValues?.get(1)?.trim().orEmpty()
                        if (href.isBlank() || href.endsWith("/")) return@mapNotNull null
                        val nm = try {
                            java.net.URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
                        } catch (t: Throwable) {
                            href.substringAfterLast('/')
                        }
                        if (!nm.startsWith("xixi-backup-")) return@mapNotNull null
                        CloudFile(
                            name = nm,
                            lastModified = parseHttpDate(modRe.find(r)?.groupValues?.get(1)?.trim().orEmpty()),
                            size = sizeRe.find(r)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L
                        )
                    }
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * 上传一份新备份（文件名按 Web 版规则生成）。
     * @return (成功?, 文件名或错误信息)
     */
    /**
     * ★★★2026-11-05 上传一份完整备份（**zip**，与 Web 版同格式）
     *
     * 以前上传的是单 json（不含照片），Web 版拿到也没法恢复照片。
     * 现在打成 zip：xixi-data.json + photos/ + 回忆册.html ——
     * Web 端「云端备份」能直接列出来并恢复，反过来也一样。
     */
    suspend fun uploadBackup(ctx: Context): Pair<Boolean, String> {
        val name = backupName()
        val bytes = try {
            BackupExport.build(
                ctx, BackupExport.Kind.FULL_ZIP,
                BackupExport.DavOpts(includeCfg = true, includePwd = false)
            )
        } catch (t: Throwable) {
            return false to (t.message ?: "打包失败")
        }
        val (ok, msg) = uploadBytes(ctx, name, bytes)
        // ★2026-11-05 对齐 Web 的自动清理：上传成功后只保留最近 **2 份**，
        //   更旧的删掉（Web 端 app-sync.js 里是同一套规则）。
        //   只清 xixi-hiking/ 子目录里的（includeLegacy=false），
        //   根目录那批老文件不动 —— 那是迁移前的历史，不该被自动删。
        if (ok) runCatching { pruneOldBackups(ctx) }
        return if (ok) true to name else false to msg
    }

    /**
     * 下载最新一份云端备份。
     *
     * 顺序：先按 Web 版命名找最新 → 没有就退回老版的 `backup.json`。
     * 「取最新」而不是「取固定名」是 Web 版的行为，也是多设备场景下唯一说得通的。
     */
    fun downloadLatest(ctx: Context): Pair<ByteArray?, String> {
        val (files, msg) = listBackups(ctx)
        files.firstOrNull()?.let { return downloadBytes(ctx, it.name) }
        // 列目录失败 / 空目录：再试一次老文件名（可能是刚升级、云端只有 backup.json）
        val (bytes, _) = downloadBytes(ctx, "backup.json")
        if (bytes != null) return bytes to "已下载（旧文件名 backup.json）"
        return null to msg
    }

    /**
     * ★2026-11-05 云端只留最近 2 份（对齐 Web `listSyncFilesFromCloud` + slice 删除）。
     * 依赖 [listBackups] 已按时间新→旧排序；失败静默（清理失败不该影响上传结果）。
     */
    private fun pruneOldBackups(ctx: Context) {
        val (files, _) = listBackups(ctx, includeLegacy = false)
        if (files.size <= 2) return
        files.drop(2).forEach { f ->
            try {
                deleteFile(ctx, f.name)
            } catch (t: Throwable) {
                // 单个删除失败跳过
            }
        }
    }

    fun deleteFile(ctx: Context, name: String): Pair<Boolean, String> {
        val c = load(ctx)
        if (!c.configured) return false to "还没配置同步"
        return try {
            val req = Request.Builder()
                .url(urlOf(c, name))
                .header("Authorization", Credentials.basic(c.user, c.pass))
                .delete()
                .build()
            client().newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 204) true to "已删除"
                else false to "HTTP ${resp.code}"
            }
        } catch (e: Exception) {
            false to (e.message ?: "网络错误")
        }
    }

    /** HTTP 日期（RFC 1123）→ 毫秒。解析不了就给 0，排序时排最后 */
    private fun parseHttpDate(s: String): Long {
        if (s.isBlank()) return 0L
        val fmts = listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss 'GMT'")
        for (f in fmts) {
            try {
                return java.text.SimpleDateFormat(f, java.util.Locale.US)
                    .parse(s)?.time ?: 0L
            } catch (t: Throwable) {
            }
        }
        return 0L
    }
}
