# 原生版开发记录（hiking-native）

> 与 `hiking-app3`（Capacitor + WebView）完全独立，两条线并行。
> 包名 `com.xixi.hiking.nativeapp` —— 与原 App 不同，**两个可同时安装**。

## 技术栈（v1.5.0 · 2026-11-02 大升级）

| 项 | 选择 | 版本 |
|---|---|---|
| 语言 / UI | Kotlin + **Jetpack Compose** | Kotlin **2.2.21** / Compose BOM 2025.11.01（UI **1.9.5**） |
| 数据库 | **Room** | **2.8.5**（KSP 2.2.21-2.0.5） |
| 图片 | **Coil** | 2.5.0 |
| 网络 | **OkHttp** | 4.12.0（WebDAV 同步） |
| 玻璃 | **Kyant0/backdrop** | **1.0.1**（Maven Central） |
| 构建 | AGP **8.9.3** / Gradle **8.11.1** | compileSdk 36 / **minSdk 24** |

**★为什么整条工具链从 Kotlin 1.9.24 / Compose 1.7.2 跳到 2.2.21 / 1.9.5**：
backdrop 1.0.1 是 **Kotlin 2.2.21 编译、依赖 androidx compose 1.9.5** 的。
Kotlin 的 `.class` metadata 向下不兼容 —— Kotlin 1.9 的编译器读不了 2.x 编译的库，
所以这不是"要不要升"的问题，是"不升就接不进来"。连带：
- Kotlin 2.x 起 Compose 编译器变成独立插件 `org.jetbrains.kotlin.plugin.compose`，
  旧的 `composeOptions.kotlinCompilerExtensionVersion` 已废弃
- AGP 8.9.3 要求 Gradle ≥ 8.11.1（AGP 8.7 只能到 compileSdk 35）
- Room 必须跟到 2.8.x（2.6.1 的编译器在 KSP2 下不工作）

**★Gradle 8.11.1 在 `../gradle-8.11.1/`**（不是 8.2.1 了）。
构建命令见文末。

**★backdrop 库的关键 API（拆 aar 得到的，文档站写的和 1.0.1 有出入）**
```kotlin
val layer = rememberLayerBackdrop()            // 图层型：录制一段内容
val canvas = rememberCanvasBackdrop { drawScope -> ... }  // 纯绘制型：适合当"背景"
Box(Modifier.layerBackdrop(layer)) { content } // 标记"内容源"

Modifier.drawBackdrop(
    backdrop = layer,
    shape = { RoundedCornerShape(24.dp) },
    effects = {
        lens(refractionHeight = 10.dp.toPx(), refractionAmount = 18.dp.toPx(),
             chromaticAberration = true)        // 真实折射（API 33+，内部自动 return）
        blur(radius = 14.dp.toPx())             // 高斯模糊（API 31+）
    },
    highlight = { Highlight.Default },          // 边缘镜面高光（内部也是 shader）
    shadow = { Shadow.Default },
    innerShadow = { InnerShadow.Default },
    exportedBackdrop = inner,                   // ★把自己下面的合成结果导出，给子级玻璃采样
    onDrawSurface = { drawRect(...) }            // 玻璃自身的膜（叠在背景上、内容下）
)
```

## ★★ 液态玻璃：三层 backdrop（v1.5.0 重做）

用户原话：「这个液态玻璃，我觉得只是模糊而已」「我要真正的 liquid glass」。
v1.4.x 是手搓 AGSL，能跑但死在根上：**玻璃只能有一层**（卡片在内容层里，采样内容层＝采样自己）。
v1.5.0 换 Kyant0/backdrop，问题从架构上消失：

| backdrop | 类型 | 谁采样它 |
|---|---|---|
| `bgBackdrop` | `rememberCanvasBackdrop`（纯绘制：渐变 + 两团光斑） | **页面里的卡片 / 按钮**。安全：只折射背景色，不会把别的卡片折进来 |
| `contentBackdrop` | `rememberLayerBackdrop`（录制背景 + 全部页面内容） | **底栏 / 弹窗**。真 backdrop：滚到玻璃底下的记录被真实弯折 |
| `dialogBackdrop` | `LayerBackdrop`，由弹窗 `exportedBackdrop` 导出 | **弹窗内部的按钮**。所以「保存」按钮折射的是弹窗自己的底，不是背后的 18 行列表 |

两个 CompositionLocal 分工（一个装不下两种语义）：
- `LocalLiquidBackdrop` → 就近取样（页面＝bg，弹窗内＝dialog）
- `LocalContentBackdrop` → 只有浮层用

### 参数怎么调（改之前先看）

| 旋钮 | 作用 | 取值 |
|---|---|---|
| `refract` | **折射高度，单位 dp** | 浮层 10~14；底栏 11；卡片 6；按钮 5~6。超过 16 边缘开始像鱼眼 |
| `frost` | **模糊强度** 0~1 | → blur 半径 `3 + frost*14` dp |
| `veilAlpha` | **这层玻璃有多不透明** 0~1 | 卡片 0.70（正文压上面，必须顶住）；浮层留空自动算（≈0.1，要能看见背后） |
| `tint` / `tintAlpha` | 颜色浸润 | 冷蓝调，别调大，大了就成彩色塑料 |

**★frost 和 veil 是两件事，别混**：frost 给模糊（背后看得见但糊），veil 给白膜（背后看不见）。
v1.4.x 把两者揉成一个 `frost`，结果弹窗做成 0.86 的奶白 —— 方向完全反了。
iOS 26 的浮层玻璃中心接近清透，存在感来自折射和边缘，不是白雾；但**卡片反过来**，正文直接读，必须靠膜顶住。

### 三重能力降级（每一层都必须有）

1. **`Build.VERSION.SDK_INT < 31` 或软件渲染** → `LiquidGlass.gpuEffectsUsable == false`
   → 完全不走 `drawBackdrop`，直接画静态厚玻璃（alpha 0.96，棱 + 高光 + 色散）。
   **必须早退，不能 try-catch**：RuntimeShader 的报错发生在 Canvas 绘制中途，
   抛出来时半块玻璃已经画上屏幕了。
2. **AGSL 编不过**（`lens()` 内 `RuntimeShader` 失败）→ `lensBroken = true`，永久跳过折射只留模糊。
   真机上少数 OEM 驱动会编不过，Robolectric 100% 编不过（SkSL 编译器太老）。
3. **`backdrop == null` 或开关关闭** → `emptyBackdrop()`，只剩高光 + 染色。

`LiquidGlass.tierLabel()` 会返回当前档位文案，将来接设置页副标题就是排障证据。

### 已知坑

- **`DrawBackdropElement.equals` 比的是 lambda 引用**：`effects` / `highlight` / `shadow` 每次重组新建
  lambda 就会判不等 ⇒ 绘制缓存全废 ⇒ 滚动掉帧。所以那些 lambda 必须 `remember` 住。
- **`remember` 的 T 是非空的**：想表达"没有阴影"不能整个返回 null（会被推成 `Nothing?` 编译报错），
  要返回一个「内部返回 null 的 provider」。
- **Robolectric 是软件 Canvas**：`Software rendering doesn't support RuntimeShader`。
  截图测试里看到的是静态兜底档，**不代表真机**。

## 已实现（v1.3.0 · 1:1 对齐 Web 版）

| 模块 | 内容 |
|---|---|
| **全局** | 顶栏（🏔️ + 可点改名的标题，最多 20 字，存 Prefs）/ 玻璃底栏（真折射） |
| **概览** | 引导卡 → 时光机（三层回退：同月同日 / 同月 / 整百天）→ 备份健康提示 → 统计 2+2+3 七指标 → 里程碑入口（红点）→ 徒步足迹热力图（年月可选 + 当天记录）→ 年度回顾（含「和去年比」） |
| **记录** | 工具行（✓ 批量 / ＋ / 山册⇄列表）/ 视图说明 / 列表表格（年份分组 + 排序 + 分页 10 条）/ 批量选择与删除 / 搜索 / 引导卡 / **山册双列名册**（集章序号 + 海拔书脊五档 + 整行抽屉：照片回忆 + 6 格统计 + 最近一次 + 连接器） |
| **计划** | 工具行（✓ 批量 / ＋ / 列表⇄日历）/ 月历（今天圆角描边、有计划打绿点、点日期看安排）/ 列表表格 + 分页 + 批量 / 引导卡 |
| **里程碑** | **17 档**（5 山峰 + 3 里程 + 4 高度 + 5 收集类），实时计算（删记录立即失效），收集类列清单打勾，「已查看」簿记控制入口红点 |
| **设置** | 数据统计 / 导出·导入 JSON（含照片 dataURL）/ WebDAV 同步（配置·测试·上传·恢复·自动）/ 外观三档 / **抹掉所有足迹**（双重确认 + 解绑网盘）/ 关于 |
| **照片** | 相册多选 → 长边 1600 压缩 → 文件系统 → 详情照片墙 → 全屏灯箱左右滑 |
| **分享卡** | Canvas 直绘 1080×1440 → JPG → FileProvider 系统分享 |

**字段对齐**：`RecordEntity` 与 Web 版 records[] 一一对应，备份双向可导入。

## 源码规模

**27 个 Kotlin 文件 / 约 5600 行**；release APK **11.5 MB**（未混淆）。

```
MainActivity.kt                     入口 + 顶栏 + 4 tab + 玻璃底栏 + 种子数据
data/Entities.kt                    三个实体（记录/计划/照片）
data/Daos.kt                        三个 DAO + AppDatabase（version 2）
data/Milestones.kt                  17 档里程碑 + 统计 + 山名/天气归一化
data/MtnMerge.kt                    山册连接器（同山异名归并，链式，只在山册生效）
data/Photo.kt                       照片文件存取
data/Prefs.kt                       轻量偏好（主题/标题/引导/备份时间/里程碑已读）
data/WebDavClient.kt                WebDAV（PUT/GET/PROPFIND + BasicAuth）
ui/Glass.kt                         模糊玻璃 liquidGlass / glassPanel / Radii
ui/GlassOverlay.kt                  同窗口玻璃浮层（替代 Dialog；自动选真折射/模糊）
ui/GlassWidgets.kt                  自绘输入框 / 按钮 / 确认 / 分段
ui/liquid/LiquidGlass.kt            ★★真折射：AGSL + GraphicsLayer 离屏录制 + 三重降级
ui/theme/Theme.kt                   浅深配色 + LocalIsDark + AccentIndigo（随主题）
ui/milestones/MilestoneListDialog.kt 里程碑一览 + 入口卡
ui/records/RecordListScreen.kt       列表/山册切换 + 表格 + 分页 + 批量 + 引导
ui/records/MountainBookView.kt       山册双列 + 抽屉 + 连接器
ui/records/RecordDetailDialog.kt     详情
ui/records/RecordEditDialog.kt       编辑表单
ui/overview/OverviewScreen.kt        概览（时光机/统计/里程碑/热力图）
ui/overview/HeatmapView.kt           热力图 + 年月选择 + 当天记录
ui/overview/YearReviewDialog.kt      年度回顾（+ 和去年比）
ui/plans/PlansScreen.kt              计划页（日历/列表双视图 + 批量）
ui/plans/PlanCalendarView.kt         月历
ui/plans/PlanDetailDialog.kt         计划详情/编辑
ui/photos/Photos.kt                  缩略图/照片墙/选择网格/灯箱
ui/share/ShareCardDialog.kt          分享卡
ui/settings/SettingsScreen.kt        设置
ui/settings/SyncConfigDialog.kt      WebDAV 配置
```

## 踩坑记录

1. **`gradlew` 下载 Gradle 发行版失败**（`PKIX path building failed`）。
   用项目根 `gradle-8.2.1/bin/gradle`，并带 `-Dmaven.wagon.http.ssl.insecure=true`。
2. **AGP 8 默认关 `BuildConfig`** ⇒ `buildFeatures { buildConfig = true }`。
3. **`HorizontalPager` 是实验 API** ⇒ `@OptIn(ExperimentalFoundationApi::class)`。
4. `HazeMaterials.ultraThin()` 是 **@Composable**，不能塞进普通 lambda。
5. **深色模式三连坑**（用户报过「文字看不见」）：
   - 各处用 `isSystemInDarkTheme()` 判断 ⇒ 设置页强制深色时不生效 ⇒ 统一改 `LocalIsDark`（HikingTheme 提供）
   - 硬编码 `Indigo`（#4F46E5）在深底上对比度仅 2.4:1 ⇒ 统一 `AccentIndigo`（深色自动换 #A5B4FC）
   - 硬编码 `Color.White.copy(alpha=0.85f)` 当按钮底 ⇒ 深色下白底压浅字 ⇒ 一律按 `isDark` 分支
6. **浮层留白别两头加**：MainActivity 曾经加了 88dp 给底栏让位，各页又各加 96~100dp ⇒ 底部空出 184dp。
   现在统一由各页自己留尾部 padding，MainActivity 不加。

## 待办

- [ ] **混淆**（R8 规则要单独调，Compose + Room + KSP 都有反射需求）
- [ ] **通知/提醒**（WorkManager + 计划日期；需 POST_NOTIFICATIONS）
- [ ] **iOS 版**（Compose Multiplatform 还是 SwiftUI，待定）
- [ ] 真折射在低端机上的帧率实测（现在每帧离屏录整页，理论上是主要开销）

## 工具链（`tools/`，参考 Web 版那套脚本）

一条命令拿到结论，不靠人记：

| 脚本 | 干什么 | 用法 |
|---|---|---|
| `gate.sh` | **唯一门禁**：文档一致 + 套件一致 + 设计检查 + 全量测试 | `bash tools/gate.sh` |
| `bump.py` | 升 vc → 自动算 versionName → 补 README 表 + 更新日志 | `python tools/bump.py --note "改了什么"` |
| `release.py` | 一键发版：bump → 门禁 → 打包 → APK 体检 → 拷桌面并规范命名 | `python tools/release.py --note "..."` |
| `docfresh.py` | 文档真值比对（版本号 / 测试项数 / 路径存在性） | `python tools/docfresh.py [--fix]` |
| `suiteguard.py` | 新增测试类有没有真的被门禁跑到 | `python tools/suiteguard.py` |
| `designcheck.py` | 设计一致性：emoji / 过小字号 / 弹窗宽度档位 / 颜色散落 | `python tools/designcheck.py [--strict]` |
| `apkcheck.py` | APK 体检：versionCode·Name / 图标 / INTERNET / minSdk / 体积 | `python tools/apkcheck.py` |
| `map.py` | 生成 `docs/CODE-MAP.md`（46 个文件、1.5 万行，一眼找东西） | `python tools/map.py [--check]` |
| `shots.sh` | 只跑截图，图按顺序拷到 `build/shots/` | `bash tools/shots.sh [关键字]` |
| `stress.sh` | 只跑点击冒烟 + 压测（约 40 秒） | `bash tools/stress.sh` |

标准流程：**改代码 → `bash tools/gate.sh` → `python tools/release.py --note "..."`**。
`gate.sh` 里任何一环失败都不许发版；`release.py` 会把门禁结果和 APK 体检一起打出来。


## 版本号规则（2026-11-02 用户定死）

**四段：`1.x.x.x`，唯一输入是 `versionCode`。**

```
索引   = vc - 1
第四段 = 索引 % 11
第三段 = (索引 / 11) % 11
第二段 = (索引 / 121) % 11
```
每段 0~10 共 11 个值，满 10 进位。

已经写进 `app/build.gradle.kts` 的 `defaultConfig`，**以后只改 `val vc = N` 这一行**，
`versionName` 自动算，不用手抄（也不要再手写 1.5.x 这种）。

对照表（近几版）：
| vc | 索引 | versionName |
|---|---|---|
| 53 | 52 | 1.0.4.8 |
| 55 | 54 | 1.0.4.10 |
| 56 | 55 | 1.0.5.0 |
| 57 | 56 | 1.0.5.1 |
| 58 | 57 | 1.0.5.2 |
| 59 | 58 | 1.0.5.3 |
| 60 | 59 | 1.0.5.4 |
| 61 | 60 | 1.0.5.5 |
| 62 | 61 | 1.0.5.6 |
| 63 | 62 | 1.0.5.7 |
| 64 | 63 | 1.0.5.8 |
| 65 | 64 | 1.0.5.9 |
| 66 | 65 | 1.0.5.10 |
| 67 | 66 | 1.0.6.0 |
| 68 | 67 | 1.0.6.1 |
| 69 | 68 | 1.0.6.2 |
| 70 | 69 | 1.0.6.3 |
| 71 | 70 | 1.0.6.4 |
| 72 | 71 | 1.0.6.5 |
| 73 | 72 | 1.0.6.6 |
| 74 | 73 | 1.0.6.7 |
| 75 | 74 | 1.0.6.8 |
| 76 | 75 | 1.0.6.9 |
| 77 | 76 | 1.0.6.10 |
| 78 | 77 | 1.0.7.0 |
| 79 | 78 | 1.0.7.1 |
| 80 | 79 | 1.0.7.2 |
| 81 | 80 | 1.0.7.3 |
| 82 | 81 | 1.0.7.4 |
| 83 | 82 | 1.0.7.5 |
| 84 | 83 | 1.0.7.6 |
| 85 | 84 | 1.0.7.7 |
| 86 | 85 | 1.0.7.8 |
| 87 | 86 | 1.0.7.9 |
| 88 | 87 | 1.0.7.10 |
| 89 | 88 | 1.0.8.0 |
| 90 | 89 | 1.0.8.1 |
| 91 | 90 | 1.0.8.2 |
| 92 | 91 | 1.0.8.3 |
| 93 | 92 | 1.0.8.4 |
| 94 | 93 | 1.0.8.5 |
| 95 | 94 | 1.0.8.6 |
| 96 | 95 | 1.0.8.7 |
| 97 | 96 | 1.0.8.8 |
| 98 | 97 | 1.0.8.9 |
| 99 | 98 | 1.0.8.10 |
| 100 | 99 | 1.0.9.0 |
| 101 | 100 | 1.0.9.1 |
| 102 | 101 | 1.0.9.2 |
| 103 | 102 | 1.0.9.3 |
| 104 | 103 | 1.0.9.4 |
| 105 | 104 | 1.0.9.5 |
| 106 | 105 | 1.0.9.6 |
| 107 | 106 | 1.0.9.7 |
| 108 | 107 | 1.0.9.8 |
| 109 | 108 | 1.0.9.9 |
| 110 | 109 | 1.0.9.10 |
| 111 | 110 | 1.0.10.0 |
| 112 | 111 | 1.0.10.1 |
| 113 | 112 | 1.0.10.2 |
| 114 | 113 | 1.0.10.3 |
| 115 | 114 | 1.0.10.4 |
| 116 | 115 | 1.0.10.5 |
| 117 | 116 | 1.0.10.6 |
| 118 | 117 | 1.0.10.7 |
| 119 | 118 | 1.0.10.8 |
| 120 | 119 | 1.0.10.9 |
| 121 | 120 | 1.0.10.10 |
| 122 | 121 | 1.1.0.0 |
| 123 | 122 | 1.1.0.1 |
| 124 | 123 | 1.1.0.2 |
| 125 | 124 | 1.1.0.3 |
| 126 | 125 | **1.1.0.4** |
| 127 | 126 | 1.1.0.5 |

## 构建

```bash
export JAVA_HOME="<项目根>/jdk-21.0.12"
cd hiking-native
# ★Gradle 用 8.11.1（AGP 8.9.3 的硬要求），不是老的 8.2.1
../gradle-8.11.1/bin/gradle :app:assembleRelease -Dmaven.wagon.http.ssl.insecure=true
# 产物：app/build/outputs/apk/release/app-release.apk
```

**门禁（43 项，报绿前必须核对项数）**
```bash
../gradle-8.11.1/bin/gradle :app:testDebugUnitTest -Dmaven.wagon.http.ssl.insecure=true
# 期望：ImportCompat 13 + LiquidGlassSmoke 3 + UiSnapshot 27
# 项数变少 = 有测试被跳过 = 假绿
```
