# 原生版（Android）· 构建说明

> 2026-11-02 从 `2026-08-07-13-58-04/hiking-native` 移出，独立存放，避免和网页版混淆。
> 当前版本：`1.4.4`（versionCode 18）

## 一、位置

- 工程：`C:\Users\NIU-XC\Desktop\buddy\hiking-native-20261102`
- JDK：`C:\Users\NIU-XC\Desktop\buddy\2026-08-07-13-58-04\jdk-21.0.12`
- Gradle：`C:\Users\NIU-XC\Desktop\buddy\2026-08-07-13-58-04\gradle-8.2.1`
- Android SDK：`2026-08-07-13-58-04\android-sdk`（`local.properties` 里是绝对路径，移动后仍然有效）

## 二、构建命令（复制即用）

```bash
cd "C:/Users/NIU-XC/Desktop/buddy/hiking-native-20261102"
export JAVA_HOME="C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/jdk-21.0.12"
export GRADLE="C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/gradle-8.2.1/bin/gradle"

"$GRADLE" :app:compileDebugKotlin --console=plain        # 只编译
"$GRADLE" :app:testDebugUnitTest --console=plain          # 门禁 43 项（13 + 3 + 27）
"$GRADLE" :app:assembleRelease --console=plain             # 出包
```

产物：`app/build/outputs/apk/release/app-release.apk`
→ 打包后**必须复制一份到桌面**（用户长期要求）。

## 三、注意

- 旧构建脚本里的 `../gradle-8.2.1` 这类相对路径**已失效**，必须用上面的绝对路径。
- 工程无 git，改动大文件前先备份：`_backup_v144/` 里是 1.4.4 的 gradle 三件套 + 源码包。
- 待办：升级 Gradle 8.10+ / AGP / Kotlin 2.x / Compose 编译器插件，才能引入 `io.github.kyant0:backdrop`（该库要求 kotlin-stdlib 2.3.10）。
