package com.xixi.hiking.nativeapp.data

import android.util.Base64
import org.json.JSONObject

/**
 * ★备份解析（2026-11-02 从 SettingsScreen 里抽出来）
 *
 * 为什么要拆：原来「解析 JSON」和「写数据库」写在同一个函数里，结果是
 * **字段兼容性根本没法验证** —— 想测「Web 版计划的日期字段叫 createdAt」，
 * 就必须先起一个 Room 数据库，而 Room 在 JVM 测试环境里又有线程限制。
 *
 * 拆开之后：解析是纯函数（只吃字符串、吐数据结构），可以放心断言；
 * 写库那部分薄到几乎不会错。
 *
 * 兼容性要点（都是踩过的坑）：
 * 1. 计划的「计划时间」：Web 版放 `createdAt`，原生版叫 `plannedDate` —— 两个都认
 * 2. 照片数据字段：可能叫 data / url / base64 / blob
 * 3. 照片是否带 `data:image/jpeg;base64,` 前缀：两种都可能
 * 4. 备注：Web 版叫 `notes`，早期可能写成 `note`
 * 5. 没有 id 的行直接跳过（主键为空会互相覆盖，等于静默丢数据）
 */
object BackupParser {

    data class ParsedPhoto(
        val id: String,
        val bytes: ByteArray,
        val w: Int,
        val h: Int
    ) {
        // ByteArray 的 equals 是引用比较，data class 需要手动改
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = id.hashCode()
    }

    data class ParsedRecord(
        val entity: RecordEntity,
        val photos: List<ParsedPhoto>
    )

    data class Parsed(
        val records: List<ParsedRecord>,
        val plans: List<PlannedTripEntity>,
        val skipped: Int,
        /** 解析里遇到但没能处理的照片数（base64 损坏之类） */
        val badPhotos: Int,
        /** ★备份里带的网盘配置（Web 版叫 syncConfig，原生版叫 webdav）。没有就是 null */
        val dav: WebDavClient.Config? = null
    ) {
        val photoCount: Int get() = records.sumOf { it.photos.size }
        val isEmpty: Boolean get() = records.isEmpty() && plans.isEmpty()
    }

    class WrongAppException(val appTag: String) :
        Exception("这个文件不是本应用导出的（app=$appTag）")

    /**
     * 解析备份文本。
     * @throws WrongAppException 文件顶层 app 标记是别的应用
     * @throws org.json.JSONException 不是合法 JSON
     */
    /**
     * @param photoResolver 可选：当照片数据不在 JSON 里（zip 完整备份把照片存成二进制文件）
     *                      时，用照片 id 去取字节。返回 null 表示没有。
     */
    fun parse(text: String, photoResolver: ((String) -> ByteArray?)? = null): Parsed {
        val root = JSONObject(extractJson(text))

        val appTag = root.optString("app", "")
        if (appTag.isNotBlank() && appTag != "XiXiHiking") throw WrongAppException(appTag)

        var skipped = 0
        var badPhotos = 0
        // Web 版把真正的 dataURL 放在顶层 photos 映射里（records[].photos 只是 id 数组）
        val photoMap = root.optJSONObject("photos")

        // ── 记录 ──
        val records = mutableListOf<ParsedRecord>()
        root.optJSONArray("records")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) { skipped++; continue }

                val photos = mutableListOf<ParsedPhoto>()
                o.optJSONArray("photos")?.let { phArr ->
                    for (j in 0 until phArr.length()) {
                        // ★Web 版有两种写法，必须都认：
                        //   ① 对象数组：[{id, data, w, h}]  ← 原生版自己导出的格式
                        //   ② id 字符串数组：["p1","p2"] + 顶层 photos 映射 {"p1": "data:..."}
                        //   老代码只处理 ①，遇到 Web 版导出（②）会把整批照片静默跳过。
                        val asObj = phArr.optJSONObject(j)
                        val pid = asObj?.optString("id").orEmpty().ifBlank {
                            if (asObj == null) phArr.optString(j, "") else ""
                        }
                        val raw = if (asObj != null) {
                            firstNonBlank(
                                asObj.optString("data"), asObj.optString("url"),
                                asObj.optString("base64"), asObj.optString("blob")
                            )
                        } else {
                            photoMap?.optString(pid, "").orEmpty()
                        }

                        // 先试 JSON 里的 base64；没有就试外部解析器（zip 里的二进制文件）
                        var bytes: ByteArray? = null
                        if (raw.isNotBlank()) {
                            val b64 = if (raw.startsWith("data:")) raw.substringAfter("base64,", raw) else raw
                            bytes = try {
                                Base64.decode(b64, Base64.DEFAULT)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bytes == null || bytes.isEmpty()) {
                            bytes = photoResolver?.invoke(pid)
                        }
                        if (bytes == null || bytes.isEmpty()) { badPhotos++; continue }
                        photos.add(
                            ParsedPhoto(
                                id = pid.ifBlank { "ph${System.currentTimeMillis()}_$i$j" },
                                bytes = bytes,
                                w = asObj?.optInt("w", 0) ?: 0,
                                h = asObj?.optInt("h", 0) ?: 0
                            )
                        )
                    }
                }

                records.add(
                    ParsedRecord(
                        entity = RecordEntity(
                            id = id,
                            name = o.optString("name"),
                            distance = o.optDouble("distance", 0.0),
                            duration = o.optInt("duration", 0),
                            elevation = o.optInt("elevation", 0),
                            difficulty = o.optInt("difficulty", 3),
                            mood = o.optString("mood"),
                            weather = o.optString("weather"),
                            companions = o.optString("companions"),
                            notes = firstNonBlank(o.optString("notes"), o.optString("note")),
                            createdAt = o.optString("createdAt"),
                            updatedAt = o.optString("updatedAt").ifBlank { o.optString("createdAt") },
                            photosJson = (o.optJSONArray("photos")?.toString() ?: "[]")
                        ),
                        photos = photos
                    )
                )
            }
        }

        // ── 计划 ──
        val plans = mutableListOf<PlannedTripEntity>()
        root.optJSONArray("plannedTrips")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) { skipped++; continue }
                // ★Web 版把计划时间放在 createdAt（ISO 字符串），原生版字段叫 plannedDate
                val date = o.optString("plannedDate").ifBlank {
                    o.optString("createdAt").take(10)
                }
                plans.add(
                    PlannedTripEntity(
                        id = id,
                        name = o.optString("name"),
                        plannedDate = date,
                        location = o.optString("location"),
                        note = o.optString("note"),
                        difficulty = o.optInt("difficulty", 3),
                        createdAt = o.optString("createdAt"),
                        elevation = o.optInt("elevation", 0)
                    )
                )
            }
        }

        // ★★网盘配置也一起解析（2026-11-02）：zip 完整备份里同样带，不能只在纯 JSON 分支读。
        //   Web 版把它叫 `syncConfig{server,username,password}`，原生版叫 `webdav{url,user,pass}`，
        //   密码还分成「明文」和「xk1: 混淆」两种 —— 见 readDavConfig。
        val dav = runCatching { readDavConfig(root) }.getOrNull()

        return Parsed(
            records = records, plans = plans, skipped = skipped,
            badPhotos = badPhotos, dav = dav
        )
    }

    /** zip 完整备份的魔数：PK\x03\x04 */
    fun looksLikeZip(b: ByteArray): Boolean =
        b.size > 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() &&
            b[2] == 0x03.toByte() && b[3] == 0x04.toByte()

    /**
     * ★解 zip 完整备份（Web 版「导出完整备份压缩包」的产物）
     *
     * 结构：
     * ```
     * 回忆册.html          纯文字版本，忽略
     * photos/<id>.jpg     照片二进制
     * xixi-data.json      记录 / 计划 / photoRefs（照片 id → 路径）
     * ```
     *
     * ★为什么必须支持：Web 版的「纯数据备份」是 .html 且**不含照片**。
     *   想把照片一起搬到原生版，只有 zip 这一条路。
     */
    fun parseZip(zipBytes: ByteArray): Parsed {
        val entries = HashMap<String, ByteArray>()
        var jsonText: String? = null

        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val name = e.name
                val data = zis.readBytes()
                if (name.endsWith("xixi-data.json")) {
                    jsonText = String(data, Charsets.UTF_8)
                } else if (data.isNotEmpty()) {
                    entries[name] = data
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }

        val text = jsonText
            ?: throw IllegalArgumentException(
                "压缩包里没找到数据文件，可能不是本应用导出的备份"
            )

        // photoRefs: { "<photoId>": "photos/<photoId>.jpg" }
        val refs = JSONObject(text).optJSONObject("photoRefs")

        return parse(text) { pid ->
            val path = refs?.optString(pid, "").orEmpty()
            when {
                path.isNotBlank() && entries.containsKey(path) -> entries[path]
                entries.containsKey("photos/$pid.jpg") -> entries["photos/$pid.jpg"]
                else -> null
            }
        }
    }

    /**
     * 从文件内容里取出 JSON。
     *
     * Web 版「导出纯数据备份」产出的其实是 **.html**（一个人可读的回忆册），
     * JSON 以内嵌 script 的形式放在里面：
     *   `<script id="backup-data" type="application/json">{...}</script>`
     *
     * 不做这层提取的话，用户从网页版导出的备份在原生版里会被判成「格式不对」。
     */
    fun extractJson(text: String): String {
        val t = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (t.startsWith("{")) return t

        val marker = "id=\"backup-data\""
        val idx = t.indexOf(marker)
        if (idx < 0) {
            throw IllegalArgumentException(
                "这个文件读不出数据，请选择本应用导出的备份（zip / json）"
            )
        }
        val tagStart = t.indexOf(">", idx)
        val end = t.indexOf("</script>", tagStart)
        if (tagStart < 0 || end < 0) {
            throw IllegalArgumentException("备份文件不完整，可能下载被中断了，建议重新下载")
        }
        return t.substring(tagStart + 1, end).trim()
    }

    private fun firstNonBlank(vararg xs: String): String =
        xs.firstOrNull { it.isNotBlank() } ?: ""
}

/**
 * ★★从备份里取网盘配置（2026-11-02 第二次修）
 *
 * 上一版只认 `webdav{url,user,pass}` —— 那是**原生版自己**导出的键。
 * Web 版导出的键叫 `syncConfig{server,username,password}`，所以拿 Web 版备份导入时
 * 这一段永远读不到，用户看到的就是「导入完成了，但账号密码还是空的」。
 *
 * 现在两种都认，并且处理 Web 版的三个差异：
 *   ① 键名      syncConfig / webdav / sync.webdav / settings.webdav / dav / 顶层 davUrl
 *   ② 地址语义  Web 存的是 WebDAV **根**（https://dav.jianguoyun.com/dav/），
 *              文件落在根下的 `xixi-hiking/`；原生存的是**含目录**的地址。这里补齐目录。
 *   ③ 密码      Web 存的是 `xk1:` + base64(XOR('xixi')) 混淆串，直接写进原生会认证失败。
 *              这里解回来（算法见 app-core.js encPwd/decPwd，两边必须一致）。
 *
 * 取不到返回 null（不影响记录导入）。
 */
fun parseWebDav(text: String): WebDavClient.Config? = try {
    readDavConfig(org.json.JSONObject(BackupParser.extractJson(text)))
} catch (t: Throwable) {
    null
}

/** 从已经解析好的根对象里读网盘配置。zip / json / html 走的都是这一条。 */
internal fun readDavConfig(root: JSONObject): WebDavClient.Config? {
    // ① Web 版：syncConfig{server, username, password}
    root.optJSONObject("syncConfig")?.let { sc ->
        val server = sc.optString("server").ifBlank { sc.optString("url") }
        val user = sc.optString("username").ifBlank { sc.optString("user") }
        val pass = decPwd(sc.optString("password").ifBlank { sc.optString("pass") })
        if (server.isNotBlank() && user.isNotBlank()) {
            return WebDavClient.Config(
                url = normalizeDavUrl(server),
                user = user.trim(),
                pass = pass
            )
        }
    }

    // ② 原生版 / 老备份：webdav{url,user,pass}，可能嵌在 sync / settings 里
    val candidates = listOf(
        root.optJSONObject("webdav"),
        root.optJSONObject("dav"),
        root.optJSONObject("sync")?.optJSONObject("webdav"),
        root.optJSONObject("settings")?.optJSONObject("webdav")
    ).filterNotNull()
    for (o in candidates) {
        val url = o.optString("url").ifBlank { o.optString("davUrl") }
        val user = o.optString("user").ifBlank { o.optString("username").ifBlank { o.optString("davUser") } }
        val pass = decPwd(
            o.optString("pass").ifBlank {
                o.optString("password").ifBlank { o.optString("davPass") }
            }
        )
        if (url.isNotBlank() && user.isNotBlank()) {
            // 已经是完整地址（含目录）就原样，否则补 xixi-hiking
            return WebDavClient.Config(url = normalizeDavUrl(url, keepPath = true), user = user.trim(), pass = pass)
        }
    }

    // ③ 顶层平铺的老格式
    val u = root.optString("davUrl")
    val n = root.optString("davUser")
    return if (u.isNotBlank() && n.isNotBlank()) {
        WebDavClient.Config(normalizeDavUrl(u), n.trim(), decPwd(root.optString("davPass")))
    } else null
}

/**
 * 把备份里的服务器地址补成原生版要的「含目录」形式。
 *
 * - 缺协议头 → 补 https://
 * - 末尾补 `/`
 * - [keepPath] = false（Web 版语义：给的是根）→ 如果还没带 xixi-hiking 就拼上
 * - [keepPath] = true（原生版语义：给的是完整目录）→ 只有在明显是 WebDAV 根时才补
 */
internal fun normalizeDavUrl(raw: String, keepPath: Boolean = false): String {
    var s = raw.trim()
    if (s.isBlank()) return s
    if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
    if (!s.endsWith("/")) s += "/"
    if (s.trimEnd('/').endsWith("xixi-hiking")) return s.trimEnd('/')
    // 根地址（以 /dav 或 / 结尾）本来就不该直接 PUT 文件，按 Web 版的规则补子目录
    val isRoot = !keepPath || s.trimEnd('/').endsWith("/dav")
    return if (isRoot) s + "xixi-hiking" else s.trimEnd('/')
}

/**
 * Web 版密码混淆的解码：`xk1:` + base64( XOR(明文, "xixi") )。
 * 对应 app-core.js 里的 decPwd —— 两边算法必须一字不差，否则导入的密码是乱的。
 * 非 `xk1:` 前缀视为明文（老备份）。
 */
internal fun decPwd(s: String): String {
    if (s.isBlank()) return ""
    if (!s.startsWith("xk1:")) return s
    return try {
        val raw = String(Base64.decode(s.substring(4), Base64.DEFAULT), Charsets.UTF_8)
        val key = "xixi"
        buildString(raw.length) {
            raw.forEachIndexed { i, c ->
                append(((c.code xor key[i % key.length].code).toChar()))
            }
        }
    } catch (t: Throwable) {
        // 解不开就当没有密码：配置能恢复，用户补填一次即可（Web 版也是这个降级）
        ""
    }
}
