#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
弹窗嵌套扫描（2026-11-05）

为什么要有它
────────────
用户报「照片和灯箱部分的删除键，按了没有弹窗」。查出来是**弹窗套弹窗**：
内层弹窗画在外层卡片的 content 里，于是
  ① 被外层卡片的 `clip(圆角)` + `heightIn(max=72%屏高)` 裁掉/挤小；
  ② 外层一旦不是栈顶就整棵 `return`（只画栈顶），内层作为子节点跟着不画。
结果：状态置了、内层也组合了，屏幕上什么都没有。

修法是在 GlassOverlay 里加 `LocalInOverlay` 标记：内层自动换独立窗口（Popup）渲染、
且不参与弹窗栈。这个脚本负责盯住两件事：

  ① **嵌套深度**：超过 2 层（弹窗 → 确认框 → 再一层）说明交互设计有问题，
     用户会迷失在"关了还有一层"里。这里只提示，不算失败。
  ② **绕过 GlassOverlay 自绘弹窗**：`ui/` 里除 GlassOverlay.kt 自己，
     不允许直接 `Dialog(` / `Popup(` 承载业务弹窗 —— 那样拿不到统一的膜、边框、
     入场动画、返回键处理，也不会被这套嵌套逻辑覆盖（等于埋同一个坑）。

用法
────
    python tools/overlayscan.py
    python tools/overlayscan.py --gate
    python tools/overlayscan.py --selftest
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UI = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp", "ui")

OVERLAY_RX = re.compile(r"\b(GlassOverlay|GlassConfirm)\s*\(")
RAW_RX = re.compile(r"(?<![\w.])(Dialog|Popup)\s*\(")
ACK = re.compile(r"overlayscan:ok\s*\S+")


def scan_text(text):
    """在源码文本里找 Overlay 调用与各自的 lambda 范围。返回 [(name, start, end, line)]"""
    calls = []
    for m in OVERLAY_RX.finditer(text):
        name = m.group(1)
        # 括号平衡，找到参数列表结束
        i = m.end() - 1
        depth = 0
        in_str = False
        esc = False
        j = i
        while j < len(text):
            ch = text[j]
            if in_str:
                if esc:
                    esc = False
                elif ch == "\\":
                    esc = True
                elif ch == '"':
                    in_str = False
            else:
                if ch == '"':
                    in_str = True
                elif ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        break
            j += 1
        # 参数后面可能跟 `{ ... }` 作为 content
        k = j + 1
        while k < len(text) and text[k] in " \t\n\r":
            k += 1
        end = j
        if k < len(text) and text[k] == "{":
            depth = 0
            in_str = False
            esc = False
            while k < len(text):
                ch = text[k]
                if in_str:
                    if esc:
                        esc = False
                    elif ch == "\\":
                        esc = True
                    elif ch == '"':
                        in_str = False
                else:
                    if ch == '"':
                        in_str = True
                    elif ch == "{":
                        depth += 1
                    elif ch == "}":
                        depth -= 1
                        if depth == 0:
                            end = k
                            break
                k += 1
        calls.append((name, m.start(), end, text.count("\n", 0, m.start()) + 1))
    return calls


def depth_of(call, calls):
    """这个调用套在几层里（0 = 顶层）"""
    _n, s, _e, _l = call
    return sum(1 for c in calls if c[1] < s <= c[2])


def scan():
    nested = []
    raw = []
    for dirpath, _dn, fns in os.walk(UI):
        for fn in sorted(fns):
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(dirpath, fn)
            rel = os.path.relpath(p, ROOT).replace("\\", "/")
            text = io.open(p, encoding="utf-8", errors="ignore").read()
            calls = scan_text(text)

            for c in calls:
                d = depth_of(c, calls)
                if d > 0:
                    nested.append((rel, c[3], c[0], d))

            # 绕过 GlassOverlay 的自绘弹窗（GlassOverlay.kt 自己内部用 Popup 是正常的）
            if not rel.endswith("ui/GlassOverlay.kt"):
                lines = text.splitlines()
                for m in RAW_RX.finditer(text):
                    line_no = text.count("\n", 0, m.start()) + 1
                    line = lines[line_no - 1]
                    # 签收可以写在行尾，也可以写在上一行（长注释更自然）
                    # 签收可以写在行尾，也可以写在上面几行（注释常是多行的）
                    if ACK.search(line) or any(
                        ACK.search(lines[k]) for k in range(max(0, line_no - 4), line_no - 1)
                    ):
                        continue
                    raw.append((rel, line_no, m.group(1), line.strip()[:70]))
    return nested, raw


def selftest() -> int:
    print("══ 规则自检 ══")
    ok = True

    code = """
    fun Demo() {
        GlassOverlay(hazeState = h) {
            Text("外层")
            GlassOverlay(hazeState = h) {
                Text("内层")
            }
        }
    }
    """
    calls = scan_text(code)
    inner = [c for c in calls if c[3] > calls[0][3] or depth_of(c, calls) > 0]
    got = any(depth_of(c, calls) == 1 for c in calls)
    print(f"  {'✓' if got else '✗'} 能识别内层嵌套")
    ok = ok and got

    code2 = """
    fun Demo() {
        GlassOverlay(hazeState = h) { Text("单独") }
    }
    """
    calls2 = scan_text(code2)
    got2 = len(calls2) == 1 and depth_of(calls2[0], calls2) == 0
    print(f"  {'✓' if got2 else '✗'} 单层不误报")
    ok = ok and got2

    code3 = 'fun A() { Dialog(onDismissRequest = {}) { Text("x") } }'
    got3 = bool(RAW_RX.search(code3))
    print(f"  {'✓' if got3 else '✗'} 能识别直接 Dialog")
    ok = ok and got3

    print()
    if not ok:
        print("✗ 规则失效 —— 结果不可信")
        return 1
    print("✓ 规则正常")
    return 0


def main():
    if "--selftest" in sys.argv[1:]:
        return selftest()

    nested, raw = scan()
    print("══ 弹窗嵌套扫描 ══")
    if nested:
        print(f"  嵌套调用 {len(nested)} 处（都会走独立窗口渲染，不再被外层裁掉）：")
        for rel, line, name, d in sorted(nested, key=lambda x: (x[0], x[1])):
            short = rel.split("nativeapp/")[-1]
            print(f"    {short}:{line}  {name}  第 {d} 层")
    else:
        print("  嵌套调用 0 处")

    too_deep = [x for x in nested if x[3] > 2]
    print()
    if too_deep:
        print(f"  ! 有 {len(too_deep)} 处嵌套超过 2 层 —— 用户会陷在「关了还有一层」里，建议改成单层")
    else:
        print("✓ 嵌套层级都在 2 层以内")

    print()
    if raw:
        print(f"✗ {len(raw)} 处直接用 Dialog/Popup 承载弹窗（绕过统一的玻璃/动画/返回键处理）：")
        for rel, line, kind, text in sorted(raw, key=lambda x: (x[0], x[1])):
            short = rel.split("nativeapp/")[-1]
            print(f"    {short}:{line}  {kind}  {text}")
        print("  改法：用 GlassOverlay / GlassConfirm；确有必要，行尾写 `// overlayscan:ok 原因` 签收。")
        return 1 if "--gate" in sys.argv[1:] else 0

    print("✓ 没有绕过 GlassOverlay 的自绘弹窗")
    return 0


if __name__ == "__main__":
    sys.exit(main())
