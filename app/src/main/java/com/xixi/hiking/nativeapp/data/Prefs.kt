package com.xixi.hiking.nativeapp.data

import android.content.Context

/** 轻量偏好存储（对应 Web 版的 localStorage 配置项） */
object Prefs {
    private const val NAME = "hiking_prefs"

    const val KEY_THEME = "theme"          // "auto" | "light" | "dark"
    const val KEY_AUTO_SYNC = "auto_sync"  // 是否自动同步
    const val KEY_MS_SEEN = "milestone_seen"   // 里程碑「已查看」id 集合（入口红点用）
    const val KEY_GUIDE_OVERVIEW = "guide_overview_seen"   // 概览引导卡已关
    const val KEY_GUIDE_RECORDS = "guide_records_seen"     // 记录页引导卡已关
    const val KEY_GUIDE_PLANS = "guide_plans_seen"         // 计划页引导卡已关
    const val KEY_APP_TITLE = "app_title"                  // 顶栏标题（可自定义，最多 20 字）
    const val KEY_LAST_BACKUP = "last_backup_at"           // 上次导出/上传备份的时间戳
    const val KEY_BACKUP_HINT_OFF = "backup_hint_off"      // 备份提示「知道了」→ 永久不再提
    const val KEY_LEGAL_AGREED = "legal_agreed_version"     // 已同意的隐私政策条款版本
    // ★2026-11-04 同意时间（ISO 8601）—— 对齐 Web：立法本意是「明确同意必须有痕迹」，
    //   只存版本号说不清"什么时候同意的"，隐私政策底部那行"你已于 X 同意"也渲染不出来。
    const val KEY_LEGAL_AGREED_AT = "legal_agreed_at"
    const val KEY_SEEDED = "sample_seeded"                  // 示例记录已灌过（抹掉数据后不再复活）
    const val KEY_HAPTIC = "haptic_on"                       // 点击震动反馈（对齐 Web 版 hapticToggle，默认开）
    const val KEY_SHOW_FPS = "show_fps"                       // 顶栏显示帧率（对齐 Web 版 fpsToggle，默认关）
    const val KEY_OVERDUE_CARE_DAY = "overdue_care_day"       // 过期关怀弹窗上次出现的日期（一天一次）
    // ★2026-11-03 视图记忆（用户：「默认都是列表状态，也保留记忆功能」）
    const val KEY_PLANS_VIEW = "plans_view_list"              // 计划页 true=列表 / false=日历
    const val KEY_RECORDS_VIEW = "records_view_list"          // 记录页 true=列表 / false=山册
    // ★2026-11-04 每页条数（对齐 Web 的 RemoteConfig pageSize：5~50，默认 10）
    const val KEY_PAGE_SIZE = "page_size"

    // ★2026-11-05 同步状态（对齐 web 的 showSyncStatusModal）：
    //   上次同步时间 / 结果 / 做了什么。写这三条是为了让「同步状态」弹窗有内容可显示 ——
    //   以前只有操作后一闪而过的 toast，用户过十分钟就想不起来"到底同步成功没有"。
    const val KEY_SYNC_LAST_AT = "sync_last_at"      // Long 时间戳
    const val KEY_SYNC_LAST_OK = "sync_last_ok"      // Boolean
    const val KEY_SYNC_LAST_ACT = "sync_last_act"    // String：上传 / 下载 / 删除

    fun getString(ctx: Context, key: String, def: String): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(key, def) ?: def

    fun getInt(ctx: Context, key: String, def: Int): Int =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(key, def)

    fun setInt(ctx: Context, key: String, value: Int) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putInt(key, value).apply()
    }

    fun getLong(ctx: Context, key: String, def: Long): Long =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong(key, def)

    fun setLong(ctx: Context, key: String, value: Long) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putLong(key, value).apply()
    }

    fun setString(ctx: Context, key: String, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }

    fun getBool(ctx: Context, key: String, def: Boolean): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(key, def)

    fun setBool(ctx: Context, key: String, value: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    /** ★id 集合（里程碑已查看等）—— 用 Set<String> 存，避免自己拼 JSON 出错 */
    fun getStringSet(ctx: Context, key: String): Set<String> =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getStringSet(key, emptySet()) ?: emptySet()

    fun setStringSet(ctx: Context, key: String, value: Set<String>) {
        // ★必须传新副本：SharedPreferences 不保证持有的是不可变集合，直接存同一引用后续修改会串
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(key, HashSet(value)).apply()
    }

    fun remove(ctx: Context, key: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().remove(key).apply()
    }
}
