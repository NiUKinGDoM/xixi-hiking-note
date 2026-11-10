package com.xixi.hiking.nativeapp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * ★通知（对齐 Web 版的「计划提醒 / 备份提醒」）
 *
 * Web 版在打开 App 时用 Notification API 弹两条：
 *   · 今天有计划 → 「今天有 1 个计划：XXX」
 *   · 超过 30 天没备份 → 「该备份了」
 * 原生版之前完全没有通知，所以设置页那条「通知权限」也就无从谈起 ——
 * 现在补上最小可用的一套：一个渠道 + 两条提醒 + 权限状态。
 *
 * 一天最多打扰一次（KEY_NOTIFY_DAY 记日期），不给用户刷屏。
 */
object Notifier {

    private const val CHANNEL_ID = "xixi_reminders"
    private const val CHANNEL_NAME = "提醒"
    const val KEY_NOTIFY_DAY = "notify_last_day"

    /** 有没有发通知的权限（Android 13+ 需要用户授权） */
    fun canPost(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "计划到期与备份提醒" }
        )
    }

    /**
     * 每天最多一次：今天有到期计划 / 太久没备份 → 发通知。
     *
     * 只在有权限时调用；任何异常都吞掉（通知失败不该影响打开 App）。
     */
    fun dailyReminders(ctx: Context, duePlans: List<String>, lastBackupAt: Long) {
        try {
            if (!canPost(ctx)) return
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            if (Prefs.getString(ctx, KEY_NOTIFY_DAY, "") == today) return

            ensureChannel(ctx)
            var posted = false
            if (duePlans.isNotEmpty()) {
                val title = if (duePlans.size == 1) "今天有 1 个计划"
                else "今天有 ${duePlans.size} 个计划"
                post(ctx, 1001, title, "「${duePlans.first()}」" +
                    if (duePlans.size > 1) " 等 ${duePlans.size} 个" else "")
                posted = true
            }
            val stale = lastBackupAt == 0L ||
                System.currentTimeMillis() - lastBackupAt > 30L * 86_400_000L
            if (stale) {
                post(ctx, 1002, "该备份了", "已经超过 30 天没备份了")
                posted = true
            }
            if (posted) Prefs.setString(ctx, KEY_NOTIFY_DAY, today)
        } catch (t: Throwable) {
            android.util.Log.w("hiking", "通知发送失败（已忽略）", t)
        }
    }

    /**
     * 计划当天早上的提醒（由 [PlanAlarm] 的闹钟触发，**不需要 App 开着**）。
     *
     * 和 [dailyReminders] 的区别：那条是"打开 App 时顺手提一句"，
     * 这条是真正的定时提醒 —— 用户关着手机也能收到。
     */
    fun postPlanReminder(ctx: Context, planName: String) {
        try {
            if (!canPost(ctx)) return
            ensureChannel(ctx)
            val name = planName.ifBlank { "今天的计划" }
            post(ctx, 2001, "今天出发：$name", "记得看天气、带够水。回来记一条。")
        } catch (t: Throwable) {
            android.util.Log.w("hiking", "计划提醒发送失败（已忽略）", t)
        }
    }

    private fun post(ctx: Context, id: Int, title: String, text: String) {
        ensureChannel(ctx)
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(
                ctx, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(com.xixi.hiking.nativeapp.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()
        // ★2026-11-05 Lint 抓到：直接 notify 没有检查 POST_NOTIFICATIONS。
        //   Android 13+ 用户拒绝通知权限后，这里要么静默丢弃（看起来"提醒没生效"），
        //   要么在个别 ROM 上抛 SecurityException 把整个定时任务带崩。
        //   两道闸：系统开关关了不做、没授权不做；最后再用 try 兜一层。
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (t: Throwable) {
            android.util.Log.w("hiking", "通知发送失败", t)
        }
    }
}

/**
 * 界面要跟的偏好（开关改了要立刻影响顶栏/点击手感）。
 *
 * 为什么单独放一个 object：设置页和主界面是兄弟组件，
 * 用回调一层层传要穿四五个函数；用 Compose 的 mutableStateOf 做单例，
 * 谁改谁生效，也不用担心 remember 读死。
 */
object UiPrefs {
    /** 顶栏显示实时帧率（Web 版默认关） */
    var showFps by mutableStateOf(false)

    /** 点击震动 */
    var haptic by mutableStateOf(true)

    /** 通知权限当前是否可用（设置页据此决定要不要显示那一行） */
    var canNotify by mutableStateOf(true)

    fun init(ctx: Context) {
        showFps = Prefs.getBool(ctx, Prefs.KEY_SHOW_FPS, false)
        haptic = Prefs.getBool(ctx, Prefs.KEY_HAPTIC, true)
        canNotify = Notifier.canPost(ctx)
    }
}
