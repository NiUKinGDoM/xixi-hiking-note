package com.xixi.hiking.nativeapp

import com.xixi.hiking.nativeapp.data.BackupParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ★备份导入的格式兼容性测试（2026-11-02）
 *
 * 为什么必须有：用户要把 Web 版的老数据倒进原生版，而两边的字段名**不一样**。
 * 这类错误不会抛异常 —— 会「导入成功」，然后计划日期全空、照片全丢。
 * 只能靠断言钉死。
 *
 * 测的是 [BackupParser]（纯函数，不碰数据库），所以能跑在普通 JVM 上。
 */
// ★必须用 Robolectric：org.json 在纯 JVM 里是空实现，JSONObject(text) 直接 NPE。
//   这一层只做字符串解析、不碰数据库，所以不会有 Room 的线程问题。
@RunWith(RobolectricTestRunner::class)
// ★必须显式指定 SDK：项目 targetSdk=36，但 Robolectric 4.13 最高只到 34，
//   不写这行会直接 "Package targetSdkVersion=36 > maxSdkVersion=34" 初始化失败。
@org.robolectric.annotation.Config(sdk = [33])
class ImportCompatTest {

    /** 一张最小合法 PNG（1×1），用 base64 充当"照片数据" */
    private val tinyB64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

    @Test
    fun webBackup_usesCreatedAtForPlanDate_isMapped() {
        // Web 版 buildFullBackupPayload 的结构：计划时间在 createdAt，没有 plannedDate
        val json = """
        {
          "app": "XiXiHiking",
          "appName": "徒步小记",
          "version": "1.4.10.2",
          "records": [],
          "plannedTrips": [
            {
              "id": "web-t1",
              "name": "鳌太穿越",
              "createdAt": "2026-11-15T08:00:00.000Z",
              "location": "太白山",
              "note": "带够装备",
              "difficulty": 5,
              "elevation": 3767
            }
          ]
        }
        """.trimIndent()

        val parsed = BackupParser.parse(json)
        assertEquals("计划条数", 1, parsed.plans.size)
        val t = parsed.plans.first()
        // ★这一行是最关键的：Web 版没有 plannedDate，必须从 createdAt 兜出来
        assertEquals("计划日期（Web 版存在 createdAt 里）", "2026-11-15", t.plannedDate)
        assertEquals("计划海拔", 3767, t.elevation)
        assertEquals("难度", 5, t.difficulty)
        assertEquals("地点", "太白山", t.location)
    }

    @Test
    fun recordAllFields_areMapped() {
        val json = """
        {
          "app": "XiXiHiking",
          "records": [
            {
              "id": "web-r1",
              "name": "太白山拔仙台",
              "distance": 18.5,
              "duration": 540,
              "elevation": 3767,
              "difficulty": 5,
              "mood": "累但值",
              "weather": "多云",
              "companions": "老爸",
              "notes": "凌晨四点出发",
              "createdAt": "2026-10-01T04:00:00.000Z",
              "updatedAt": "2026-10-01T20:00:00.000Z",
              "photos": [ { "id": "p1", "data": "data:image/png;base64,$tinyB64", "w": 1, "h": 1 } ]
            }
          ]
        }
        """.trimIndent()

        val parsed = BackupParser.parse(json)
        assertEquals(1, parsed.records.size)
        val r = parsed.records.first()
        assertEquals("名称", "太白山拔仙台", r.entity.name)
        assertEquals("里程", 18.5, r.entity.distance, 0.001)
        assertEquals("用时", 540, r.entity.duration)
        assertEquals("海拔", 3767, r.entity.elevation)
        assertEquals("备注（Web 版字段名 notes）", "凌晨四点出发", r.entity.notes)
        assertEquals("同行人", "老爸", r.entity.companions)
        assertEquals("照片条数", 1, r.photos.size)
        assertTrue("照片二进制应被解码出来", r.photos.first().bytes.isNotEmpty())
        assertEquals("照片宽", 1, r.photos.first().w)
    }

    @Test
    fun photo_without_dataUrlPrefix_isAccepted() {
        // 有些导出路径存的是裸 base64（没有 data:image/...;base64, 前缀）
        val json = """
        {
          "app": "XiXiHiking",
          "records": [ {
            "id": "r2", "name": "测试", "createdAt": "2026-10-02T04:00:00.000Z",
            "photos": [ { "id": "p2", "blob": "$tinyB64", "w": 1, "h": 1 } ]
          } ]
        }
        """.trimIndent()

        val parsed = BackupParser.parse(json)
        assertEquals("裸 base64（blob 字段名）也应识别", 1, parsed.records.first().photos.size)
        assertEquals("不应记为损坏", 0, parsed.badPhotos)
    }

    @Test
    fun brokenBase64_isCountedNotThrown() {
        val json = """
        {
          "app": "XiXiHiking",
          "records": [ {
            "id": "r3", "name": "测试", "createdAt": "2026-10-02T04:00:00.000Z",
            "photos": [ { "id": "bad", "data": "data:image/jpeg;base64,@@@notbase64@@@" } ]
          } ]
        }
        """.trimIndent()

        val parsed = BackupParser.parse(json)
        assertEquals("坏照片不该进列表", 0, parsed.records.first().photos.size)
        assertTrue("应记一笔损坏数，便于提示用户", parsed.badPhotos >= 1)
    }

    @Test
    fun rowsWithoutId_areSkippedAndCounted() {
        val json = """
        {
          "app": "XiXiHiking",
          "records": [
            { "name": "没有 id 的记录", "createdAt": "2026-10-03T04:00:00.000Z" },
            { "id": "good", "name": "正常记录", "createdAt": "2026-10-04T04:00:00.000Z" }
          ],
          "plannedTrips": [ { "name": "没有 id 的计划" } ]
        }
        """.trimIndent()

        val parsed = BackupParser.parse(json)
        assertEquals("只保留有 id 的", 1, parsed.records.size)
        assertEquals("计划同理", 0, parsed.plans.size)
        assertEquals("应记两条被跳过", 2, parsed.skipped)
    }

    @Test
    fun otherAppFile_throwsWithClearReason() {
        val json = """{ "app": "SomeOtherApp", "records": [] }"""
        val ex = runCatching { BackupParser.parse(json) }.exceptionOrNull()
        assertTrue("应抛 WrongAppException，实际：$ex", ex is BackupParser.WrongAppException)
        assertTrue("信息里要说明是哪个 app", ex!!.message!!.contains("SomeOtherApp"))
    }

    @Test
    fun nativeExport_roundTripsThroughParser() {
        // 原生版自己导出的格式（app 标记 + plannedDate + elevation）应能原样解析回来
        val json = """
        {
          "app": "XiXiHiking",
          "appName": "徒步小记",
          "version": "1.3.6",
          "records": [ { "id": "n1", "name": "本机记录", "distance": 3.0, "duration": 60,
            "elevation": 500, "difficulty": 2, "mood": "", "weather": "", "companions": "",
            "notes": "", "createdAt": "2026-10-05T04:00:00.000Z",
            "updatedAt": "2026-10-05T04:00:00.000Z", "photos": [] } ],
          "plannedTrips": [ { "id": "np1", "name": "本机计划", "plannedDate": "2026-12-01",
            "location": "", "note": "", "difficulty": 3, "createdAt": "2026-11-01T00:00:00.000Z",
            "elevation": 1200 } ]
        }
        """.trimIndent()

        val parsed = BackupParser.parse(json)
        assertEquals(1, parsed.records.size)
        assertEquals(1, parsed.plans.size)
        assertEquals("plannedDate 优先于 createdAt", "2026-12-01", parsed.plans.first().plannedDate)
        assertEquals(1200, parsed.plans.first().elevation)
    }

    // ══ Web 版真实导出格式（HTML 回忆册 + 顶层 photos 映射）══

    private val webHtmlBackup = """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8"><title>XiXiの徒步小记 · 回忆册</title></head>
        <body>
        <h1>回忆册</h1>
        <p>这是一份纯数据备份，用浏览器打开就能读。</p>
        <script id="backup-data" type="application/json">
        {"app":"XiXiHiking","appName":"徒步小记","version":"1.4.10.2",
         "records":[{"id":"html-r1","name":"翠华山天池","distance":6.2,"duration":180,
           "elevation":1200,"difficulty":2,"mood":"轻松","weather":"晴","companions":"",
           "notes":"湖边坐着发呆","createdAt":"2026-09-20T02:00:00.000Z",
           "updatedAt":"2026-09-20T02:00:00.000Z",
           "photos":["hp1"]}],
         "plannedTrips":[{"id":"html-t1","name":"华山北峰","createdAt":"2026-12-05T00:00:00.000Z",
           "location":"渭南","note":"","difficulty":4,"elevation":1614}],
         "photos":{"hp1":"data:image/png;base64,TINY"}}
        </script>
        </body></html>
    """.trimIndent().replace("TINY", tinyB64)

    @Test
    fun htmlBackup_isParsed() {
        // Web 版「导出纯数据备份」产出的就是 .html，JSON 嵌在 <script id="backup-data"> 里
        val parsed = BackupParser.parse(webHtmlBackup)
        assertEquals("记录应能从 HTML 里取出来", 1, parsed.records.size)
        assertEquals("翠华山天池", parsed.records.first().entity.name)
        assertEquals("计划也应在", 1, parsed.plans.size)
        assertEquals("计划日期从 createdAt 兜出", "2026-12-05", parsed.plans.first().plannedDate)
    }

    @Test
    fun webPhotoIdArray_isResolvedFromTopLevelMap() {
        // ★Web 版 records[].photos 是 id 字符串数组，dataURL 在顶层 photos 映射里。
        //   老代码只认对象数组，会把整批照片静默丢掉。
        val parsed = BackupParser.parse(webHtmlBackup)
        val photos = parsed.records.first().photos
        assertEquals("照片应通过顶层映射解析出来", 1, photos.size)
        assertEquals("照片 id 应保留", "hp1", photos.first().id)
        assertTrue("照片二进制非空", photos.first().bytes.isNotEmpty())
    }

    @Test
    fun nonBackupHtml_givesActionableError() {
        val ex = runCatching { BackupParser.parse("<html><body>随便一个网页</body></html>") }
            .exceptionOrNull()
        assertTrue("应报错，实际：$ex", ex is IllegalArgumentException)
        // ★2026-11-05 文案压到 10 字以内之后，错误信息不再列举扩展名，
        //   但仍然必须"告诉用户下一步做什么"（不是一句"失败"就完事）。
        assertTrue(
            "错误信息要告诉用户该怎么办，实际：${ex!!.message}",
            ex.message!!.contains("格式") || ex.message!!.contains("备份") ||
                ex.message!!.contains(".html") || ex.message!!.contains(".json")
        )
    }

    // ══ zip 完整备份（唯一能带照片的格式）══

    /** 造一个和 Web 版 buildFullBackupZip 同结构的 zip */
    private fun buildWebZip(): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            // 回忆册（纯文字，导入时忽略）
            zos.putNextEntry(java.util.zip.ZipEntry("回忆册.html"))
            zos.write("<html><body>回忆册</body></html>".toByteArray())
            zos.closeEntry()

            // 照片二进制
            val photoBytes = java.util.Base64.getDecoder().decode(tinyB64)
            zos.putNextEntry(java.util.zip.ZipEntry("photos/zip-p1.jpg"))
            zos.write(photoBytes)
            zos.closeEntry()

            // 数据文件（注意：records[].photos 是 **id 字符串数组**，真正的字节在 photos/ 目录）
            val data = """
            {"app":"XiXiHiking","appName":"徒步小记","version":"1.4.10.2","zip":true,
             "records":[{"id":"zip-r1","name":"子午峪小环线","distance":9.8,"duration":300,
               "elevation":800,"difficulty":3,"mood":"","weather":"晴","companions":"",
               "notes":"","createdAt":"2026-08-15T03:00:00.000Z","updatedAt":"2026-08-15T03:00:00.000Z",
               "photos":["zip-p1"]}],
             "plannedTrips":[{"id":"zip-t1","name":"黄柏塬","createdAt":"2026-11-30T00:00:00.000Z",
               "location":"太白县","note":"","difficulty":2,"elevation":1500}],
             "photoRefs":{"zip-p1":"photos/zip-p1.jpg"}}
            """.trimIndent()
            zos.putNextEntry(java.util.zip.ZipEntry("xixi-data.json"))
            zos.write(data.toByteArray())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @Test
    fun zipBackup_detectedAndParsed() {
        val zip = buildWebZip()
        assertTrue("应识别为 zip", BackupParser.looksLikeZip(zip))

        val parsed = BackupParser.parseZip(zip)
        assertEquals("记录", 1, parsed.records.size)
        assertEquals("子午峪小环线", parsed.records.first().entity.name)
        assertEquals("计划", 1, parsed.plans.size)
        assertEquals("计划日期从 createdAt 兜出", "2026-11-30", parsed.plans.first().plannedDate)
    }

    @Test
    fun zipBackup_resolvesPhotosFromBinaryEntries() {
        // ★这是 zip 存在的唯一理由：把照片一起搬过来。
        //   photoRefs 把 id 指到 photos/<id>.jpg，解析器要从 zip 条目里取真正字节。
        val parsed = BackupParser.parseZip(buildWebZip())
        val photos = parsed.records.first().photos
        assertEquals("照片应被解析出来", 1, photos.size)

        val expected = java.util.Base64.getDecoder().decode(tinyB64)
        assertTrue("照片字节应与 zip 里的文件一致", photos.first().bytes.contentEquals(expected))
    }

    @Test
    fun nonBackupZip_givesActionableError() {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
        }
        val ex = runCatching { BackupParser.parseZip(baos.toByteArray()) }.exceptionOrNull()
        assertTrue("应报错，实际：$ex", ex is IllegalArgumentException)
        // 同上：短文案不点名文件，但要说明"包里没有数据"
        assertTrue("信息应说明缺什么，实际：${ex!!.message}",
            ex.message!!.contains("数据") || ex.message!!.contains("xixi-data"))
    }

    // ══════════════════════════════════════════════════════════════
    //  ★网盘配置（用户两次反馈「导入后账号密码还是空的」）
    //  Web 版的键名/地址语义/密码算法都与原生不同，这三条各钉一个断言。
    // ══════════════════════════════════════════════════════════════

    /** 复刻 app-core.js 的 encPwd：'xk1:' + base64( XOR(明文, "xixi") ) */
    private fun encPwdJs(pw: String): String {
        val key = "xixi"
        val xored = buildString {
            pw.forEachIndexed { i, c -> append(((c.code xor key[i % key.length].code).toChar())) }
        }
        return "xk1:" + android.util.Base64.encodeToString(
            xored.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
        )
    }

    @Test
    fun webSyncConfig_isRead_andPasswordDecoded() {
        val json = """
        {
          "app": "XiXiHiking",
          "records": [],
          "plannedTrips": [],
          "syncConfig": {
            "server": "https://dav.jianguoyun.com/dav/",
            "username": "me@example.com",
            "password": "${encPwdJs("app-pass-1234")}"
          }
        }
        """.trimIndent()

        val dav = BackupParser.parse(json).dav
        assertTrue("Web 版 syncConfig 必须被读出来", dav != null)
        assertEquals("账号", "me@example.com", dav!!.user)
        // ★Web 存的是 WebDAV 根地址，原生要的是「含目录」——必须补 xixi-hiking
        assertEquals("地址要补上应用目录", "https://dav.jianguoyun.com/dav/xixi-hiking", dav.url)
        // ★密码是 xk1: 混淆的，原样存进去会 401
        assertEquals("应用密码要解回来", "app-pass-1234", dav.pass)
    }

    @Test
    fun nativeWebdavSection_isRead_andUrlKept() {
        val json = """
        {
          "app": "XiXiHiking",
          "records": [],
          "plannedTrips": [],
          "webdav": {
            "url": "https://dav.jianguoyun.com/dav/xixi-hiking",
            "user": "me@example.com",
            "pass": "plain-pass"
          }
        }
        """.trimIndent()

        val dav = BackupParser.parse(json).dav
        assertTrue(dav != null)
        assertEquals("原生版自己导出的地址不该被改", "https://dav.jianguoyun.com/dav/xixi-hiking", dav!!.url)
        assertEquals("明文密码原样保留", "plain-pass", dav.pass)
    }

    @Test
    fun zipBackup_alsoCarriesSyncConfig() {
        // ★上一版只对非 zip 读配置，而用户导的正是 Web 版 zip 完整备份
        val inner = """
        {
          "app": "XiXiHiking",
          "records": [],
          "plannedTrips": [],
          "syncConfig": {
            "server": "https://dav.jianguoyun.com/dav/",
            "username": "me@example.com",
            "password": ""
          }
        }
        """.trimIndent()
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("xixi-data.json"))
            zos.write(inner.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val dav = BackupParser.parseZip(baos.toByteArray()).dav
        assertTrue("zip 备份里的配置也要读出来", dav != null)
        assertEquals("me@example.com", dav!!.user)
        assertEquals("没带密码就是空（导入后提示补填）", "", dav.pass)
    }
}
