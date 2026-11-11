#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
单文件行数守卫 —— 防止拆开的文件又被堆回去。

★2026-11-11 为什么要它：
   SettingsScreen.kt 一个函数长到 1260 行，改一个分组要在 1700 行里上下翻。
   这次拆了一半（1746 → 1345），但**没有任何东西拦得住它长回去** ——
   下次"顺手在这里加一段"，半年后又是一个 1700 行。

规则：
    > 1200 行 → ERROR（门禁红，不许发）
    > 800 行  → WARN（能发，但每次都会提醒你该拆了）

豁免名单必须写明**原因和退出条件**，不许只写"暂时"。拆完就把条目删掉。

用法：
    python tools/sizeguard.py            # 只看
    python tools/sizeguard.py --gate     # 有 ERROR 就退出 1
"""
import argparse
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java")

ERR_LIMIT = 1500
WARN_LIMIT = 800

# {文件名: 豁免理由}  —— 理由里必须写"什么时候删"
#
# ★为什么阈值定 1500 而不是 800：
#   一刀切 800 会把 MainActivity(1453) / LiquidGlass(1400) 也算 ERROR，
#   那不是"防止膨胀"，是"逼着立刻重构"——门禁一红就发不了版，
#   最后只能整个关掉，守卫等于没有。
#   这里的目标只有一个：**不许再出现 1700 行的单文件**。
#   WARN 是提醒（现在有 6 个文件在 800~1100，属于"该找时间拆"但不紧急）。
EXEMPT = {}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gate", action="store_true")
    a = ap.parse_args()

    rows = []
    for dirpath, _dn, fns in os.walk(SRC):
        for fn in fns:
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(dirpath, fn)
            try:
                n = sum(1 for _ in open(p, encoding="utf-8", errors="ignore"))
            except Exception:
                continue
            rows.append((n, fn, os.path.relpath(p, ROOT)))
    rows.sort(reverse=True)

    errs, warns = [], []
    for n, fn, rel in rows:
        if fn in EXEMPT:
            continue
        if n > ERR_LIMIT:
            errs.append((n, rel))
        elif n > WARN_LIMIT:
            warns.append((n, rel))

    print("══ 单文件行数（ERROR > %d / WARN > %d）══" % (ERR_LIMIT, WARN_LIMIT))
    total = len(rows)
    over = len(errs) + len(warns)
    print("共 %d 个 .kt，超标 %d 个" % (total, over))
    for n, rel in errs:
        print("  ERROR %5d 行  %s" % (n, rel))
    for n, rel in warns:
        print("  WARN  %5d 行  %s" % (n, rel))
    if EXEMPT:
        print("  （豁免中：%s）" % "；".join("%s —— %s" % (k, v) for k, v in EXEMPT.items()))

    # Top3 永远打出来：知道最大的三个是谁，比看数字有感觉
    print("  最大三个：")
    for n, fn, rel in rows[:3]:
        print("     %5d 行  %s" % (n, fn))

    if not errs:
        print("✓ 没有超限文件")
    if a.gate and errs:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
