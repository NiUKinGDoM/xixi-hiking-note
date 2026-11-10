package com.xixi.hiking.nativeapp.data

import android.content.Context
import android.os.Build
import com.xixi.hiking.nativeapp.BuildConfig
import com.xixi.hiking.nativeapp.ui.settings.AppDatabaseRef
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 导出 —— ★★2026-11-04 按 Web 版 `showExportModal` / `performBackupExport` 重做。
 *
 * 用户原话：「再完善一下导出和导入数据，对齐一下 web 版」。
 *
 * ── Web 给了三种导出（原生之前只有一种 JSON）──────────────────
 *   ① **导出完整备份压缩包**（.zip）：数据 + 照片二进制。照片以 `photos/<id>.jpg`
 *      存成文件，比 base64 省约 33% 体积。包内固定有 `xixi-data.json`。
 *   ② **导出纯数据备份**（.html，内嵌 JSON）：不含照片，体积小，日常快速备份。
 *      原生版直接给 .json —— 两边导入器都认（Web 的 extractJson 见到 `{` 就按 JSON 读）。
 *   ③ **导出诊断报告**（.txt）：版本 / 机型 / 最近崩溃 / 操作轨迹，用来定位问题。
 *
 * ── 还有一个「网盘配置」开关 ─────────────────────────────────
 *   Web 的导出弹窗里有：
 *     ☑ 同时包含网盘配置（换机导入后自动填好地址与账号，不含密码）
 *     ☐ 连应用密码一起带上（新机彻底免填；但文件外泄就能访问你的网盘）
 *   原生之前是**无条件**把地址+账号+密码全写进备份 —— 等于每次导出都在泄露密码。
 *   现在按 Web 的默认值来：带配置、不带密码。
 */
object BackupExport {

    /** 三种导出的种类 */
    enum class Kind { FULL_ZIP, DATA_ONLY, DIAGNOSTICS }

    /** 网盘配置的携带方式 */
    data class DavOpts(val includeCfg: Boolean = true, val includePwd: Boolean = false)

    /** 文件名里的时间戳（Web：`yyyyMMddHHmmss`） */
    fun stamp(): String =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())

    fun fileName(kind: Kind): String = when (kind) {
        Kind.FULL_ZIP -> "XiXi徒步备份-${stamp()}.zip"
        Kind.DATA_ONLY -> "XiXi纯数据备份-${stamp()}.json"
        Kind.DIAGNOSTICS -> "XiXi诊断报告-${stamp()}.txt"
    }

    /**
     * 导出主入口：返回要写盘的字节。
     *
     * FULL_ZIP 会去读照片文件，几十 MB 的量级 —— 必须在 IO 线程调用。
     */
    suspend fun build(context: Context, kind: Kind, dav: DavOpts): ByteArray = when (kind) {
        Kind.FULL_ZIP -> buildZip(context, dav)
        Kind.DATA_ONLY -> buildPayload(context, dav, inlinePhotos = true).toByteArray(Charsets.UTF_8)
        Kind.DIAGNOSTICS -> buildDiagnostics(context).toByteArray(Charsets.UTF_8)
    }

    /**
     * 完整备份压缩包（与 Web 的 `buildFullBackupZip` 同结构）：
     * ```
     * xixi-data.json        记录 / 计划 / photoRefs
     * photos/<id>.jpg       照片二进制
     * ```
     * ★这个结构 Web 和原生**双向可读**：原生 BackupParser.parseZip 认 photoRefs，
     *   Web 的 importZipBackup 也是同一套。
     */
    private suspend fun buildZip(context: Context, dav: DavOpts): ByteArray {
        val db = AppDatabaseRef.get(context)
        val refs = JSONObject()
        val out = ByteArrayOutputStream()

        // ① 先收集照片（JSON 里只留 id / 宽高，二进制单独进包）
        data class Blob(val path: String, val bytes: ByteArray)
        val blobs = ArrayList<Blob>()
        val records = db.recordDao().observeAllOnce()
        val photosByRecord = HashMap<String, List<PhotoEntity>>()
        records.forEach { r ->
            val phs = db.photoDao().byRecord(r.id)
            photosByRecord[r.id] = phs
            phs.forEach { ph ->
                val f: File = PhotoStore.file(context, ph.fileName)
                if (f.exists() && f.length() > 0) {
                    val path = "photos/${ph.id}.jpg"
                    refs.put(ph.id, path)
                    blobs.add(Blob(path, f.readBytes()))
                }
            }
        }

        val json = buildPayload(context, dav, inlinePhotos = false, photoRefs = refs).toByteArray(Charsets.UTF_8)

        // ★★2026-11-05 回忆册（对齐 Web 的 buildFullBackupZip）
        //   zip 里除了 json + 照片，还要有一本"能直接读"的册子：
        //   日记排版、A4 分页友好，浏览器 Ctrl+P 就能存成 PDF。
        //   ★★2026-11-10 照片改成**内嵌 base64**（用户：「不要得读取照片才能显示了，
        //     直接显示进去吧，压缩一下就行，但是也得清楚」）——
        //     以前用 `photos/<id>.jpg` 相对路径，HTML 单独发出去（或只留 html）
        //     整页都是破图。现在单文件自带全部照片，zip 里的 photos/ 仍然保留原图。
        val memoir = MemoirHtml.build(
            appTitle = runCatching {
                Prefs.getString(context, Prefs.KEY_APP_TITLE, "徒步小记")
            }.getOrDefault("徒步小记"),
            records = records,
            photosByRecord = photosByRecord,
            plans = db.plannedTripDao().observeAllOnce(),
            payloadJson = String(json, Charsets.UTF_8),
            exportedAt = System.currentTimeMillis(),
            ctx = context
        ).toByteArray(Charsets.UTF_8)

        ZipOutputStream(out).use { zos ->
            // 回忆册放第一个：解压后第一眼看到的就是它
            zos.putNextEntry(ZipEntry("回忆册.html"))
            zos.write(memoir)
            zos.closeEntry()
            // data 文件（Web 解析时按名字找它）
            zos.putNextEntry(ZipEntry("xixi-data.json"))
            zos.write(json)
            zos.closeEntry()
            blobs.forEach { b ->
                zos.putNextEntry(ZipEntry(b.path))
                zos.write(b.bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * 备份 JSON 本体。
     *
     * @param inlinePhotos true = 照片以 base64 内嵌（纯数据/云端用）；
     *                     false = 只写 id+宽高，二进制交给 zip（照片走 [photoRefs]）
     */
    private suspend fun buildPayload(
        context: Context,
        dav: DavOpts,
        inlinePhotos: Boolean,
        photoRefs: JSONObject? = null
    ): String {
        val db = AppDatabaseRef.get(context)
        val root = JSONObject()
        root.put("app", "XiXiHiking")
        root.put("appName", "徒步小记")
        root.put("version", BuildConfig.VERSION_NAME)
        root.put("exportedAt", System.currentTimeMillis())

        val recs = JSONArray()
        db.recordDao().observeAllOnce().forEach { r ->
            val photos = JSONArray()
            db.photoDao().byRecord(r.id).forEach { ph ->
                if (inlinePhotos) {
                    val b64 = PhotoStore.toBase64(context, ph.fileName)
                    if (b64 != null) {
                        photos.put(JSONObject().apply {
                            put("id", ph.id)
                            put("data", "data:image/jpeg;base64,$b64")
                            put("w", ph.width)
                            put("h", ph.height)
                        })
                    }
                } else if (photoRefs?.has(ph.id) == true) {
                    photos.put(JSONObject().apply {
                        put("id", ph.id)
                        put("w", ph.width)
                        put("h", ph.height)
                    })
                }
            }
            recs.put(recordToJson(r, photos))
        }
        root.put("records", recs)

        val plans = JSONArray()
        db.plannedTripDao().observeAllOnce().forEach { p ->
            plans.put(planToJson(p))
        }
        root.put("plannedTrips", plans)

        photoRefs?.let { root.put("photoRefs", it) }

        // 网盘配置：按开关来（对齐 Web 的两级勾选）
        val cfg = WebDavClient.load(context)
        if (dav.includeCfg && cfg.configured) {
            root.put("webdav", JSONObject().apply {
                put("url", cfg.url)
                put("user", cfg.user)
                put("pass", if (dav.includePwd) cfg.pass else "")
            })
        }
        return root.toString(2)
    }

    /**
     * ★2026-11-05 把「记录 → JSON」抽成纯函数（原本内联在 buildPayload 里）。
     *
     * 抽出来的唯一理由是**可测**：备份最怕的不是导出失败，而是「导出成功但少写了一个
     * 字段」—— 换手机恢复之后才发现里程没了、小日记没了。那种 bug 没有任何异常，
     * 只能靠「导出 → 解析 → 逐字段比对」的往返测试来钉。
     *
     * 所以导出这条链路必须只有一份实现：线上导出和测试用的是同一个 [recordToJson]。
     */
    internal fun recordToJson(r: RecordEntity, photos: JSONArray): JSONObject =
        JSONObject().apply {
            put("id", r.id); put("name", r.name); put("distance", r.distance)
            put("duration", r.duration); put("elevation", r.elevation)
            put("difficulty", r.difficulty)
            put("mood", r.mood); put("weather", r.weather); put("companions", r.companions)
            put("notes", r.notes); put("createdAt", r.createdAt); put("updatedAt", r.updatedAt)
            put("photos", photos)
        }

    /** ★2026-11-05 同上：计划 → JSON 的唯一实现 */
    internal fun planToJson(p: PlannedTripEntity): JSONObject =
        JSONObject().apply {
            put("id", p.id); put("name", p.name); put("plannedDate", p.plannedDate)
            put("location", p.location); put("note", p.note)
            put("difficulty", p.difficulty); put("createdAt", p.createdAt)
            put("elevation", p.elevation)
        }

    /**
     * 诊断报告（Web `exportDiagnostics` 的等价物）：
     * 版本 + 机型 + 上次崩溃堆栈 + 最近操作轨迹。
     * 只用于定位问题，**不含任何记录内容/照片**。
     */
    private fun buildDiagnostics(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("XiXi 徒步小记 · 诊断报告")
        sb.appendLine("生成时间：").appendLine(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
        sb.appendLine()
        sb.appendLine("── 版本 ──")
        sb.appendLine("versionName：${BuildConfig.VERSION_NAME}")
        sb.appendLine("versionCode：${BuildConfig.VERSION_CODE}")
        sb.appendLine("buildType：${BuildConfig.BUILD_TYPE}")
        sb.appendLine()
        sb.appendLine("── 设备 ──")
        sb.appendLine("机型：${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        sb.appendLine("ABI：${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine()
        sb.appendLine("── 上次崩溃 ──")
        val crash = CrashGuard.readLast(context)
        sb.appendLine(crash ?: "（没有崩溃记录）")
        sb.appendLine()
        sb.appendLine("── 操作轨迹 ──")
        sb.appendLine("上次运行最后一步：" + (Trace.lastOfPreviousRun() ?: "（无）"))
        sb.appendLine("本次最后一步：" + (Trace.last() ?: "（无）"))
        return sb.toString()
    }
}
