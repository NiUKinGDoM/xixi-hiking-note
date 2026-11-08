#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Web ⇄ 原生 功能对齐核对（2026-11-05，临时脚本）

为什么是"人工判定表 + 脚本校验"
────────────────────────────────
纯字符串匹配跑不通：web 的弹窗全是 JS 拼的，源码里 8000 多条中文片段绝大多数是注释，
自动匹配只会在噪音里淹死（我试过，486 条候选里一半以上是通用词）。

所以做法是：
  · 我从 web 的 47 个功能入口（open*/show*/toggle*）+ 设置项 + 列表操作里**人工整理**
    出一份功能清单，每条写清「web 在哪 / 原生查什么关键字 / 我判定的状态」；
  · 脚本负责**验证我的判定是否还成立**：去原生源码里找那条关键字，
    找不到就说明状态变了（要么我又漏了，要么后来补上了），直接报出来。

这样这份表既是给用户看的结论，也是防止文档过期的哨兵。

用法
──────
    python tools/webfeature.py            # 打印全表 + 统计
    python tools/webfeature.py --md docs/WEB-PARITY.md   # 生成 markdown 报告
    python tools/webfeature.py --gate     # 有"声称已做但找不到证据"的条目就非 0

状态取值
────────
  same  web 有的原生也有，行为一致
  diff  两边都有，但交互/呈现不同（写清差在哪）
  miss  原生没有
  n/a   故意不做（比如依赖浏览器能力）
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NAT = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp")
WEB = r"C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/hiking-app3/www"

# (分组, 功能, web 侧证据, 原生关键字, 状态, 说明)
# 原生关键字写成列表：全部命中才算"有"；空列表表示"没做/不该有"
FEATURES = [
    # ── 概览 ──────────────────────────────────────────────
    ("概览", "四张统计卡（里程/海拔/座数/时长）", "overview 统计区", ["总里程", "累计"], "same", ""),
    ("概览", "里程碑卡（17 档进度）", "showMilestoneSummary", ["里程碑"], "same", ""),
    ("概览", "里程碑一览弹窗", "showMilestoneList", ["MilestoneListDialog"], "same", ""),
    ("概览", "里程碑达成庆祝（彩屑 + 提示）", "showMilestoneCelebration", [], "miss",
     "原生只在「计划完成转记录」撒彩屑；里程碑达成时没有任何反馈"),
    ("概览", "热力图（按月/日，色阶）", "app-data-heatmap.js", ["热力图"], "same", ""),
    ("概览", "热力图年月选择器", "openHmYmPicker", ["PickerRequest"], "same", "选年不关、选月才关"),
    ("概览", "热力图点某天看当天记录", "showHeatmapDayDetail", ["DayRequest", "DayRecordRow"], "same", ""),
    ("概览", "年度回顾", "openYearReview", ["YearReviewDialog"], "same", ""),
    ("概览", "年度回顾「和去年比」", "和去年比", ["和去年比"], "same", ""),
    ("概览", "备份提醒卡（超 30 天）", "backupHintGo", ["30 天未备份"], "same", ""),
    ("概览", "未备份时的引导卡", "guideOverview", ["KEY_GUIDE_OVERVIEW"], "same", ""),

    # ── 记录 ──────────────────────────────────────────────
    ("记录", "列表 / 卡片视图切换", "recordsViewToggleBtn", ["KEY_RECORDS_VIEW"], "same", ""),
    ("记录", "按名称/时间排序", "sortField", ["SortHeader"], "same", ""),
    ("记录", "分页（每页条数可调）", "pagePrevBtn", ["KEY_PAGE_SIZE"], "same", ""),
    ("记录", "批量选择 / 批量删除", "toggleBatchMode", ["batchMode"], "same", ""),
    ("记录", "新增记录", "addBtn", ["RecordEditDialog"], "same", ""),
    ("记录", "记录详情", "openRecordDetailModal", ["RecordDetailDialog"], "same", "原生四个按钮：编辑/删除/分享/关闭"),
    ("记录", "编辑记录（含照片）", "app-data-detail.js", ["RecordEditDialog"], "same", ""),
    ("记录", "删除二次确认", "showDeleteConfirmModal", ["删除记录"], "same", ""),
    ("记录", "全局搜索（滚动才出现）", "globalSearchBar", ["GlobalSearchBar"], "same", ""),
    ("记录", "首条记录庆祝提示", "showFirstRecordTip", [], "miss", "web 记下第一条会弹「第一笔已记下」并撒彩屑，原生没有"),
    ("记录", "山册（按山聚合卡片）", "山册", ["MountainBookView"], "same", ""),
    ("记录", "山册：连接同一座山", "openMtnMergeDialog", ["MtnMerge"], "same", ""),
    ("记录", "山册：整卡改名", "showEditTitleModal", ["合并后用这个名字"], "same", ""),
    ("记录", "照片灯箱（预览态）", "openPhotoLightbox", ["PhotoLightbox"], "same", "预览只有保存到相册"),
    ("记录", "灯箱删除照片（编辑态）", "lb-delete", ["onDelete"], "same", ""),
    ("记录", "灯箱左右滑动 / 缩放", "lbStep", ["detectTransformGestures"], "same", ""),

    # ── 计划 ──────────────────────────────────────────────
    ("计划", "列表 / 日历视图切换", "togglePlansView", ["KEY_PLANS_VIEW"], "same", ""),
    ("计划", "日历（有行程的日子标点）", "app-data-plans.js 日历", ["PlanCalendarView"], "same", ""),
    ("计划", "新增计划", "addPlannedTripBtn", ["PlanDetailDialog"], "same", ""),
    ("计划", "计划详情", "openPlannedDetailModal", ["PlanDetailDialog"], "same", ""),
    ("计划", "计划完成 → 转成记录", "showConfirmCompleteModal", ["doneRecordId"], "same", "转记录后撒彩屑"),
    ("计划", "计划延期", "延期", ["延期"], "same", ""),
    ("计划", "批量选择 / 批量删除", "togglePlannedBatchMode", ["batchMode"], "same", ""),
    ("计划", "过期标记", "已过期", ["已过期"], "same", ""),

    # ── 设置 · 数据 ───────────────────────────────────────
    ("设置", "导入数据", "importBtn", ["showImport"], "same", "支持 zip / json / html"),
    ("设置", "导出纯数据备份", "showExportModal", ["DATA_ONLY"], "same", ""),
    ("设置", "导出完整备份（含照片+回忆册）", "buildFullBackupZip", ["FULL_ZIP", "回忆册"], "same", ""),
    ("设置", "导出诊断报告", "exportDiagnostics", ["DIAGNOSTICS"], "same", ""),
    ("设置", "本地自动备份（留 7 份）", "showManageBackupsModal", ["LocalBackup"], "same", ""),
    ("设置", "本地备份恢复 / 删除", "restoreFromFile", ["LocalBackupsDialog"], "same", ""),
    ("设置", "云端备份列表（只留 2 份）", "showManageBackupsModal 云端", ["CloudBackupsDialog", "pruneOldBackups"], "same", ""),
    ("设置", "网盘绑定 / 解绑 / 测试连接", "openSyncBindModal", ["SyncConfigDialog"], "same", ""),
    ("设置", "同步状态（上次同步时间/连接结果）", "showSyncStatusModal", [], "miss",
     "web 有独立的「同步状态」弹窗，显示上次同步时间与连接结果；原生只有操作后的一条 toast"),
    ("设置", "同步教程演示", "showSyncDemoModal", [], "n/a",
     "web 给第一次绑定的人看图文步骤；原生用「去导入/云端」引导卡代替，不做图文教程"),
    ("设置", "数据管理说明", "showDataInfoModal", ["数据管理说明"], "same", "原生单按钮"),
    ("设置", "抹掉所有足迹（两步确认）", "wipeAll", ["抹掉所有足迹"], "same", ""),
    ("设置", "照片占用（缓存清理）", "openPhotoUsageDetailModal", ["PhotoUsageDialog"], "same", ""),
    ("设置", "每页条数", "pageSize", ["KEY_PAGE_SIZE"], "same", ""),
    ("设置", "标题改名", "showEditTitleModal", ["标题"], "same", ""),

    # ── 设置 · 通用 ───────────────────────────────────────
    ("设置", "外观模式（跟随系统/浅/深）", "跟随系统外观模式", ["KEY_THEME"], "same", ""),
    ("设置", "显示帧率", "显示帧率", ["showFps"], "same", ""),
    ("设置", "震动反馈", "震动反馈", ["KEY_HAPTIC"], "same", ""),
    ("设置", "检测更新（只认原生包）", "checkUpdate", ["Updater.check"], "same", ""),
    ("设置", "通知权限入口", "通知权限未开启", ["POST_NOTIFICATIONS"], "same", ""),
    ("设置", "计划提醒（当天通知）", "showSystemNotification", ["Notifier"], "same", ""),

    # ── 关于 / 法务 ───────────────────────────────────────
    ("关于", "隐私政策", "showPrivacyPolicyModal", ["隐私政策"], "same", "11 条，与 web 同文"),
    ("关于", "免责声明", "showDisclaimerModal", ["免责声明"], "same", "11 条"),
    ("关于", "更新日志（全版本可展开）", "showChangelogModal", ["NativeChangelog"], "same", ""),
    ("关于", "支持作者（收款码）", "showSupportModal", ["support_qr_wechat"], "same", ""),
    ("关于", "GitHub / 官网入口", "GitHub", [], "n/a", "2026-11-04 用户要求删除"),
    ("法务", "首次同意（勾选 + 暂不同意）", "showLegalConsentModal", ["LegalConsent"], "same", ""),
    ("法务", "条款版本变更后重新同意", "LEGAL_VERSION", ["LEGAL_VERSION"], "same", ""),

    # ── 分享 / 其他 ───────────────────────────────────────
    ("分享", "分享卡（1080×1440）", "generateShareCard", ["ShareCardDialog"], "same", "保存到相册"),
    ("分享", "回忆册 HTML（随完整备份）", "buildMemoirHtml", ["MemoirHtml"], "same", ""),
    ("其他", "液态玻璃 / 模糊", "backdrop-filter", ["LiquidGlass"], "diff", "web 用 CSS；原生用 AGSL/RenderEffect，低版本降级"),
    ("其他", "安装包完整性校验", "ResGuard", ["Integrity"], "diff", "web 校验资源哈希；原生校验签名指纹（更硬）"),
    ("其他", "PWA 安装引导", "beforeinstallprompt", [], "n/a", "原生不需要"),
    ("其他", "Service Worker 离线缓存", "sw.js", [], "n/a", "原生本身就是离线应用"),
]


def native_corpus():
    buf = []
    for dp, _dn, fns in os.walk(NAT):
        for fn in fns:
            if fn.endswith(".kt"):
                buf.append(io.open(os.path.join(dp, fn), encoding="utf-8", errors="ignore").read())
    return "\n".join(buf)


def check(corpus, keys):
    """所有关键字都出现才算命中；返回 (命中数, 缺失关键字)"""
    if not keys:
        return 0, []
    miss = [k for k in keys if k not in corpus]
    return len(keys) - len(miss), miss


def main():
    args = sys.argv[1:]
    corpus = native_corpus()
    rows = []
    for group, name, web, keys, status, note in FEATURES:
        hit, missing = check(corpus, keys)
        expect_have = status in ("same", "diff")
        actual_have = bool(keys) and not missing
        # 哨兵：声称"有"但代码里找不到 → 说明我记错了或代码被删了
        alarm = ""
        if expect_have and keys and missing:
            alarm = "证据缺失：" + ", ".join(missing)
        if status == "miss" and keys and not missing:
            alarm = "已补上（文档过期）"
        rows.append((group, name, web, status, note, hit, len(keys), alarm))

    order = {"miss": 0, "diff": 1, "n/a": 2, "same": 3}
    rows.sort(key=lambda r: (order[r[3]], r[0]))

    counts = {}
    for r in rows:
        counts[r[3]] = counts.get(r[3], 0) + 1

    print("══ Web ⇄ 原生 功能对齐 ══")
    print(f"  共 {len(rows)} 项：一致 {counts.get('same', 0)} · 有差异 {counts.get('diff', 0)} "
          f"· 未做 {counts.get('miss', 0)} · 故意不做 {counts.get('n/a', 0)}")
    alarms = [r for r in rows if r[7]]
    if alarms:
        print()
        print(f"  ⚠ {len(alarms)} 条与代码不符（文档需要更新或功能被删）：")
        for r in alarms:
            print(f"     · {r[1]} —— {r[7]}")

    if "--md" in args:
        path = args[args.index("--md") + 1]
        write_md(path, rows)
        print()
        print(f"✓ 已生成 {path}")
    else:
        for r in rows:
            if r[3] in ("miss", "diff"):
                label = "缺" if r[3] == "miss" else "差"
                print(f"  [{label}] {r[0]} · {r[1]}")
                if r[4]:
                    print(f"        {r[4]}")

    if "--gate" in args and alarms:
        return 1
    return 0


def write_md(path, rows):
    icon = {"same": "✅", "diff": "⚠️", "miss": "❌", "n/a": "—"}
    lines = [
        "# Web ⇄ 原生 功能对齐表",
        "",
        "> 自动生成：`python tools/webfeature.py --md docs/WEB-PARITY.md`",
        "> 判定表是人工整理的（web 的弹窗全在 JS 里拼，纯字符串匹配跑不通）；",
        "> 脚本负责校验「原生关键字」是否还在，代码一变就会报「文档过期」。",
        "",
        "| # | 模块 | 功能 | Web 入口 | 状态 | 说明 |",
        "|---|---|---|---|---|---|",
    ]
    for i, (group, name, web, status, note, hit, total, alarm) in enumerate(rows, 1):
        st = icon[status] + {"same": "", "diff": " 有差异", "miss": " 未做", "n/a": " 故意不做"}[status]
        note2 = (note or "").replace("|", "\\|")
        if alarm:
            note2 = (note2 + " ⚠ " + alarm).strip()
        lines.append(f"| {i} | {group} | {name} | `{web}` | {st} | {note2} |")
    lines += ["", "## 统计", ""]
    c = {}
    for r in rows:
        c[r[3]] = c.get(r[3], 0) + 1
    lines.append(f"- 一致 **{c.get('same',0)}** 项")
    lines.append(f"- 有差异 **{c.get('diff',0)}** 项")
    lines.append(f"- 未做 **{c.get('miss',0)}** 项")
    lines.append(f"- 故意不做 **{c.get('n/a',0)}** 项")
    io.open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")


if __name__ == "__main__":
    sys.exit(main())
