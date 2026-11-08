#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
版本号一致性检查（原生版「六项优化」④ 文档同步 / ⑤ 单一门禁的一部分）

检查三处必须一致：
  ① app/build.gradle.kts 的 `val vc`        —— 唯一真值
  ② 由 vc 按公式算出的 versionName           —— 1.x.x.x
  ③ README 的版本号规则段 + 原生版更新日志首条 —— 必须已经跟上

为什么要自动化：之前 README 里写着旧版本号、更新日志停在上一版，
每次都要靠人记。Web 版就是被这个坑过，才做了 docfresh（文档真值比对）。
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(ROOT, "app", "build.gradle.kts")
README = os.path.join(ROOT, "README.md")
CHANGELOG = os.path.join(
    ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp",
    "ui", "settings", "FullChangelog.kt"
)


def version_name(vc: int) -> str:
    """1.x.x.x：索引 = vc-1；第四段 = 索引%11；第三段 = (索引//11)%11；第二段 = (索引//121)%11"""
    i = vc - 1
    return "1.%d.%d.%d" % ((i // 121) % 11, (i // 11) % 11, i % 11)


def main() -> int:
    gradle = io.open(GRADLE, encoding="utf-8").read()
    m = re.search(r"val\s+vc\s*=\s*(\d+)", gradle)
    if not m:
        print("✗ build.gradle.kts 里找不到 val vc")
        return 1
    vc = int(m.group(1))
    name = version_name(vc)

    problems = []

    readme = io.open(README, encoding="utf-8").read()
    if name not in readme:
        problems.append("README 里没有当前版本号 %s（vc%d）" % (name, vc))
    # 对照表里必须有一行当前 vc（形如 "| 55 | 54 | **1.0.4.10** |"）
    if not re.search(r"\|\s*%d\s*\|" % vc, readme):
        problems.append("README 的版本对照表里没有 vc%d 这一行" % vc)

    cl = io.open(CHANGELOG, encoding="utf-8").read()
    first = re.search(r'"v([\d.]+)"\s*to', cl)
    if not first:
        problems.append("原生版更新日志里没有条目")
    elif first.group(1) != name:
        problems.append(
            "原生版更新日志首条是 v%s，但当前版本是 %s —— 发版前必须补一条"
            % (first.group(1), name)
        )

    if problems:
        print("✗ 版本号 / 文档不一致（vc%d → %s）：" % (vc, name))
        for p in problems:
            print("   · " + p)
        return 1

    print("✓ 版本号一致：vc%d → %s（README + 原生版更新日志都跟上了）" % (vc, name))
    return 0


if __name__ == "__main__":
    sys.exit(main())
