# 原生版全流程手册

> 这份文件的目的只有一个：**半年后回来，不用重新摸索一遍**。
> 每次改了流程（换了 JDK、换了签名、发了新版）就往这里补一句，别只记在脑子里。
>
> 最后更新：2026-11-05（v1.0.9.11）

---

## 0. 一句话总览

```
改代码 → bash tools/doctor.sh（10 秒）→ python tools/release.py（全自动发版）
                                              ↓
                                   桌面得到 APK + mapping 归档
                                              ↓
                         python tools/ghrelease.py upload（测试版）
                                              ↓
                       验完 → python tools/ghrelease.py promote（转正式版）
```

---

## 1. 环境

| 项 | 值 |
|---|---|
| JDK | `C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/jdk-21.0.12` |
| Gradle | `.../gradle-8.11.1/bin/gradle.bat`（**Windows 必须用 .bat**，用 sh 会 WinError 193） |
| minSdk / target | 24 / 36（Compose 模糊、AGSL 需要 31+，运行时自动降级） |
| 签名 | 目前用 **debug keystore**（`~/.android/debug.keystore`），方便直接装 |
| 包名 | `com.xixi.hiking.nativeapp`（与网页版 `com.xixi.hiking` 不同 → 两个独立应用，可共存） |

脚本会自动用上面的 JDK/Gradle；要覆盖就设 `JAVA_HOME` / `GRADLE_BIN`。

---

## 2. 日常改动

改完代码，**先跑体检，别直接跑门禁**：

```bash
bash tools/doctor.sh          # 10 秒，不编译
```

它跑 8 项：崩溃点 / 代码气味 / 扫描器自检 / 设计一致性 / 版本文档一致 /
说明文案长度 / 系统审计 / 上次单测结果。红了再改，绿了再往下。

```bash
bash tools/doctor.sh --with-lint   # 额外跑 Android Lint（约 2 分钟）
```

### 常用脚本

| 脚本 | 干什么 | 什么时候用 |
|---|---|---|
| `tools/doctor.sh` | 一键体检（静态，10 秒） | 每次改完 |
| `tools/gate.sh` | 完整门禁（编译 + 106 项测试 + Lint） | 发版前 |
| `tools/release.py` | 升版本→门禁→打包→体检→拷桌面 | 发版 |
| `tools/crashscan.py` | 崩溃点扫描（`!!` / 下标 / 转型 / 日期解析） | 体检里自动跑 |
| `tools/smellscan.py` | 气味扫描（组合期写 state / 主线程 IO / Lazy 缺 key） | 体检里自动跑 |
| `tools/textcheck.py` | 说明文案 ≤10 字 | 体检里自动跑 |
| `tools/obfcheck.py` | 混淆体检（关键资源/关键类/mapping） | 发版里自动跑 |
| `tools/signpin.py` | 生成/校验签名指纹常量 | 换签名时 |
| `tools/ghrelease.py` | GitHub 发测试版 / 转正式版 | 发布时 |
| `tools/logcat.py` | 抓真机崩溃 / ANR / 软错误 | 真机出问题时 |

---

## 3. 门禁（gate.sh）到底跑什么

```
①  版本号 / 文档一致性     更新日志首条必须等于当前 versionName
①b 门禁套件一致性         确保所有测试类都被跑到
①c 设计一致性             emoji 禁令 / 字号 / 弹窗宽度档位
①d 系统级 + 软件层审计     权限、明文流量、崩溃兜底、未用 import…（提示性）
①d2 说明文案长度          所有界面中文说明 ≤10 字
①e 崩溃点扫描             HIGH 必须为 0
①f 代码气味扫描           先自检规则，再扫 ERROR=0
①g Android Lint           error 必须为 0（抓到过 Android 7~9 分享卡必崩）
②  全量单测               106 项（含截图 / 点击冒烟 / 压测 / 备份往返）
③  汇总
```

任何一步非 0 退出 → 不许发版。

**为什么 Lint 在门禁里**：它抓到过一个只在旧系统上炸的崩溃（`LinearGradient` 的
Int/Long 重载走错），新手机永远测不出来。

---

## 4. 发版

```bash
python tools/release.py --note "这一版改了什么"
```

它依次做：

1. `tools/bump.py` 升 `versionCode`（版本名按 4 段规则自动算，**只改 vc 一行**）
2. `bash tools/gate.sh`
3. `:app:assembleRelease`（**开了 R8 混淆 + 资源瘦身**）
4. `tools/apkcheck.py`（图标 / 权限 / minSdk / 体积）
5. `tools/signpin.py --verify`（APK 签名 == 代码里钉的指纹）
6. `tools/obfcheck.py`（关键资源 & 关键类没被混淆删掉 + mapping 归档）
7. 重命名 + 拷到桌面

### 版本号规则

`1.x.x.x`，只看 `versionCode`：

```
idx = vc - 1
第四段 = idx % 11    第三段 = (idx/11) % 11    第二段 = (idx/121) % 11
```

所以**永远只改 `app/build.gradle.kts` 里的 `val vc = N` 这一行**，别手抄版本名。

### 混淆（R8）

- `isMinifyEnabled = true` + `isShrinkResources = true`：**17.5 MB → 6.5 MB**
- 规则在 `app/proguard-rules.pro`，原则是**只保留"运行时要靠名字找"的东西**：
  - Room 的 `AppDatabase_Impl`（反射加载）、`@Entity`、`@Dao`
  - 清单里的 Activity
  - 异常类名（诊断报告要显示）
  - 第三方库用 `-keepnames`（只保名不删代码）：okhttp3 / coil / backdrop / haze
- **必须保留** `-keepattributes SourceFile,LineNumberTable`，否则崩溃堆栈连行号都没有
- 资源名会被改成 `res/Vk.ttf` 这种短名 → 想确认某资源在不在，看
  `app/build/outputs/mapping/release/resources.txt` 里的 `reachable=true`

### mapping.txt（崩溃还原）

每次发版自动 gzip 归档到 `_debug/mapping/mapping-v<版本>.txt.gz`（约 2.5 MB）。

线上崩溃长这样：

```
at a.a.a(SourceFile:151)
```

还原：

```bash
python tools/obfcheck.py --retrace 崩溃.txt
# 指定历史版本：
python tools/obfcheck.py --retrace 崩溃.txt --mapping _debug/mapping/mapping-v1.0.9.10.txt.gz
```

它只还原**类名**（90% 的定位信息在这里），不做行号重排。

---

## 5. 发布到 GitHub（测试版 → 正式版）

用户的诉求：原生包先当**测试版**发，自己验几天，没问题再"转正"。

GitHub 的 pre-release 正好是这个语义：

| 状态 | 谁能收到 |
|---|---|
| `prerelease = true`（测试版） | 只有在 App 里开了「测试版通道」的人 |
| `prerelease = false`（正式版） | 所有人（默认通道） |

### 一次性准备

1. GitHub → Settings → Developer settings → Personal access tokens → 勾 `repo`
2. 把 token 写进环境变量 `GITHUB_TOKEN`，或存到仓库根的 `.gh_token`（一行）

### 发测试版

```bash
python tools/ghrelease.py upload \
  --tag v1.0.9.11 \
  --apk "C:/Users/NIU-XC/Desktop/XiXi原生版-v1.0.9.11-xxx.apk" \
  --name "原生版 v1.0.9.11" \
  --notes-file docs/notes-1.0.9.11.md       # 可选，会显示在更新弹窗里
```

- 默认就是 `prerelease=true`，加 `--official` 才是直接发正式版
- 脚本会拦一道：**标签必须是 `v` + 四段数字**。写成 `v1.0.9` 的话，
  App 的逐段比较会当它是老版本，永远收不到更新，而且**不报错**。

### 验证完转正

```bash
python tools/ghrelease.py promote v1.0.9.11
```

资产不重传，只改标记 → 默认通道的人立刻能收到。

### 其它

```bash
python tools/ghrelease.py list               # 看每条的通道状态
python tools/ghrelease.py demote v1.0.9.11   # 退回测试版
python tools/ghrelease.py delete v1.0.9.11 --yes
```

> 仓库是 **web 版那个**（`NiUKinGDoM/xixi-hiking`）。App 的更新检查按
> "资产名含「原生」或 `native`" 挑出原生包，所以网页版的 release 不会误报。

---

## 6. 安装包完整性（对应网页版 ResGuard）

网页版校验资源哈希；原生版资源编进 APK，改一个字节就必须重签 —— 所以校验的是
**签名证书指纹**：

- `tools/signpin.py` 从 APK 的 v2 签名块里取出证书 DER，算 SHA-256，
  生成 `data/IntegrityPin.kt`（自动生成，勿手改）
- App 启动时比对 `PackageManager` 读到的签名，不一致就弹一次「安装包已被改动」
  （**软提示**：不退出、不禁用）
- 设置页有一行「安装包校验」，显示「官方签名 / 已被改动 / 无法读取」
- 诊断报告里也有一行

### 换签名时（重要）

以后上架要换正式密钥：

```bash
# 1. 改 build.gradle.kts 的 signingConfig
# 2. 重新生成指纹
python tools/signpin.py --keystore     # 或 --apk <已签名的包>
# 3. 重新打包（release.py 会自动 --verify）
```

**忘了这步的后果**：所有老用户升级后都会看到"安装包已被改动"的误报，
而且只有真机能发现（单测跑不出）。

---

## 7. 回退

| 场景 | 怎么做 |
|---|---|
| 新版本有功能问题 | 直接装 `_apk_archive/baseline-v1099.apk`（未混淆的 1.0.9.9） |
| 混淆导致某个功能坏 | 同上；或把 `isMinifyEnabled` 改回 false 重打 |
| GitHub 发错了 | `ghrelease.py demote <tag>` 或 `delete <tag> --yes` |
| 数据出问题 | 设置 → 本地备份 / 云端备份，恢复上一份 |

> 装回旧版不会丢数据：Room 库结构没变时直接覆盖安装即可。
> 如果哪天改了表结构（加了字段），必须写 Room 迁移，否则装回去会崩。

---

## 8. 数据与备份

| 类型 | 位置 | 说明 |
|---|---|---|
| 记录 / 计划 | Room（`hiking.db`） | 卸载即消失 |
| 照片 | 应用私有目录 | 同上 |
| 本地自动备份 | 应用私有目录，保留 7 份 | 每天一份 |
| 云端备份 | 用户自己的 WebDAV（`xixi-hiking/`） | 保留最近 2 份，与网页版同名同格式 |
| 完整备份 zip | 导出到用户选的位置 | `xixi-data.json` + `photos/` + `回忆册.html` |

**与网页版的关系**：两边格式互通（zip 双向可读），但**数据是两份独立存储**。
迁移路径：网页版导出 zip → 原生版「导入数据」；之后云端备份两边互相可见。

---

## 9. 真机排查

```bash
python tools/logcat.py --wait 20     # 清空 → 你复现 → 20 秒后自动抓
```

输出分四类：`FATAL`（崩溃）、`ANR`（卡死）、`SOFT`（App 自己记的软错误，
tag=`hiking`）、`GLASS`（液态玻璃降级）。原始日志在 `_debug/logcat-*.txt`。

没有 adb 的话：

```
winget install --id Google.PlatformTools -e
```

App 内也有：「设置 → 诊断报告 → 分享」，含最近 20 步操作轨迹 + 上次崩溃堆栈 +
签名校验结论，不含任何记录内容。

---

## 10. 踩过的坑（别再踩）

| 坑 | 症状 | 原因 |
|---|---|---|
| 抄 web 数值 | 排版全错 | 漏了 `@media(max-width:640px)` 手机端覆盖 |
| 字体不生效 | 中文还是黑体 | Android 的 `serif` 只挂拉丁，必须内嵌 Noto Serif SC |
| 分享卡 OOM | 预览一直"正在生成" | 图放 `drawable/` 被 3x 屏放大到 27MB，挪 `drawable-nodpi` |
| 弹窗按钮全失效 | 点什么都没反应 | `HorizontalPager` 抢手势；Robolectric 零位移点击测不出来 |
| 弹窗永久隐身 | 下层弹窗打不开 | 互斥用单值 top，改成真栈 |
| 白条 | 弹窗里一条纯白横条 | `Modifier.shadow(clip=false)` + `clip()` 漏底 |
| 灯箱照片小 | 竖图只显示一小块 | 数据库里宽高为 0，兜底 4:3；改成等真实像素比例 |
| 热力图默认空月 | 一进去没数据 | `remember` 只求值一次，records 异步；改 `LaunchedEffect` |
| 分享卡旧机崩 | Android 7~9 点分享闪退 | `0xFFFFFFFF` 被推成 Long → 调了 API 29 的重载 |
| 混淆后堆栈不可读 | `a.a.a(SourceFile:42)` | 没加 `-keepattributes SourceFile,LineNumberTable` |
| 混淆删资源 | 分享底图/收款码空白 | 需要 `obfcheck` 检查 `reachable=true` |

---

## 11. 相关文档

- `docs/REGRESSION.md` —— 手工回归清单（每次发版前照着点一遍）
- `docs/OBFUSCATION.md` —— 混淆细节（如果和本文件重复，以本文件为准）
- `README.md` —— 项目简介
- `构建说明-ANDROID.md` —— 早期构建笔记（历史参考）

## 6. 检查更新链路（2026-11 重构，改之前先读这节）

以前 App 直接查 `api.github.com/repos/.../releases`，**国内基本不通** ——
本机实测 curl 直连三次全是 1 毫秒断连（`HTTP=000`），只有挂代理才通。
所以「检测更新老是失败」不是代码 bug，是网络到不了。

现在是三层：

| 层 | 地址 | 作用 |
|---|---|---|
| ① 版本清单 | `cdn.jsdelivr.net/gh/NiUKinGDoM/xixi-hiking-note@main/version.json` | **主源**，国内直连约 0.25s |
| ② GitHub API | `api.github.com/.../releases` | 兜底，海外或有代理时可用 |
| ③ 安装包 | gh-proxy → gh.xmly → 官方直链 | 三源轮询，支持断点续传 |

### version.json 是谁写的

`tools/release.py` 在发布成功后自动：

1. 写 `version.json`（tag / version / apk 名 / 直链 / 正文 / 发布时间）
2. 调 `https://purge.jsdelivr.net/gh/.../version.json` 清 CDN 缓存
3. `git add version.json && commit && push`

> ⚠️ **第 3 步失败等于没发布**：jsDelivr 读的是仓库里的文件，没 push 上去，
> App 就永远读不到新版（而且界面看起来一切正常）。脚本会打一行
> 「version.json 已写好但没推上去」，看到就手动 push 一次再 purge。

### jsDelivr 的缓存坑

`@main` 分支引用最长缓存 **12 小时**。发完版不 purge 的话，用户点「检测更新」
还是旧版本。purge 接口是公开的，一次 GET 就够：
`https://purge.jsdelivr.net/gh/<user>/<repo>@main/version.json`

### 断点续传的边界

- 带 `Range` 头 → 服务端回 **206** 才追加写；回 **200** 说明不支持，清 `.part` 重下
- 网络中断时**故意不删** `.part`（这是续传的前提）
- 换镜像时沿用同一份 `.part`：同一 release 的同一 APK 各镜像字节一致，
  拼坏也会被签名校验挡住

## 7. 代码规模守卫（sizeguard.py）

单文件：**> 1500 行 ERROR（门禁红），> 800 行 WARN**。

为什么阈值是 1500 而不是 800：一刀切会把 `MainActivity`(1453)、`LiquidGlass`(1400)
也判成 ERROR，那不是防膨胀是逼着立刻重构 —— 门禁一红就发不了版，最后只能整个关掉。

目标只有一个：**不许再出现 1700 行的单文件**（`SettingsScreen` 曾经一个函数 1260 行）。

## 8. 字体（不要随手子集化）

`app_serif_regular.ttf` / `app_serif_bold.ttf` 各 3.35MB，是**全站正文**的宋体字形。

`tools/subset_fonts.py` 能砍到约 2.36MB（-29%），但**当前不启用**：

- 全站正文都用这个字体，砍字意味着生僻山名会回落到系统黑体（能显示，但字形不一致）
- 省 2MB 换视觉不一致，不值

真要启用时的注意点：

- 字符集 = GB2312 全集 + 源码里出现的所有字符（保证界面文案不缺字）
- 缺字时 Android 会回退系统字体，不会出豆腐块
- 原件会备份到 `_backup_fonts/`，要还原直接拷回去

## 9. 设置页结构（2026-11 拆分）

`SettingsScreen.kt` 曾是一个 1746 行的文件、其中单个 `@Composable` 函数 1260 行。
现在 815 行，四个分组各自独立：

| 文件 | 内容 |
|---|---|
| `SettingsScreen.kt` | 状态声明 + 标题 + 四个 Section 调用 + 弹窗调度 |
| `SettingsSections.kt` | `DataSection` / `AppearanceSection` / `MiscSection` / `AboutSection` |
| `SettingsWidgets.kt` | `SettingRow` / `InfoTile` / `StatInline` / `AboutLink` / `ExportOpt` / `ioScope` |

**状态归属三条规则**（拆新的分组时照这个来）：

1. 只在组内用的状态 → 内聚进组件（`checkingUpdate` / `syncBusy` / `autoSync`）
2. 只读的值 → 直接传参（`photoUsageDesc` / `pageSizePref` / `dav` / `syncTick`）
3. 有读有写、且语义不止"打开" → 传 setter（`onShowSync: (Boolean) -> Unit`，
   因为 `showSync` 解绑后要置 false 关闭，单向回调会丢语义）

> 拆出去的函数一律**无状态**：不持有 `remember`、不读写 `Prefs`、不碰 `context`。
> 状态留在父级，否则切页再回来状态会被重建（填一半的配置全丢）。

## 10. 视觉基线（2026-11-12 新增，Web 版有 133 项 E2E 基线，这里是原生版的对位）

`UiSnapshotTest` 原来只把 43 张截图**生成**到 `build/ui-shots`，**不比对** ——
也就是说改一个弹窗的字号，没有任何东西能告诉你另外 11 个有没有被带歪。

现在加了基线比对：截图降采样到 360 宽后存 `app/src/test/resources/ui-baseline/`
（46 张，约 3MB，**必须提交进 git**），每次跑测试逐像素对比。

### 判定规则

| 项 | 值 | 为什么 |
|---|---|---|
| 降采样宽度 | 360px | 原图约 1080 宽，一张 PNG 几百 KB，43 张进 git 十几 MB；缩到 360 后每张 30~80KB，且缩放天然吸收 1px 级抗锯齿差异 |
| 单通道容差 | ±10 | Robolectric 的 Skia 渲染和真机不同，同一次改动重跑也会有极小抖动 |
| 允许不一致比例 | 0.6% | 太严会变成"每次都红"，那种守卫最后一定会被关掉 |

### 怎么用

```bash
# 正常跑（门禁里已包含），超阈值就红
bash tools/gate.sh

# 改动是**有意的**、确认过没问题 → 刷基线
./gradlew testDebugUnitTest -DupdateBaseline=true --tests "*UiSnapshotTest*"

# 差异图：红色 = 对不上的像素，压暗 = 一致的部分
ls build/ui-shots/diff/
```

**刷完基线记得把新的 PNG 一起提交**，否则别人（或 CI）跑还是旧基线。

### 守卫自检（重要）

守卫必须能证明自己会红，否则可能是个"永远绿"的假守卫。验证方法：

1. 临时把 `UiSnapshotTest.PageBg` 里的背景色 `0xFFF7F8FD` 改成 `0xFFFF0000`
2. 跑 `--tests "*UiSnapshotTest.shot_records*"` → **必须 FAILED**
3. 改回来 → 再跑必须绿

> ⚠️ 注意：改 `Theme.kt` 里的 `DangerRed` 不会让 `shot_records` 变红 ——
> 记录列表页里根本没有红色元素。要自检就改**所有截图都有的东西**（背景色）。

### @AfterClass 必须写在 companion 里

JUnit4 只认「测试类本身的静态 @AfterClass」。写在嵌套 object 里**根本不会被调用** ——
那等于基线比对永远不报告，测试永远绿，比没有守卫更糟。

