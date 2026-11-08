package com.xixi.hiking.nativeapp.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 从一个 Context 往上找 Activity。
 *
 * 用途：需要 Activity（比如 `finish()`、`window`）而手上只有 `LocalContext.current` 时。
 * Compose 里拿到的通常是 ContextThemeWrapper，不能直接强转。
 */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
