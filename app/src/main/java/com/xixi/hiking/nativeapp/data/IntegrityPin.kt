package com.xixi.hiking.nativeapp.data

/**
 * ★自动生成，勿手改 —— `python tools/signpin.py`（2026-11-05）
 *
 * 内容：官方安装包的**签名证书 SHA-256**（小写、无冒号）。
 * 来源：app-release.apk 的 v2 签名块
 *
 * 用途：启动时软校验（[Integrity]）。签名变了说明这个包不是我用这套代码构建的，
 * 只提示、不退出 —— 和网页版 ResGuard 的"资源校验提示"同一个态度。
 *
 * 换签名（比如以后上架换正式密钥）必须重新跑 signpin 生成这个文件，
 * 否则老用户升级后会看到"不是官方安装包"的误报。
 */
object IntegrityPin {
    const val CERT_SHA256 = "9396fee4e13f3fd1f939d66820d0ca623187be62234fcfcc621577b63ccf8899"
}
