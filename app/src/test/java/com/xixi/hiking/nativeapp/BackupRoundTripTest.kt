package com.xixi.hiking.nativeapp

import com.xixi.hiking.nativeapp.data.BackupExport
import com.xixi.hiking.nativeapp.data.BackupParser
import com.xixi.hiking.nativeapp.data.PlannedTripEntity
import com.xixi.hiking.nativeapp.data.RecordEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ★★备份「导出 → 导入」往返一致性测试（2026-11-05）
 *
 * 为什么必须有
 * ────────────
 * 备份出问题有两种，痛苦程度差一个数量级：
 *
 *   ① 导出直接失败 —— 用户当场就看到报错，损失可控。
 *   ② **导出"成功"，但少写了一两个字段** —— 用户换手机、恢复完、删掉旧机，
 *      过了两周才发现"我的小日记全没了""计划的备注是空的"。
 *      这条链路全程没有任何异常，日志里一片正常。
 *
 * ② 只能靠断言钉死。这个测试做三件事：
 *
 *   1. **字段覆盖**：用反射列出 [RecordEntity] / [PlannedTripEntity] 的**全部**属性，
 *      逐个检查导出的 JSON 里有没有对应 key。以后给实体加一列却忘了加进导出，
 *      测试会直接报出字段名 —— 不用等用户丢数据。
 *   2. **值不丢真**：每个字段塞一个互不相同的值，导出再解析回来逐字段比对。
 *      （专门防"类型变了"：Double 被写成 String、Int 被截断、空字符串被写成 null）
 *   3. **特殊字符**：引号 / 换行 / emoji / 反斜杠 走一遍 JSON 序列化，不能变形。
 *
 * 为什么不用 buildPayload（真实导出函数）
 * ─────────────────────────────────────
 * 它要读 Room（需要 Context + 磁盘 + 协程）。往返测试的价值在"字段对不对"，
 * 不在"数据库读没读到"，所以直接调 [BackupExport.recordToJson] ——
 * **线上导出用的就是这同一个函数**（2026-11-05 特意抽出来的），
 * 不是测试里另写一份，所以不存在"测试绿、线上丢字段"。
 */
// org.json 在纯 JVM 里是空实现，必须走 Robolectric；这一层不碰数据库，没有线程问题。
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class BackupRoundTripTest {

    /**
     * 实体真正的「数据字段」。
     *
     * 直接用 `declaredFields` 会混进编译器塞的合成字段 —— Compose 编译器给每个
     * 被它处理过的类加 `$stable`（稳定性标记），Room 也可能加东西。它们不是数据，
     * 却会让"字段覆盖"检查永远红。这里统一过滤掉 `$` 开头的合成字段。
     */
    private fun syntheticFree(cls: Class<*>): List<String> =
        cls.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .filterNot { it.startsWith("$") || it == "INSTANCE" || it == "Companion" }

    private fun record(
        id: String = "r1",
        name: String = "太白山拔仙台",
        distance: Double = 18.5,
        duration: Int = 540,
        elevation: Int = 3767,
        difficulty: Int = 5,
        mood: String = "疲惫",
        weather: String = "多云",
        companions: String = "老张、阿May",
        notes: String = "凌晨四点出发，六点半赶上日出。",
        createdAt: String = "2026-11-01T04:10:00",
        updatedAt: String = "2026-11-02T09:30:00"
    ) = RecordEntity(
        id = id, name = name, distance = distance, duration = duration,
        elevation = elevation, difficulty = difficulty, mood = mood,
        weather = weather, companions = companions, notes = notes,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun plan(
        id: String = "p1",
        name: String = "华山·西峰看日落",
        plannedDate: String = "2026-11-08",
        location: String = "渭南·华阴",
        note: String = "带厚外套和头灯",
        difficulty: Int = 4,
        createdAt: String = "2026-11-05T10:00:00",
        elevation: Int = 2154
    ) = PlannedTripEntity(
        id = id, name = name, plannedDate = plannedDate, location = location,
        note = note, difficulty = difficulty, createdAt = createdAt, elevation = elevation
    )

    /** 用真实的导出函数拼一份 payload（结构 = xixi-data.json） */
    private fun payload(records: List<RecordEntity>, plans: List<PlannedTripEntity>): String {
        val root = JSONObject()
        root.put("app", "XiXiHiking")
        root.put("appName", "徒步小记")
        root.put("version", "1.0.0.0")
        root.put("exportedAt", 1762300000000L)
        root.put("records", JSONArray().apply {
            records.forEach { put(BackupExport.recordToJson(it, JSONArray())) }
        })
        root.put("plannedTrips", JSONArray().apply {
            plans.forEach { put(BackupExport.planToJson(it)) }
        })
        return root.toString(2)
    }

    // ────────────────────────────────────────────────────────────────
    //  ① 字段覆盖：实体加了列但导出没加 → 这里红
    // ────────────────────────────────────────────────────────────────

    @Test
    fun everyRecordField_isExported() {
        val json = BackupExport.recordToJson(record(), JSONArray())
        // 这些属性**故意**不导出（本地派生量 / 与 photos 重复）
        val intentionalSkip = setOf("photosJson")
        val missing = syntheticFree(RecordEntity::class.java)
            .filter { it !in intentionalSkip }
            .filter { !json.has(it) }

        assertTrue(
            "记录实体有字段没写进备份（换机会丢这一列）：$missing\n" +
                "修法：在 BackupExport.recordToJson 里补 put(\"<字段>\", ...)，" +
                "并在 BackupParser 里读回来",
            missing.isEmpty()
        )
    }

    @Test
    fun everyPlanField_isExported() {
        val json = BackupExport.planToJson(plan())
        val missing = syntheticFree(PlannedTripEntity::class.java)
            .filter { !json.has(it) }
        assertTrue(
            "计划实体有字段没写进备份：$missing\n" +
                "修法：BackupExport.planToJson 补 put(...)",
            missing.isEmpty()
        )
    }

    // ────────────────────────────────────────────────────────────────
    //  ② 值不丢真：导出 → 解析 → 逐字段比对
    // ────────────────────────────────────────────────────────────────

    @Test
    fun record_roundTrip_keepsEveryValue() {
        val src = record()
        val parsed = BackupParser.parse(payload(listOf(src), emptyList()))
        assertEquals("解析出来的记录数不对", 1, parsed.records.size)

        val got = parsed.records.first().entity
        assertEquals("id", src.id, got.id)
        assertEquals("name", src.name, got.name)
        assertEquals("distance", src.distance, got.distance, 0.0001)
        assertEquals("duration", src.duration, got.duration)
        assertEquals("elevation", src.elevation, got.elevation)
        assertEquals("difficulty", src.difficulty, got.difficulty)
        assertEquals("mood", src.mood, got.mood)
        assertEquals("weather", src.weather, got.weather)
        assertEquals("companions", src.companions, got.companions)
        assertEquals("notes", src.notes, got.notes)
        assertEquals("createdAt", src.createdAt, got.createdAt)
        assertEquals("updatedAt", src.updatedAt, got.updatedAt)
    }

    @Test
    fun plan_roundTrip_keepsEveryValue() {
        val src = plan()
        val parsed = BackupParser.parse(payload(emptyList(), listOf(src)))
        assertEquals("解析出来的计划数不对", 1, parsed.plans.size)

        val got = parsed.plans.first()
        assertEquals("id", src.id, got.id)
        assertEquals("name", src.name, got.name)
        assertEquals("plannedDate", src.plannedDate, got.plannedDate)
        assertEquals("location", src.location, got.location)
        assertEquals("note", src.note, got.note)
        assertEquals("difficulty", src.difficulty, got.difficulty)
        assertEquals("createdAt", src.createdAt, got.createdAt)
        assertEquals("elevation", src.elevation, got.elevation)
    }

    /**
     * 浮点边界：里程 0 / 0.1 / 123.45 这类值走一遍
     * （JSON 里 18.5 和 18.500000001 是同一个数，但 0 和 0.0 可能被写成不同东西）
     */
    @Test
    fun distance_edgeValues_survive() {
        val srcs = listOf(
            record(id = "z", name = "零公里", distance = 0.0),
            record(id = "d", name = "一点点", distance = 0.1),
            record(id = "l", name = "长线", distance = 123.45)
        )
        val parsed = BackupParser.parse(payload(srcs, emptyList())).records.map { it.entity }
        assertEquals(3, parsed.size)
        assertEquals(0.0, parsed[0].distance, 0.0001)
        assertEquals(0.1, parsed[1].distance, 0.0001)
        assertEquals(123.45, parsed[2].distance, 0.0001)
    }

    // ────────────────────────────────────────────────────────────────
    //  ③ 特殊字符：JSON 转义走不通时，用户的小日记会变成乱码/截断
    // ────────────────────────────────────────────────────────────────

    @Test
    fun specialCharacters_surviveRoundTrip() {
        val nasty = "他说\"今天风好大\"\n第二行\\反斜杠\tTab 结束"
        val src = record(id = "s1", name = "带\"引号\"的山", notes = nasty)
        val got = BackupParser.parse(payload(listOf(src), emptyList())).records.first().entity
        assertEquals("名称里的引号被吞了", src.name, got.name)
        assertEquals("小日记的换行/反斜杠/引号变形了", nasty, got.notes)
    }

    /** 空字段（用户没填心情/同行人/小日记）不能被写成 "null" 字符串 */
    @Test
    fun blankFields_stayEmpty_notNull() {
        val src = record(
            id = "e1", name = "只有名字", mood = "", weather = "",
            companions = "", notes = ""
        )
        val got = BackupParser.parse(payload(listOf(src), emptyList())).records.first().entity
        assertEquals("心情空值变成了别的", "", got.mood)
        assertEquals("天气空值变成了别的", "", got.weather)
        assertEquals("同行人空值变成了别的", "", got.companions)
        assertEquals("小日记空值变成了别的", "", got.notes)
        assertTrue("空值被写成了字符串 null", got.notes != "null")
    }

    /** 多条记录 + 多条计划一起走，顺序和数量都不能错位 */
    @Test
    fun manyItems_keepIdentity() {
        val recs = (1..25).map { record(id = "r$it", name = "山 $it", distance = it * 1.5) }
        val plans = (1..7).map { plan(id = "p$it", name = "计划 $it") }
        val parsed = BackupParser.parse(payload(recs, plans))

        assertEquals(25, parsed.records.size)
        assertEquals(7, parsed.plans.size)
        // 按 id 对齐，确认没有"第 3 条变成第 4 条"这种错位
        val byId = parsed.records.associateBy { it.entity.id }
        recs.forEach { r ->
            val got = byId[r.id]
            assertTrue("丢了 ${r.id}", got != null)
            assertEquals("${r.id} 的名称串了", r.name, got!!.entity.name)
            assertEquals("${r.id} 的里程串了", r.distance, got.entity.distance, 0.0001)
        }
    }
}
