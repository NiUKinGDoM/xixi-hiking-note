package com.xixi.hiking.nativeapp.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ★★回忆册（对齐 Web `buildBackupHTMLString`）
 *
 * 完整备份 zip 里除了 `xixi-data.json` + `photos/`，还会放一份 **回忆册.html**：
 * 日记风格的排版（纸感背景、日期大字、难度五档圆点、小日记正文、照片墙），
 * 用浏览器打开就能读，Ctrl+P → 另存为 PDF 就成了一本册子。
 *
 * 照片用 zip 内的**相对路径** `photos/<id>.jpg` —— 解压后图片直接能显示
 * （Web 版回忆册为了控制体积不带照片，这里带上了，因为 zip 里本来就有二进制）。
 */
object MemoirHtml {

    fun build(
        appTitle: String,
        records: List<RecordEntity>,
        photosByRecord: Map<String, List<PhotoEntity>>,
        plans: List<PlannedTripEntity>,
        payloadJson: String,
        exportedAt: Long
    ): String {
        val title = esc(appTitle.ifBlank { "徒步小记" })
        val recCards = records.joinToString("") { r ->
            val phs = photosByRecord[r.id].orEmpty()
            val imgs = phs.joinToString("") { ph ->
                "<img src=\"photos/${esc(ph.id)}.jpg\" alt=\"\">"
            }
            val meta = ArrayList<String>()
            if (r.mood.isNotBlank()) meta.add("心情 " + esc(r.mood))
            if (r.weather.isNotBlank()) meta.add("天气 " + esc(r.weather))
            if (r.companions.isNotBlank()) meta.add("同行 " + esc(r.companions))
            val metaLine = if (meta.isEmpty()) "" else " · " + meta.joinToString(" · ")

            val facts = ArrayList<String>()
            facts.add("难度 <b>${r.difficulty}</b> 级<span class=\"diff5\">${diffDots(r.difficulty)}</span>")
            if (r.elevation != 0) facts.add("海拔 <b>${r.elevation}</b> m")
            if (r.distance > 0) facts.add("里程 <b>${trimNum(r.distance)}</b> km")
            if (r.duration > 0) facts.add("用时 <b>${fmtDuration(r.duration)}</b>")

            val note = if (r.notes.isNotBlank()) {
                "<div class=\"diary\"><span class=\"lab\">小日记</span>${esc(r.notes.trim())}</div>"
            } else ""

            val longCls = if (r.notes.length > 60) " entry-long" else ""
            buildString {
                append("<div class=\"entry$longCls\">")
                append("<div class=\"entry-head\">")
                append("<div class=\"entry-date\">")
                dayMark(r.createdAt)?.let { append("<span class=\"day\">$it</span>") }
                append("<span class=\"meta\">${fmtDateCN(r.createdAt)}$metaLine</span>")
                append("</div>")
                append("<div class=\"entry-name\">${esc(r.name.ifBlank { "未命名" })}</div>")
                append("<div class=\"facts\">${facts.joinToString("<span class=\"gap\"></span>")}</div>")
                append("</div>")
                append(note)
                if (imgs.isNotEmpty()) append("<div class=\"photos\">$imgs</div>")
                append("</div>")
            }
        }
        val planCards = plans.joinToString("") { t ->
            "<div class=\"plan\"><b>${esc(t.name.ifBlank { "未命名" })}</b>" +
                " —— 难度 ${t.difficulty} 级" +
                (if (t.createdAt.isNotBlank()) " · ${fmtDateCN(t.createdAt)}" else "") +
                (if (t.plannedDate.isNotBlank()) " · 计划 ${esc(t.plannedDate.take(10))}" else "") +
                "</div>"
        }
        val safeJson = payloadJson.replace("</script", "<\\/script")

        return buildString {
            append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>$title · 回忆册</title>")
            append("<style>$CSS</style></head><body>")
            append("<div class=\"tipbar\">" + "\uD83D\uDCC4" + " 想存成 PDF？按 Ctrl+P（手机：浏览器菜单「打印」），目标选「另存为 PDF」即可成册保存</div>")
            append("<h1>" + "\uD83E\uDD7E" + " $title</h1>")
            append("<p class=\"sub\">回忆册 · 共 ${records.size} 条徒步记录 · ${plans.size} 条计划 · 导出于 ")
            append(SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(Date(exportedAt)))
            append("</p>")
            append("<h2><span class=\"t\">徒步记录</span></h2>")
            append(recCards.ifBlank { "<p style=\"color:#64748b;\">暂无记录</p>" })
            append("<h2><span class=\"t\">计划徒步</span></h2>")
            append(planCards.ifBlank { "<p style=\"color:#64748b;\">暂无计划</p>" })
            append("<footer>Made by XiXi " + "\uD83D\uDC9B · 此文件可在 App 内导入恢复</footer>")
            append("<script id=\"backup-data\" type=\"application/json\">$safeJson</script>")
            append("</body></html>")
        }
    }

    // ── 小工具 ────────────────────────────────────────────

    private fun parse(raw: String): Date? {
        val s = raw.trim().replace(' ', 'T')
        for (p in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")) {
            try {
                val f = SimpleDateFormat(p, Locale.getDefault())
                f.isLenient = false
                return f.parse(s)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun fmtDateCN(raw: String): String {
        val d = parse(raw) ?: return raw
        val c = Calendar.getInstance().apply { time = d }
        val wd = arrayOf("日", "一", "二", "三", "四", "五", "六")[c.get(Calendar.DAY_OF_WEEK) - 1]
        return "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 · 周$wd"
    }

    private fun dayMark(raw: String): String? {
        val d = parse(raw) ?: return null
        val c = Calendar.getInstance().apply { time = d }
        return String.format(Locale.US, "%02d.%02d", c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun diffDots(n: Int): String = (1..5).joinToString("") {
        if (it <= n) "<i class=\"on\"></i>" else "<i></i>"
    }

    private fun fmtDuration(min: Int): String =
        if (min < 60) "${min}分" else {
            val h = min / 60
            val m = min % 60
            if (m == 0) "${h}小时" else "${h}小时${m}分"
        }

    private fun trimNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString()
        else String.format(Locale.getDefault(), "%.1f", v)

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** 与 Web 版 `printCss` 同一套（纸感、A4 分页友好） */
    private val CSS = """
body{font-family:"SimSun","Songti SC","STSong",serif;background:#f5f1e8;margin:0 auto;padding:30px 14px 56px;max-width:780px;color:#292524;-webkit-print-color-adjust:exact;print-color-adjust:exact;}
h1{margin:6px 0 2px;font-size:26px;text-align:center;letter-spacing:2px;color:#1c1917;}
p.sub{margin:0 0 24px;text-align:center;color:#a8a29e;font-size:13px;}
.tipbar{background:#eef2ff;border:1px solid #c7d2fe;color:#4338ca;border-radius:10px;padding:8px 12px;font-size:13px;text-align:center;margin-bottom:20px;}
h2{font-size:17px;color:#44403c;margin:34px 0 14px;}
h2 .t{display:inline-block;border-bottom:2px solid #d97706;padding-bottom:3px;letter-spacing:1px;}
.entry{margin:0 0 28px;padding-bottom:24px;border-bottom:1px dashed #ddd3bd;}
.entry:last-of-type{border-bottom:none;}
.entry-head{break-inside:avoid;page-break-inside:avoid;}
.entry-date{display:flex;align-items:baseline;gap:10px;margin-bottom:3px;flex-wrap:wrap;}
.entry-date .day{font-size:26px;font-weight:700;color:#b45309;line-height:1;}
.entry-date .meta{font-size:13px;color:#a8a29e;}
.entry-name{margin:2px 0 7px;font-size:21px;color:#1c1917;letter-spacing:.5px;}
.facts{display:flex;flex-wrap:wrap;font-size:13px;color:#57534e;margin-bottom:9px;}
.facts .gap{display:inline-block;width:2px;margin:0 12px;color:transparent;}
.facts b{font-weight:700;color:#44403c;}
.diff5{display:inline-block;margin-left:8px;}
.diff5 i{display:inline-block;width:7px;height:7px;border-radius:50%;margin-right:3px;background:#e7e0d0;}
.diff5 i.on{background:#d97706;}
.diary{background:#fdf6e3;border-left:3px solid #e7c98a;border-radius:0 10px 10px 0;padding:9px 13px;margin:2px 0 12px;font-size:14px;line-height:1.9;white-space:pre-wrap;word-break:break-word;color:#44403c;}
.diary .lab{color:#b45309;font-weight:700;margin-right:6px;}
.photos{margin-top:6px;line-height:0;}
.photos img{display:inline-block;width:104px;height:104px;object-fit:cover;border-radius:8px;margin:0 6px 6px 0;vertical-align:top;background:#e7e0d0;}
.plan{border-left:3px solid #cbd5e1;padding:7px 0 7px 12px;margin:8px 0;font-size:14px;color:#57534e;}
.plan b{color:#292524;}
footer{text-align:center;color:#a8a29e;font-size:12px;margin-top:34px;}
@page{size:A4;margin:14mm 13mm;}
@media print{.tipbar{display:none!important;}body{background:#fff;padding:0;max-width:none;}.entry{break-inside:avoid-page;}.entry-long{break-inside:auto;}.photos{break-inside:avoid;}img{width:82px!important;height:82px!important;}h1{margin-top:4mm;}}
    """.trimIndent()
}
