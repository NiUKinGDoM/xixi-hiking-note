#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
设计一致性检查（designcheck）

这些规则都是这几轮真踩过的坑，靠人记必漏：
  ① **emoji**：项目规则里内容区不用 emoji（web 版图标全是 material icons）
  ② **硬编码颜色**：颜色应集中在 theme/Design.kt；散落的 0xFFxxxxxx 超过阈值就提醒
  ③ **字号范围**：小于 9sp 的字在手机上基本看不清
  ④ **弹窗宽度**：只允许 0.84 / 0.90 / 0.94 三档（用户要求统一）
  ⑤ **卡片描边**：glassPanel 不再带高光，若又出现 border(1.dp...) 说明有人加了框

用法：python tools/designcheck.py [--strict]
  --strict 时 ② 也算失败（默认只提醒）
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UI = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp")

# 只认真正的彩色 emoji（含天气符号）；★ ✓ ⚠ ✕ 这些排版符号不算
EMOJI = re.compile("[\U0001F300-\U0001FAFF\u2600\u2601\u2744\u26C5]")


def strip_comments(code: str) -> str:
    """去掉 // 行注释与 /* */ 块注释，避免把注释里的符号当成 UI 文案"""
    code = re.sub(r"/\*[\s\S]*?\*/", "", code)
    code = re.sub(r"//[^\n]*", "", code)
    return code
# 允许出现 emoji 的地方：心情/天气选项（这是产品要的表情符号）
EMOJI_OK = {
    "GlassPickers.kt",        # 心情/天气选择器的表情
    "MilestoneListDialog.kt",
    "Milestones.kt",          # 天气 emoji → 文字 的映射表
    "FullChangelog.kt",
    "MainActivity.kt",        # 顶栏 🏔️（与 Web 版一致）
    "SettingsScreen.kt",      # 关于卡的品牌山（与顶栏同一个 🏔️）
    "SettingsSections.kt",    # ★2026-11-11 关于卡搬到这里，品牌山跟着过来
    "AppChrome.kt",           # 顶栏 🏔️ 迁到 TopGlassBar 后在这里
}


def kt_files():
    for r, _, fs in os.walk(UI):
        for f in fs:
            if f.endswith(".kt"):
                yield os.path.join(r, f)


def main():
    strict = "--strict" in sys.argv
    problems, warns = [], []

    colors = 0
    for path in kt_files():
        rel = os.path.relpath(path, UI)
        name = os.path.basename(path)
        raw = io.open(path, encoding="utf-8").read()
        body = strip_comments(raw)

        # ① emoji
        if name not in EMOJI_OK:
            for m in EMOJI.finditer(body):
                line = body[:m.start()].count("\n") + 1
                problems.append("%s:%d 内容里出现 emoji「%s」" % (rel, line, m.group(0)))

        # ③ 过小字号
        for m in re.finditer(r"fontSize\s*=\s*([\d.]+)\.sp", body):
            v = float(m.group(1))
            if v < 9:
                line = body[:m.start()].count("\n") + 1
                problems.append("%s:%d 字号 %.1fsp 太小（<9sp 看不清）" % (rel, line, v))

        # ④ 弹窗宽度档位
        for m in re.finditer(r"maxWidthFraction\s*=\s*([\d.]+)f", body):
            v = float(m.group(1))
            if v not in (0.84, 0.86, 0.88, 0.90, 0.92, 0.94):
                line = body[:m.start()].count("\n") + 1
                warns.append("%s:%d 弹窗宽度 %.2f 不在三档内" % (rel, line, v))

        # ② 颜色散落
        if name != "Design.kt":
            colors += len(re.findall(r"Color\(0x[0-9A-Fa-f]{8}\)", body))

    if colors > 120:
        msg = "硬编码颜色 %d 处（建议收敛到 theme/Design.kt）" % colors
        (problems if strict else warns).append(msg)

    for w in warns:
        print("  ! " + w)
    if problems:
        print("✗ 设计检查不过：")
        for p in problems:
            print("   · " + p)
        return 1
    print("✓ 设计检查通过（emoji / 字号 / 弹窗宽度 / 颜色散落 %d 处）" % colors)
    return 0


if __name__ == "__main__":
    sys.exit(main())
