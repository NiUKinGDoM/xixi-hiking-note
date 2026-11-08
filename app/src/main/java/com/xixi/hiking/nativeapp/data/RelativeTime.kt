package com.xixi.hiking.nativeapp.data

/**
 * 相对时间（2026-11-05）
 *
 * 用于「同步状态」这类"上次什么时候"的展示。
 * 只说人话：刚刚 / 5 分钟前 / 3 小时前 / 昨天 / 6 天前 / 日期。
 *
 * 为什么不直接格式化绝对时间：这一行在设置页里当副标题用，
 * "2026-11-05 12:03" 一长串读起来费劲，而且不看日期根本不知道自己多久没同步。
 */
object RelativeTime {

    fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return "还没同步过"
        val d = now - millis
        if (d < 0) return "刚刚"
        val min = d / 60_000L
        val hour = d / 3_600_000L
        val day = d / 86_400_000L
        return when {
            min < 1 -> "刚刚"
            min < 60 -> "${min} 分钟前"
            hour < 24 -> "${hour} 小时前"
            day == 1L -> "昨天"
            day < 7 -> "${day} 天前"
            else -> java.text.SimpleDateFormat("M月d日", java.util.Locale.getDefault())
                .format(java.util.Date(millis))
        }
    }
}
