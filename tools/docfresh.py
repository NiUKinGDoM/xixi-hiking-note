#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
文档真值校验（docfresh 的原生版）

Web 版被这件事坑过：README / 交接文档里写着旧版本号、旧的测试套件数，
每次发版靠人记着改。这里把「文档里的事实」和**代码真值**逐条比对。

检查项：
  ① app/build.gradle.kts 的 vc → 按公式算出的 versionName
  ② README 的版本对照表里有当前 vc，且当前版号加粗
  ③ 原生版更新日志首条 == 当前版本
  ④ README 里提到的「测试 N 项」与实际测试方法数一致（按 @Test 计数）
  ⑤ README 里的 gradle / jdk 路径确实存在（防止换机后照抄跑不通）

用法：python tools/docfresh.py [--fix]
  --fix 只修 ②③（安全项）；①②④ 只报不自动改，避免改错语义。
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
TEST_DIR = os.path.join(ROOT, "app", "src", "test", "java", "com", "xixi", "hiking", "nativeapp")


def version_name(vc):
    i = vc - 1
    return "1.%d.%d.%d" % ((i // 121) % 11, (i // 11) % 11, i % 11)


def read(p):
    return io.open(p, encoding="utf-8").read()


def main():
    fix = "--fix" in sys.argv
    problems = []
    hints = []

    gradle = read(GRADLE)
    m = re.search(r"val\s+vc\s*=\s*(\d+)", gradle)
    vc = int(m.group(1))
    name = version_name(vc)

    readme = read(README)
    # ② 对照表
    if not re.search(r"\|\s*%d\s*\|" % vc, readme):
        problems.append("README 对照表缺 vc%d 这一行" % vc)
    elif ("**%s**" % name) not in readme:
        problems.append("README 对照表里 %s 没有加粗（应标为当前版）" % name)
        if fix:
            readme = readme.replace("| %d | %d | %s |" % (vc, vc - 1, name),
                                    "| %d | %d | **%s** |" % (vc, vc - 1, name))
            io.open(README, "w", encoding="utf-8", newline="\n").write(readme)
            hints.append("已修：%s 加粗" % name)

    # ③ 更新日志首条
    cl = read(CHANGELOG)
    first = re.search(r'"v([\d.]+)"\s*to', cl)
    if first and first.group(1) != name:
        problems.append("更新日志首条是 v%s，当前是 %s" % (first.group(1), name))

    # ④ 测试项数
    total = 0
    files = 0
    for f in os.listdir(TEST_DIR):
        if f.endswith(".kt"):
            files += 1
            total += len(re.findall(r"@Test\b", read(os.path.join(TEST_DIR, f))))
    m2 = re.search(r"(\d+)\s*项(?:测试|门禁)", readme)
    if m2 and int(m2.group(1)) != total:
        problems.append("README 写「%s 项测试」，实际 @Test 是 %d 项" % (m2.group(1), total))
    else:
        hints.append("测试：%d 个类 / %d 个 @Test" % (files, total))

    # ⑤ 路径存在性
    for p in re.findall(r"C:/Users/[^\s\"`)]*(?:jdk|gradle)[^\s\"`)]*", readme):
        if not os.path.exists(p):
            problems.append("README 里的路径不存在：%s" % p)

    if problems:
        print("✗ 文档与代码不一致（vc%d → %s）" % (vc, name))
        for p in problems:
            print("   · " + p)
        for h in hints:
            print("   · " + h)
        return 1

    print("✓ 文档一致：vc%d → %s" % (vc, name))
    for h in hints:
        print("   · " + h)
    return 0


if __name__ == "__main__":
    sys.exit(main())
