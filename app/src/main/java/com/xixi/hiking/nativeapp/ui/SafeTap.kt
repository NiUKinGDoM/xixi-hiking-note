package com.xixi.hiking.nativeapp.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/**
 * ★★点击兜底（2026-11-02）
 *
 * 用户反复反馈「好多选项按了就闪退」。原生版里 100 多个可点区域，每一个的
 * onClick 都直接跑在**主线程的事件分发**里 —— 里面任何一句抛异常（Room 查询、
 * 文件读写、JSON 解析、Uri 权限、null 解引用）都会直接冒到 Looper 上，
 * 系统立刻杀掉进程。这就是「点一下就闪退」的全部机制。
 *
 * 与其一个个去猜哪个 handler 有雷，不如在**事件入口**统一兜住：
 *   · 异常不再杀进程
 *   · 屏幕底部弹一句「操作出错了：xxx」，用户能截图告诉我到底哪一步炸了
 *   · 堆栈同时写进 filesDir/soft_error.txt（诊断导出里能看到）
 *
 * ⚠️ 这只兜"点击处理函数内部"的异常，兜不住**重组阶段**（composition）抛的异常。
 *    所以 composable 里仍然不许做重活，该 try 的地方也要自己 try。
 */
object SafeTap {

    /** 由 CrashGuard.install() 注入，避免到处传 Context */
    @Volatile
    var appContext: Context? = null

    /**
     * 点击震动。偏好读的是内存缓存的 SharedPreferences，开销可以忽略；
     * 任何异常都吞掉 —— 震动失败绝不该影响点击本身。
     */
    fun tap(view: android.view.View?) {
        try {
            val ctx = view?.context ?: appContext ?: return
            if (!com.xixi.hiking.nativeapp.data.Prefs.getBool(
                    ctx, com.xixi.hiking.nativeapp.data.Prefs.KEY_HAPTIC, true
                )
            ) return
            view?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (t: Throwable) {
        }
    }

    fun run(tag: String = "点击", block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e("hiking", "$tag 处理异常（已拦截，不再闪退）", t)
            try {
                com.xixi.hiking.nativeapp.data.CrashGuard.recordSoft("$tag 异常", t)
            } catch (ignored: Throwable) {
            }
            try {
                val msg = (t.message ?: t.javaClass.simpleName).take(60)
                Toast.makeText(appContext, "操作出错了：$msg", Toast.LENGTH_LONG).show()
            } catch (ignored: Throwable) {
            }
        }
    }
}

/**
 * [clickable] 的安全版本：语义、可用性判断、无障碍 role 完全一致，
 * 只是把 onClick 包进 [SafeTap.run]。
 *
 * ★★2026-11-02 两个关键改动：
 *
 * 1. **`indication = null`**（用户要求「把默认涟漪全关了吧」）：
 *    默认涟漪颜色取自 onSurface（浅色灰、深色白），边界是节点矩形，
 *    扩散后要几百毫秒才淡出 —— 在玻璃/圆角控件上看起来就是"冒出一块方块"。
 *
 * 2. **不再传 `interactionSource = null`，改成 [composed] 里 remember 一个实例。**
 *    之前图省事给两边都传 null：编译能过、截图测试也过，但那是边界用法 ——
 *    Compose 内部要往这个 source 发 Press/Release 事件，null 时走"临时新建"分支，
 *    在点击密集的页面（设置页、列表页）有概率在事件分发阶段抛异常，
 *    表现就是「点一下就闪退」。换成稳定的 remember 实例，这条路封死。
 */
fun Modifier.safeClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    /**
     * ★2026-11-05 允许调用方接管交互状态（默认为 null，内部自己建）。
     *
     * 目前只有设置页的开关用它：开关需要「按下时圆钮缩一下、松开回弹」，
     * 这个反馈必须读到 `collectIsPressedAsState()`。而 Compose 的 press 状态
     * 只能从 clickable 的 interactionSource 里拿到 —— 所以得把它暴露出来。
     * 其余一百多个调用点不受影响（不传 = 行为完全不变）。
     */
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = this.composed {
    val src = interactionSource ?: remember { MutableInteractionSource() }
    // ★震动反馈（对齐 Web 版「震动反馈」开关）：默认开，设置页可关。
    //   在事件入口统一做，不用去一百多个调用点各加一行。
    val view = androidx.compose.ui.platform.LocalView.current
    this.clickable(
        interactionSource = src,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role
    ) {
        SafeTap.tap(view)
        SafeTap.run { onClick() }
    }
}

/**
 * [androidx.compose.foundation.combinedClickable] 的安全版本（点按 + 长按）。
 *
 * 长按也用 [SafeTap.run] 兜住 —— 底栏长按切页时会读数据库/写偏好，
 * 这类 handler 抛异常一样会直接杀进程。
 *
 * ★indication 固定传 null：底栏的玻璃自己会做「按下凸显」（放大 + 提亮），
 *   再叠一层系统涟漪会和玻璃质感打架。
 * ★interactionSource 由调用方传，或在这里 remember（不再用 null）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.safeCombinedClickable(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    enabled: Boolean = true,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = this.composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    this.combinedClickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick?.let { l -> { SafeTap.run("长按") { l() } } },
        onClick = { SafeTap.run { onClick() } }
    )
}
