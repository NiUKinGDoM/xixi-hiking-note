// ★「XiXiの徒步小记」原生版 —— 顶层构建脚本
// 与 hiking-app3（Capacitor + WebView）完全独立，两条线并行互不影响。
//
// ★2026-11-02 工具链升级（为了接 Kyant0/backdrop 液态玻璃库）：
//   Kotlin 1.9.24 → 2.2.21   —— 库是 Kotlin 2.2 编译的，1.9 读不了它的 metadata
//   AGP    8.2.1  → 8.9.3    —— compileSdk 36 正式支持
//   Gradle 8.2.1  → 8.11.1   —— AGP 8.9 的最低要求
//   Compose 1.7.2 → 1.9.5    —— 库的 runtime 依赖（BOM 2025.11.01）
//   ★Kotlin 2.x 起 Compose 编译器是独立插件（org.jetbrains.kotlin.plugin.compose），
//     不再用 composeOptions.kotlinCompilerExtensionVersion（那个字段已废弃）。
plugins {
    id("com.android.application") version "8.9.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}
