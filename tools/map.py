#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
代码地图（map 的原生版）

生成 docs/CODE-MAP.md：每个源文件多少行、里面有哪些 @Composable / object / 关键函数。
作用：
  · 找东西不用 grep 全工程（"弹窗在哪" → 直接看地图）
  · 文件行数暴涨时一眼能发现（比如某个 Screen 长到 1500 行，该拆了）

用法：
    python tools/map.py          # 生成 / 覆盖 docs/CODE-MAP.md
    python tools/map.py --check  # 只检查是否过期（CI/门禁用）
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp")
OUT = os.path.join(ROOT, "docs", "CODE-MAP.md")

FUN = re.compile(r"^(?:@Composable\s*)?(?:private\s+|internal\s+)?fun\s+(?:Modifier\.)?(\w+)", re.M)


def build():
    lines = [
        "# 代码地图（自动生成，勿手改）",
        "",
        "`python tools/map.py` 重新生成；`python tools/map.py --check` 校验是否过期。",
        "",
        "| 文件 | 行数 | 主要声明 |",
        "|---|---|---|",
    ]
    total = 0
    count = 0
    for r, _, fs in sorted(os.walk(SRC)):
        for f in sorted(fs):
            if not f.endswith(".kt"):
                continue
            p = os.path.join(r, f)
            body = io.open(p, encoding="utf-8").read()
            n = body.count("\n") + 1
            total += n
            count += 1
            rel = os.path.relpath(p, SRC).replace("\\", "/")
            names = []
            for m in FUN.finditer(body):
                nm = m.group(1)
                if nm in ("onCreate", "onDestroy", "get", "set"):
                    continue
                names.append(nm)
            # 只留前 8 个，避免表格爆炸
            shown = "、".join("`%s`" % x for x in names[:8])
            if len(names) > 8:
                shown += " …"
            lines.append("| `%s` | %d | %s |" % (rel, n, shown))
    lines += ["", "**合计 %d 个文件、%d 行**" % (count, total)]
    return "\n".join(lines) + "\n"


def main():
    content = build()
    if "--check" in sys.argv:
        if not os.path.exists(OUT):
            print("✗ docs/CODE-MAP.md 不存在（先跑 python tools/map.py）")
            return 1
        old = io.open(OUT, encoding="utf-8").read()
        if old != content:
            print("✗ 代码地图已过期（行数或声明变了）—— 跑 python tools/map.py 重生成")
            return 1
        print("✓ 代码地图是最新的")
        return 0

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    io.open(OUT, "w", encoding="utf-8", newline="\n").write(content)
    print("✓ 已生成 docs/CODE-MAP.md（%d 行）" % content.count("\n"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
