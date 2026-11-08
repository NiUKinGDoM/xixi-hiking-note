#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
门禁套件一致性（suiteguard 的原生版）

Web 版的教训：门禁有两份实现（checkall.sh / checkall.js），新加的检查只进了一份，
发版时根本不跑 —— 名义有门禁等于没有。

原生版只有 tools/gate.sh 一个入口，但还有另一类漏：**新增了测试类却没人跑**。
这里做三件事：
  ① 测试目录里的每个 *Test.kt 必须真的会被 gradle 跑到（文件名以 Test.kt 结尾）
  ② gate.sh 必须存在，且必须调用测试任务与版本检查
  ③ gate.sh 里引用的脚本文件都必须存在

用法：python tools/suiteguard.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEST_DIR = os.path.join(ROOT, "app", "src", "test", "java", "com", "xixi", "hiking", "nativeapp")
GATE = os.path.join(ROOT, "tools", "gate.sh")


def read(p):
    return io.open(p, encoding="utf-8-sig").read()


def main():
    problems = []

    # ① 测试类命名
    tests = []
    for f in sorted(os.listdir(TEST_DIR)):
        if not f.endswith(".kt"):
            continue
        if not f.endswith("Test.kt"):
            problems.append("测试文件命名不合规（不会被 gradle 识别）：%s" % f)
            continue
        body = read(os.path.join(TEST_DIR, f))
        if "@Test" not in body:
            problems.append("%s 里没有任何 @Test" % f)
        tests.append(f)

    # ② gate.sh 必须跑测试 + 版本检查
    if not os.path.exists(GATE):
        problems.append("tools/gate.sh 不存在")
        gate = ""
    else:
        gate = read(GATE)
        if "testDebugUnitTest" not in gate:
            problems.append("gate.sh 没有跑 testDebugUnitTest")
        if "check_version.py" not in gate and "docfresh.py" not in gate:
            problems.append("gate.sh 没有跑版本/文档一致性检查")

    # ③ gate.sh 引用的脚本都要存在
    for name in re.findall(r"(?:python|bash)\s+(tools/[\w\-.]+)", gate):
        if not os.path.exists(os.path.join(ROOT, name)):
            problems.append("gate.sh 引用了不存在的脚本：%s" % name)

    if problems:
        print("✗ 门禁套件不一致：")
        for p in problems:
            print("   · " + p)
        return 1

    print("✓ 门禁套件一致：%d 个测试类，全部会被执行" % len(tests))
    for t in tests:
        print("   · " + t)
    return 0


if __name__ == "__main__":
    sys.exit(main())
