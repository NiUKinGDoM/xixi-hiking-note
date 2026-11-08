#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
未测模块清单（2026-11-05）

问题
──────
106 项测试听着不少，但它们集中在 6 个类里（截图、点击冒烟、压测、导入兼容、备份往返）。
剩下 50 多个源文件**一行断言都没有** —— 出问题时只能靠手点。

这个脚本不追求"覆盖率百分比"（那要插桩，重），只回答一个更实用的问题：
**哪些文件完全没被任何测试引用过，其中哪些最该补测试。**

风险排序（按"坏了会怎样"）：
  高：解析 / 备份 / 数据库 / 迁移 / 文件读写 —— 坏了会丢数据或打不开
  中：纯计算（里程碑、统计、格式化）—— 坏了数字不对
  低：纯 UI 布局 —— 坏了看得见

用法
──────
    python tools/covscan.py            # 按风险列出未测文件
    python tools/covscan.py --gate     # 高风险未测文件 > 阈值就非 0
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp")
TEST = os.path.join(ROOT, "app", "src", "test", "java")

HIGH_PAT = re.compile(r"Backup|Parser|Dao|Dao\b|Database|Migrat|PhotoStore|LocalBackup|SecretBox|WebDav|Memoir|Integrity|Updater", re.I)
MID_PAT = re.compile(r"Milestone|Stat|Format|Heatmap|MtnMerge|Calc", re.I)


def test_corpus():
    buf = []
    for dp, _dn, fns in os.walk(TEST):
        for fn in fns:
            if fn.endswith(".kt"):
                buf.append(io.open(os.path.join(dp, fn), encoding="utf-8", errors="ignore").read())
    return "\n".join(buf)


def main():
    gate = "--gate" in sys.argv[1:]
    corpus = test_corpus()
    rows = []
    for dp, _dn, fns in os.walk(MAIN):
        for fn in sorted(fns):
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(dp, fn)
            rel = os.path.relpath(p, MAIN).replace("\\", "/")
            src = io.open(p, encoding="utf-8", errors="ignore").read()
            # 判定"被测到"：测试里出现这个类名/文件名（去掉 .kt）
            stem = fn[:-3]
            referenced = stem in corpus or (stem + "Kt") in corpus
            loc = src.count("\n") + 1
            if referenced:
                continue
            risk = "高" if HIGH_PAT.search(stem + rel) else ("中" if MID_PAT.search(stem + rel) else "低")
            rows.append((risk, rel, loc))

    order = {"高": 0, "中": 1, "低": 2}
    rows.sort(key=lambda r: (order[r[0]], -r[2]))

    total = sum(1 for _ in rows)
    high = [r for r in rows if r[0] == "高"]
    print("══ 未测模块 ══")
    print(f"  没有任何测试引用的文件：{total} 个（高风险 {len(high)}）")
    for risk in ("高", "中", "低"):
        group = [r for r in rows if r[0] == risk]
        if not group:
            continue
        print()
        print(f"[{risk}] {len(group)} 个")
        for r, rel, loc in group:
            print(f"  {rel:<48} {loc:>5} 行")

    print()
    if high:
        print(f"建议先补这几个：{', '.join(r[1].split('/')[-1] for r in high[:4])}")
    if gate and len(high) > 6:
        print(f"✗ 高风险未测模块 {len(high)} 个（阈值 6）")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
