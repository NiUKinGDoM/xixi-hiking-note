# Web ⇄ 原生 功能对齐表

> 自动生成：`python tools/webfeature.py --md docs/WEB-PARITY.md`
> 判定表是人工整理的（web 的弹窗全在 JS 里拼，纯字符串匹配跑不通）；
> 脚本负责校验「原生关键字」是否还在，代码一变就会报「文档过期」。

| # | 模块 | 功能 | Web 入口 | 状态 | 说明 |
|---|---|---|---|---|---|
| 1 | 概览 | 里程碑达成庆祝（彩屑 + 提示） | `showMilestoneCelebration` | ❌ 未做 | 原生只在「计划完成转记录」撒彩屑；里程碑达成时没有任何反馈 |
| 2 | 记录 | 首条记录庆祝提示 | `showFirstRecordTip` | ❌ 未做 | web 记下第一条会弹「第一笔已记下」并撒彩屑，原生没有 |
| 3 | 设置 | 同步状态（上次同步时间/连接结果） | `showSyncStatusModal` | ❌ 未做 | web 有独立的「同步状态」弹窗，显示上次同步时间与连接结果；原生只有操作后的一条 toast |
| 4 | 其他 | 液态玻璃 / 模糊 | `backdrop-filter` | ⚠️ 有差异 | web 用 CSS；原生用 AGSL/RenderEffect，低版本降级 |
| 5 | 其他 | 安装包完整性校验 | `ResGuard` | ⚠️ 有差异 | web 校验资源哈希；原生校验签名指纹（更硬） |
| 6 | 关于 | GitHub / 官网入口 | `GitHub` | — 故意不做 | 2026-11-04 用户要求删除 |
| 7 | 其他 | PWA 安装引导 | `beforeinstallprompt` | — 故意不做 | 原生不需要 |
| 8 | 其他 | Service Worker 离线缓存 | `sw.js` | — 故意不做 | 原生本身就是离线应用 |
| 9 | 设置 | 同步教程演示 | `showSyncDemoModal` | — 故意不做 | web 给第一次绑定的人看图文步骤；原生用「去导入/云端」引导卡代替，不做图文教程 |
| 10 | 关于 | 隐私政策 | `showPrivacyPolicyModal` | ✅ | 11 条，与 web 同文 |
| 11 | 关于 | 免责声明 | `showDisclaimerModal` | ✅ | 11 条 |
| 12 | 关于 | 更新日志（全版本可展开） | `showChangelogModal` | ✅ |  |
| 13 | 关于 | 支持作者（收款码） | `showSupportModal` | ✅ |  |
| 14 | 分享 | 分享卡（1080×1440） | `generateShareCard` | ✅ | 保存到相册 |
| 15 | 分享 | 回忆册 HTML（随完整备份） | `buildMemoirHtml` | ✅ |  |
| 16 | 概览 | 四张统计卡（里程/海拔/座数/时长） | `overview 统计区` | ✅ |  |
| 17 | 概览 | 里程碑卡（17 档进度） | `showMilestoneSummary` | ✅ |  |
| 18 | 概览 | 里程碑一览弹窗 | `showMilestoneList` | ✅ |  |
| 19 | 概览 | 热力图（按月/日，色阶） | `app-data-heatmap.js` | ✅ |  |
| 20 | 概览 | 热力图年月选择器 | `openHmYmPicker` | ✅ | 选年不关、选月才关 |
| 21 | 概览 | 热力图点某天看当天记录 | `showHeatmapDayDetail` | ✅ |  |
| 22 | 概览 | 年度回顾 | `openYearReview` | ✅ |  |
| 23 | 概览 | 年度回顾「和去年比」 | `和去年比` | ✅ |  |
| 24 | 概览 | 备份提醒卡（超 30 天） | `backupHintGo` | ✅ | ⚠ 证据缺失：30 天未备份 |
| 25 | 概览 | 未备份时的引导卡 | `guideOverview` | ✅ |  |
| 26 | 法务 | 首次同意（勾选 + 暂不同意） | `showLegalConsentModal` | ✅ |  |
| 27 | 法务 | 条款版本变更后重新同意 | `LEGAL_VERSION` | ✅ |  |
| 28 | 计划 | 列表 / 日历视图切换 | `togglePlansView` | ✅ |  |
| 29 | 计划 | 日历（有行程的日子标点） | `app-data-plans.js 日历` | ✅ |  |
| 30 | 计划 | 新增计划 | `addPlannedTripBtn` | ✅ |  |
| 31 | 计划 | 计划详情 | `openPlannedDetailModal` | ✅ |  |
| 32 | 计划 | 计划完成 → 转成记录 | `showConfirmCompleteModal` | ✅ | 转记录后撒彩屑 |
| 33 | 计划 | 计划延期 | `延期` | ✅ |  |
| 34 | 计划 | 批量选择 / 批量删除 | `togglePlannedBatchMode` | ✅ |  |
| 35 | 计划 | 过期标记 | `已过期` | ✅ |  |
| 36 | 记录 | 列表 / 卡片视图切换 | `recordsViewToggleBtn` | ✅ |  |
| 37 | 记录 | 按名称/时间排序 | `sortField` | ✅ |  |
| 38 | 记录 | 分页（每页条数可调） | `pagePrevBtn` | ✅ |  |
| 39 | 记录 | 批量选择 / 批量删除 | `toggleBatchMode` | ✅ |  |
| 40 | 记录 | 新增记录 | `addBtn` | ✅ |  |
| 41 | 记录 | 记录详情 | `openRecordDetailModal` | ✅ | 原生四个按钮：编辑/删除/分享/关闭 |
| 42 | 记录 | 编辑记录（含照片） | `app-data-detail.js` | ✅ |  |
| 43 | 记录 | 删除二次确认 | `showDeleteConfirmModal` | ✅ |  |
| 44 | 记录 | 全局搜索（滚动才出现） | `globalSearchBar` | ✅ |  |
| 45 | 记录 | 山册（按山聚合卡片） | `山册` | ✅ |  |
| 46 | 记录 | 山册：连接同一座山 | `openMtnMergeDialog` | ✅ |  |
| 47 | 记录 | 山册：整卡改名 | `showEditTitleModal` | ✅ | ⚠ 证据缺失：合并后用这个名字 |
| 48 | 记录 | 照片灯箱（预览态） | `openPhotoLightbox` | ✅ | 预览只有保存到相册 |
| 49 | 记录 | 灯箱删除照片（编辑态） | `lb-delete` | ✅ |  |
| 50 | 记录 | 灯箱左右滑动 / 缩放 | `lbStep` | ✅ |  |
| 51 | 设置 | 导入数据 | `importBtn` | ✅ | 支持 zip / json / html |
| 52 | 设置 | 导出纯数据备份 | `showExportModal` | ✅ |  |
| 53 | 设置 | 导出完整备份（含照片+回忆册） | `buildFullBackupZip` | ✅ |  |
| 54 | 设置 | 导出诊断报告 | `exportDiagnostics` | ✅ |  |
| 55 | 设置 | 本地自动备份（留 7 份） | `showManageBackupsModal` | ✅ |  |
| 56 | 设置 | 本地备份恢复 / 删除 | `restoreFromFile` | ✅ |  |
| 57 | 设置 | 云端备份列表（只留 2 份） | `showManageBackupsModal 云端` | ✅ |  |
| 58 | 设置 | 网盘绑定 / 解绑 / 测试连接 | `openSyncBindModal` | ✅ |  |
| 59 | 设置 | 数据管理说明 | `showDataInfoModal` | ✅ | 原生单按钮 |
| 60 | 设置 | 抹掉所有足迹（两步确认） | `wipeAll` | ✅ |  |
| 61 | 设置 | 照片占用（缓存清理） | `openPhotoUsageDetailModal` | ✅ |  |
| 62 | 设置 | 每页条数 | `pageSize` | ✅ |  |
| 63 | 设置 | 标题改名 | `showEditTitleModal` | ✅ |  |
| 64 | 设置 | 外观模式（跟随系统/浅/深） | `跟随系统外观模式` | ✅ |  |
| 65 | 设置 | 显示帧率 | `显示帧率` | ✅ |  |
| 66 | 设置 | 震动反馈 | `震动反馈` | ✅ |  |
| 67 | 设置 | 检测更新（只认原生包） | `checkUpdate` | ✅ |  |
| 68 | 设置 | 通知权限入口 | `通知权限未开启` | ✅ |  |
| 69 | 设置 | 计划提醒（当天通知） | `showSystemNotification` | ✅ |  |

## 统计

- 一致 **60** 项
- 有差异 **2** 项
- 未做 **3** 项
- 故意不做 **4** 项

---

## 原生独有能力（Web 版没有，2026-11 补上的一批）

> 这张表是**反向**的：上面那张记的是「Web 有、原生对齐没有的」，
> 这一节记的是原生领先的地方。Web 版是浏览器应用，有些事它做不到。

| 能力 | 为什么 Web 做不到 | 原生实现 |
|---|---|---|
| **计划到点系统通知** | 浏览器关掉就没有后台 | `AlarmManager.setAndAllowWhileIdle` + `BootReceiver` 开机重排 |
| **应用内自动更新** | PWA 只能靠 Service Worker 换资源，装不了包 | 启动时查 `version.json` → 镜像下载（**断点续传**）→ 验签 → 拉起安装器 |
| **安装包签名自校验** | 没有"安装包"这个概念 | 启动时比对自身证书 SHA-256，下载的更新包再验一次 |
| **山名联想** | 可以做，但 Web 没做 | 输入框下方列出历史山名（按频次），点一下填入 |
| **同一座山多次对比** | 可以做，但 Web 没做 | 记录详情显示「第 N 次来 · 上次 X · 这次快/慢 Y」 |
| **照片 EXIF 自动填日期** | File API 读不到 EXIF | `ExifInterface` 读 `DATETIME_ORIGINAL` |
| **年度回顾存成图片** | 只能截屏 | 独立 Canvas 绘制 1080×1620 竖图，存相册 |
| **回忆册照片内嵌** | — | 导出时照片压成 base64 写进 HTML，单文件可发给别人 |
| **云同步冲突保护** | — | 上传前比对云端最新修改时间，冲突则拒绝并提示，不静默覆盖 |
| **崩溃后启动提示** | 浏览器崩了刷新即可 | `CrashGuard` 记录 + 下次启动提示一次（内容 hash 去重） |
| **液态玻璃真折射** | CSS `backdrop-filter` 只能模糊 | AGSL `RuntimeShader`，低端机自动降级为静态玻璃 |

