package com.xixi.hiking.nativeapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 联网状态（2026-11-05）
 *
 * 用户要求：「保证离线时候，app 不会出现任何报错情况」。
 *
 * 离线时不该做的事
 * ────────────────
 * 不是"把错误藏起来"，而是**别去发那个请求**：
 *   · 检查更新 → 直接说"离线，稍后再试"，不要报"检查失败"（用户会以为软件坏了）
 *   · 云备份上传/下载 → 直接说"离线"，别等 8 秒超时再报网络错误
 *   · 自动同步 → **静默跳过**，连提示都不要（用户没主动点，不该被打扰）
 *
 * 为什么"读不到就当在线"
 * ─────────────────────
 * 某些 ROM 会限制后台读网络状态，读不到时如果判成离线，会**误伤正常联网** ——
 * 那比多等一次超时更糟。所以异常一律 fallback 到"在线"，让真实请求自己说话。
 */
object Net {

    fun isOnline(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        // VALIDATED：真的能通外网（连上 WiFi 但没登录门户时这里是 false）
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (t: Throwable) {
        true
    }
}
