package com.xixi.hiking.nativeapp.data

import android.content.Context
import org.json.JSONObject

/**
 * ★山册「连接器」—— 1:1 对齐 Web 版 mtnMerges / mtnKeyOf / mtnLabelOf。
 *
 * 解决什么问题：同一座山写成不同名字（「朱雀-草甸」「朱雀-冰晶顶」）在山册里会分成两张卡。
 * 这里让用户手动把几座「其实是同一座」的连成一张，并自己起个名字。
 *
 * 三条铁律（照抄 Web 版设计）：
 *   ① **只在山册里生效** —— 记录本身、统计、里程碑一律不动（否则改个名就污染数据）
 *   ② **不做自动前缀归并** —— 自动切「-」前缀容易误并（「太白山」「太白山南坡」不一定是一座）
 *   ③ **可逆** —— 断开即完整还原
 *
 * 存储：Prefs 里一段 JSON `{ 来源名: { to: 目标名, name: 显示名 } }`，支持链式（A→B、B→C）。
 */
object MtnMerge {
    private const val KEY = "mtn_merge"

    data class Link(val to: String, val name: String)

    fun read(ctx: Context): Map<String, Link> {
        val raw = Prefs.getString(ctx, KEY, "")
        if (raw.isBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            val out = HashMap<String, Link>()
            root.keys().forEach { k ->
                val o = root.optJSONObject(k) ?: return@forEach
                val to = o.optString("to", "")
                // ★★★ 2026-11-03 修「连接后名称改不了」的真因就在这里。
                //
                //   原来这里写的是 `if (to.isNotBlank() && to != k)` —— 把「to 指向自己」
                //   的条目当脏数据丢掉了。可 link() 恰恰就是**这么存名字的**：
                //       map[tgt] = Link(tgt, displayName)   // 目标的 to 指回自己，只为了挂名字
                //   于是名字写进 Prefs 之后，读的时候被这一行过滤掉 ⇒ labelOf() 永远拿到空，
                //   山册卡片永远显示原名。用户点了「连接」、填了新名字、确认 —— 什么都没变。
                //
                //   现在 to == k 的条目保留下来当**名字锚点**：
                //     · keyOf() 遇到 `link.to == base` 会直接 break（本来就有这道保险），不会打转
                //     · labelOf() 第一分支 `map[key]?.name` 立刻命中
                //     · isMergeTarget() 单独排除自身（否则连过的山会被误判成"归并目标"）
                if (to.isNotBlank()) out[k] = Link(to, o.optString("name", ""))
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun write(ctx: Context, map: Map<String, Link>) {
        val root = JSONObject()
        map.forEach { (k, v) ->
            root.put(k, JSONObject().apply { put("to", v.to); put("name", v.name) })
        }
        Prefs.setString(ctx, KEY, if (map.isEmpty()) "" else root.toString())
    }

    fun clear(ctx: Context) = Prefs.setString(ctx, KEY, "")

    /** 跟随链拿到归并后的键（hop 上限 10，防脏数据成环死循环） */
    fun keyOf(ctx: Context, name: String): String {
        var base = name.trim()
        if (base.isEmpty()) return ""
        val map = read(ctx)
        var hop = 0
        while (true) {
            val link = map[base] ?: break
            if (link.to.isBlank() || link.to == base || hop++ >= 10) break
            base = link.to
        }
        return base
    }

    /** 组显示名：自定义名优先，否则原名 */
    fun labelOf(ctx: Context, key: String): String {
        val map = read(ctx)
        map[key]?.name?.takeIf { it.isNotBlank() }?.let { return it }
        map.entries.firstOrNull { it.value.to == key && it.value.name.isNotBlank() }
            ?.let { return it.value.name }
        return key
    }

    /** 把 sources 全部连到 target，并给这一组起名（target 为空 = 用 sources 第一个当目标） */
    fun link(ctx: Context, sources: List<String>, target: String?, displayName: String) {
        val map = read(ctx).toMutableMap()
        val tgt = (target ?: sources.firstOrNull()) ?: return
        sources.filter { it.isNotBlank() && it != tgt }.forEach { s ->
            map[s] = Link(tgt, "")
        }
        // 名字挂在目标上（目标自己也记一条 name，labelOf 直接命中）
        map[tgt] = Link(tgt, displayName.trim())
        write(ctx, map)
    }

    /** 断开：清掉指向该键的所有来源 + 该键自己的名字 */
    fun unlink(ctx: Context, key: String) {
        val map = read(ctx).toMutableMap()
        map.keys.filter { map[it]?.to == key }.forEach { map.remove(it) }
        map.remove(key)
        write(ctx, map)
    }

    /** 这张卡是不是「归并目标」—— 只有目标卡需要显示「断开连接」（来源卡已并入它、不再单独出现） */
    fun isMergeTarget(ctx: Context, key: String): Boolean =
        // ★排除「to 指回自己」的名字锚点，那样才算真的把别的山连过来了
        read(ctx).entries.any { it.key != key && it.value.to == key }

    /** 这张卡有没有自定义名（连过 / 改过名）—— 决定抽屉里要不要显示「断开连接」 */
    fun hasName(ctx: Context, key: String): Boolean =
        read(ctx)[key]?.name?.isNotBlank() == true
}
