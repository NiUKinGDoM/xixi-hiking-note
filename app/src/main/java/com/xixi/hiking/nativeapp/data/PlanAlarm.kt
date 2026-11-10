package com.xixi.hiking.nativeapp.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 计划提醒（**真**提醒，不依赖 App 在后台活着）。
 *
 * ★★★2026-11-10 为什么要有这个文件：
 *   之前只有 `Notifier.dailyReminders()`，它是在 MainActivity 启动时调一次的 ——
 *   意思是**App 不开着就永远不会有提醒**。用户计划明天去爬山，明早 App 是关的，
 *   手机上什么都不会响。计划、日历、到期完成/延期这一整套流程，最后一环是空的。
 *
 * ── 关键取舍：为什么用 setAndAllowWhileIdle 而不是 setExact ──
 *   `setExactAndAllowWhileIdle` 需要 `SCHEDULE_EXACT_ALARM` 这个**特殊权限**：
 *   Android 13 起新安装默认不授予，要跳系统设置让用户手动开。
 *   为了"早上 8 点提醒今天有徒步计划"去逼用户过一道权限墙，不值。
 *   `setAndAllowWhileIdle` 不需要任何权限，Doze 深度休眠下也能唤醒，
 *   代价是**可能晚几分钟**（系统会做批次调度）。
 *   对"今天要去爬山"这种提醒，晚 5 分钟没有任何影响。
 *
 * ── 为什么要 BootReceiver ──
 *   闹钟不是持久化的：手机一重启，AlarmManager 里排的全部消失。
 *   没有开机重排，用户周五设了周六的计划、周五晚上关个机，周六就静默失约。
 */
object PlanAlarm {

    const val ACTION = "com.xixi.hiking.nativeapp.PLAN_REMIND"
    const val EXTRA_NAME = "plan_name"

    /** 提醒时间：当天早上 8 点（和 Web 版一致） */
    private const val HOUR = 8
    private const val MINUTE = 0

    /**
     * requestCode 用固定区间，取消时逐个打一遍。
     *
     * 为什么不用 plan.id.hashCode()：取消的时候手里没有 id 列表（计划可能已被删），
     * 固定区间能无脑全清，不会漏掉"已删除计划的残留闹钟"——那种残留会在
     * 用户明明删了计划之后还弹一次提醒，比不提醒更糟。
     */
    private const val RC_BASE = 3200
    private const val RC_COUNT = 120

    private val DATE_FMT = "yyyy-MM-dd"

    /** 重排全部计划闹钟。幂等：调几次结果一样。 */
    fun reschedule(ctx: Context, plans: List<PlannedTripEntity>) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            cancelAll(ctx, am)

            val today = todayStr()
            val upcoming = plans
                .filter { it.plannedDate.isNotBlank() && it.plannedDate >= today }
                .sortedBy { it.plannedDate }
                .take(RC_COUNT)

            upcoming.forEachIndexed { i, p ->
                val at = triggerMillis(p.plannedDate) ?: return@forEachIndexed
                // 今天 8 点已经过了 → 不排（今天的提醒交给打开 App 时那条）
                if (at <= System.currentTimeMillis()) return@forEachIndexed
                schedule(ctx, am, RC_BASE + i, p.name, at)
            }
            Log.i("hiking", "计划闹钟已排 ${upcoming.size} 个")
        } catch (t: Throwable) {
            // 提醒失败绝不能影响 App 本身
            Log.w("hiking", "排计划闹钟失败", t)
        }
    }

    private fun schedule(ctx: Context, am: AlarmManager, rc: Int, name: String, at: Long) {
        val pi = pendingIntent(ctx, rc, name) ?: return
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            // 个别 ROM 会对 setAndAllowWhileIdle 也设限，退回普通 set（可能更不准，但不会崩）
            Log.w("hiking", "精确闹钟被拒，退回普通 set", e)
            try {
                am.set(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (t: Throwable) {
                Log.w("hiking", "排闹钟彻底失败", t)
            }
        }
    }

    private fun cancelAll(ctx: Context, am: AlarmManager) {
        for (i in 0 until RC_COUNT) {
            val pi = pendingIntent(ctx, RC_BASE + i, null, create = false) ?: continue
            try {
                am.cancel(pi)
                pi.cancel()
            } catch (t: Throwable) {
                // 忽略：取消失败最坏是多响一次
            }
        }
    }

    private fun pendingIntent(
        ctx: Context,
        rc: Int,
        name: String?,
        create: Boolean = true
    ): PendingIntent? {
        val i = Intent(ctx, PlanAlarmReceiver::class.java).apply {
            action = ACTION
            if (name != null) putExtra(EXTRA_NAME, name)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (create) {
            PendingIntent.getBroadcast(ctx, rc, i, flags)
        } else {
            // create=false 时用 FLAG_NO_CREATE，没排过就返回 null（不凭空造一个）
            PendingIntent.getBroadcast(
                ctx, rc, i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    /** "2026-11-10" → 当天 08:00 的时间戳；格式不对返回 null（不猜） */
    private fun triggerMillis(date: String): Long? {
        return try {
            val fmt = SimpleDateFormat(DATE_FMT, Locale.US)
            fmt.isLenient = false
            val d = fmt.parse(date) ?: return null
            val c = Calendar.getInstance()
            c.time = d
            c.set(Calendar.HOUR_OF_DAY, HOUR)
            c.set(Calendar.MINUTE, MINUTE)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        } catch (t: Throwable) {
            null
        }
    }

    private fun todayStr(): String =
        SimpleDateFormat(DATE_FMT, Locale.US).format(java.util.Date())
}

/** 到点后的接收器：只负责发一条通知 */
class PlanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            if (intent.action != PlanAlarm.ACTION) return
            val name = intent.getStringExtra(PlanAlarm.EXTRA_NAME).orEmpty()
            Notifier.postPlanReminder(ctx, name)
        } catch (t: Throwable) {
            Log.w("hiking", "计划提醒发送失败", t)
        }
    }
}

/**
 * 开机后重排闹钟。
 *
 * ★没有它，用户关机一次，之后所有计划提醒全部失效，而且**没有任何提示**。
 *   这是"提醒"类功能最常见的隐性故障。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val app = ctx.applicationContext
        // Receiver 只有 10 秒窗口；读库是 suspend，交给 IO 协程，用 goAsync 保住生命周期
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val db = buildDatabase(app)
                val plans = db.plannedTripDao().observeAllOnce()
                PlanAlarm.reschedule(app, plans)
            } catch (t: Throwable) {
                Log.w("hiking", "开机重排闹钟失败", t)
            } finally {
                try {
                    pending.finish()
                } catch (t: Throwable) {
                    // 忽略
                }
            }
        }
    }
}
