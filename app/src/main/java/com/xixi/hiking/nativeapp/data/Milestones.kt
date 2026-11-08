package com.xixi.hiking.nativeapp.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ★里程碑 —— 1:1 对齐 Web 版（www/app-data-plan-detail.js 的 MILESTONES）
 *
 * 关键设计（照抄 Web 版踩过的坑）：
 *   ① 成就**实时计算**，不落库存「已达成」—— 删掉华山，陕西名山立刻退回未达成；
 *      持久化的只有「已查看」这一个簿记（入口红点用）。
 *   ② 收集类把清单列出来打勾，还缺哪几个一眼看到。
 *   ③ 山名归一化：去括号、去方位前缀（东岳/西岳…）、去标点 ⇒「西岳华山」与「华山」算同一座。
 *   ④ 天气两种存法都认：新数据存 emoji（☀️🌤️☁️🌧️❄️），老数据存文本（晴/晴天…）。
 */

/** 一档里程碑。items 的每一项是「别名组」，第一个元素作显示名。 */
data class Milestone(
    val id: String,
    val type: String,                       // peaks | km | alt | collect
    val value: Double = 0.0,
    val collectKey: String = "",
    val matchBy: String = "",               // peak | season | weather | difficulty
    val items: List<List<String>> = emptyList(),
    val title: String,
    val desc: String
)

/** 当前统计快照（一次遍历算全部维度） */
data class MilestoneStats(
    val peaks: Int,
    val km: Double,
    val maxAlt: Int,
    val collections: Map<String, Set<String>>   // collectKey -> 命中的显示名
)

private fun itemsOf(vararg groups: Any): List<List<String>> = groups.map { g ->
    when (g) {
        is List<*> -> g.map { it.toString() }
        else -> listOf(g.toString())
    }
}

/** ★17 档（顺序与 Web 版完全一致，不要重排 —— 一览按此顺序展示） */
val MILESTONES: List<Milestone> = listOf(
    Milestone("peaks_1", "peaks", 1.0, title = "第 1 座山", desc = "入坑了，周末有去处"),
    Milestone("peaks_5", "peaks", 5.0, title = "第 5 座山", desc = "五座山，够凑一桌"),
    Milestone("peaks_10", "peaks", 10.0, title = "第 10 座山", desc = "两位数，敢叫驴友了"),
    Milestone("peaks_25", "peaks", 25.0, title = "第 25 座山", desc = "山册边角磨圆了"),
    Milestone("peaks_50", "peaks", 50.0, title = "第 50 座山", desc = "半百，是生活了"),
    Milestone("km_100", "km", 100.0, title = "累计 100 公里", desc = "够绕城墙七圈"),
    Milestone("km_500", "km", 500.0, title = "累计 500 公里", desc = "差不多到郑州"),
    Milestone("km_1000", "km", 1000.0, title = "累计 1000 公里", desc = "一千里，进京赶考"),
    Milestone("alt_1000", "alt", 1000.0, title = "登顶 1000 米", desc = "视野一下子开阔"),
    Milestone("alt_2000", "alt", 2000.0, title = "登顶 2000 米", desc = "云在脚下了"),
    Milestone("alt_3000", "alt", 3000.0, title = "登顶 3000 米", desc = "正经的高山了"),
    Milestone("alt_4000", "alt", 4000.0, title = "登顶 4000 米", desc = "喘气得慢慢来"),
    Milestone(
        "wuyue", "collect", collectKey = "wuyue", matchBy = "peak",
        items = itemsOf("泰山", "华山", "衡山", "恒山", "嵩山"),
        title = "五岳集齐", desc = "五岳走了一遍"
    ),
    Milestone(
        "shaanxi", "collect", collectKey = "shaanxi", matchBy = "peak",
        items = itemsOf(listOf("南五台山", "南五台"), listOf("翠华山", "翠华"), "华山", listOf("太白山", "太白")),
        title = "陕西名山", desc = "家门口的都得走"
    ),
    Milestone(
        "season", "collect", collectKey = "season", matchBy = "season",
        items = itemsOf("春", "夏", "秋", "冬"),
        title = "四季足迹", desc = "一季都没落下"
    ),
    Milestone(
        "weather", "collect", collectKey = "weather", matchBy = "weather",
        items = itemsOf("晴", "多云", "阴", "雨", "雪"),
        title = "天气收藏家", desc = "晴雨各有各的好"
    ),
    Milestone(
        "difficulty", "collect", collectKey = "difficulty", matchBy = "difficulty",
        items = itemsOf("1", "2", "3", "4", "5"),
        title = "难度全通关", desc = "从散步到自虐"
    )
)

/** ★山名归一化：去括号内容、空白、方位前缀、连接符（与 Web 版 normalizePeakName 同规则） */
fun normalizePeakName(raw: String?): String {
    var n = (raw ?: "").trim()
    if (n.isEmpty()) return ""
    n = Regex("[（(][^）)]*[）)]").replace(n, "")
    n = Regex("\\s+").replace(n, "")
    n = Regex("^(东岳|西岳|南岳|北岳|中岳)").replace(n, "")
    n = Regex("[·•.\\-—_、,，]").replace(n, "")
    return n
}

/**
 * ★天气归一化：新数据存 emoji，老数据存文本，两者都归一为 晴|多云|阴|雨|雪。
 *   ⚠️ 别只做 indexOf("晴") —— Web 版曾因此让「天气收藏家」永远收不齐。
 */
fun normWeather(raw: String?): String? {
    val s = (raw ?: "").replace(Regex("[\\uFE0F\\u200D\\s]"), "")
    if (s.isEmpty()) return null
    when (s) {
        "☀", "☀️" -> return "晴"
        "🌤", "🌤️" -> return "多云"
        "☁", "☁️" -> return "阴"
        "🌧", "🌧️" -> return "雨"
        "❄", "❄️" -> return "雪"
    }
    for (t in listOf("晴", "多云", "阴", "雨", "雪")) if (s.contains(t)) return t
    return null
}

private val ISO_FMTS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd"
)

/** 解析记录时间（Web 版存的是 ISO 字符串；解析失败返回 null，绝不抛） */
fun parseRecordDate(raw: String?): java.util.Date? {
    val s = (raw ?: "").trim()
    if (s.isEmpty()) return null
    for (p in ISO_FMTS) {
        try {
            val f = SimpleDateFormat(p, Locale.getDefault())
            f.isLenient = false
            val d = f.parse(s)
            if (d != null) return d
        } catch (e: Exception) { /* 换下一个格式 */ }
    }
    return null
}

/** 一次遍历算全部维度 */
fun milestoneStats(records: List<RecordEntity>): MilestoneStats {
    val nameSeen = HashSet<String>()          // 原始名去重（对齐 Web 版 peaks 口径）
    val peakPool = HashSet<String>()          // 归一化名（收集类命中判断）
    val seasonPool = HashSet<String>()
    val weatherPool = HashSet<String>()
    val diffPool = HashSet<String>()
    var km = 0.0
    var maxAlt = 0

    records.forEach { r ->
        val rawName = (r.name ?: "").trim()
        if (rawName.isNotEmpty()) {
            nameSeen.add(rawName)
            val nn = normalizePeakName(rawName)
            if (nn.isNotEmpty()) peakPool.add(nn)
        }
        km += r.distance
        if (r.elevation > maxAlt) maxAlt = r.elevation

        parseRecordDate(r.createdAt)?.let { d ->
            val c = Calendar.getInstance().apply { time = d }
            val mo = c.get(Calendar.MONTH) + 1
            seasonPool.add(
                when {
                    mo == 12 || mo <= 2 -> "冬"
                    mo <= 5 -> "春"
                    mo <= 8 -> "夏"
                    else -> "秋"
                }
            )
        }
        normWeather(r.weather)?.let { weatherPool.add(it) }
        if (r.difficulty in 1..5) diffPool.add(r.difficulty.toString())
    }

    val pools = mapOf(
        "peak" to peakPool,
        "season" to seasonPool,
        "weather" to weatherPool,
        "difficulty" to diffPool
    )

    val collections = HashMap<String, Set<String>>()
    MILESTONES.filter { it.type == "collect" }.forEach { ms ->
        val pool = pools[ms.matchBy] ?: emptySet()
        val hit = HashSet<String>()
        ms.items.forEach { group ->
            val label = group.firstOrNull()
            if (label != null && group.any { pool.contains(it) }) hit.add(label)
        }
        collections[ms.collectKey] = hit
    }

    return MilestoneStats(
        peaks = nameSeen.size,
        km = Math.round(km * 10.0) / 10.0,
        maxAlt = maxAlt,
        collections = collections
    )
}

/** 当前值 / 目标值 */
fun milestoneProgress(ms: Milestone, st: MilestoneStats): Pair<Double, Double> =
    if (ms.type == "collect") {
        val got = st.collections[ms.collectKey]?.size ?: 0
        got.toDouble() to ms.items.size.toDouble()
    } else {
        val cur = when (ms.type) {
            "peaks" -> st.peaks.toDouble()
            "km" -> st.km
            "alt" -> st.maxAlt.toDouble()
            else -> 0.0
        }
        cur to ms.value
    }

/** 已达成的档位 id（实时计算 —— 删记录会立即失效） */
fun milestoneDone(records: List<RecordEntity>): List<String> {
    val st = milestoneStats(records)
    return MILESTONES.filter { ms ->
        val (cur, need) = milestoneProgress(ms, st)
        cur >= need
    }.map { it.id }
}

/** 进度串（对齐 Web 版：km 带 1 位小数 + 单位，peaks 带「座」，alt 带「m」） */
fun milestoneProgressText(ms: Milestone, st: MilestoneStats, done: Boolean): String {
    if (done) return "已达成"
    val (cur, need) = milestoneProgress(ms, st)
    return when (ms.type) {
        "collect" -> "${cur.toInt()} / ${need.toInt()}"
        "km" -> String.format(Locale.getDefault(), "%.1f", cur) + " / ${need.toInt()} km"
        "alt" -> "${cur.toInt()} / ${need.toInt()} m"
        "peaks" -> "${cur.toInt()} / ${need.toInt()} 座"
        else -> "${cur.toInt()} / ${need.toInt()}"
    }
}
