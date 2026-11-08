#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
删除入口守卫（2026-11-05）

起因
────
用户问：「所有的删除键都加二次确认弹窗了吗，查一下，没加的话加上」。
靠眼睛查不全 —— 删除入口散在 9 个文件、11 个位置。实际查出**两个漏的**：
  · 日历视图的垃圾桶（`onDelete = onDeleteOne` 直接传执行函数，绕过确认）
  · 编辑记录页照片角标的 18dp 小叉（点了就删文件 + 删库记录）
两个都是「同一个删除动作的另一个入口」：列表里有闸，换个视图/位置就没闸。

判定模型（v2，第一版是错的，记一下）
──────────────────────────────────
v1 按「入口所在的函数里有没有 GlassConfirm」判 —— 大面积误报。
因为删除按钮定义在 `RecordRow` / `PlanRow` 这种**子组件**里，
而确认弹窗在**父组件**（持有数据的那个）。这是正常的架构，不是漏洞。

所以 v2 走两层：
  ① 同函数确认：入口所在函数体里有 `GlassConfirm(...)` 且提到 删除/清理/抹掉 → 有闸
  ② 回调链：否则看这个入口调的回调（如 `onDelete`），找到该组件的**调用点**，
     看实参是什么：
        · 含 pending / confirm       → 有闸（点击只置状态，父组件弹确认）
        · 是裸的 `onXxx`             → 转发，继续往上追（最多 3 层）
        · 其它（dao.delete / PhotoStore.delete / scope.launch / onDeleteOne）
                                     → 直接执行 ⇒ 无闸

局限
────
· 只看静态文本，动态拼的回调看不出来
· 同名回调在多个组件里出现时，按「组件名 + 回调名」配对，不做完整类型推导
· 确属误报（删的是缓存不是数据），在入口行或上一行写 `// delguard:ok 原因` 签收

用法
────
    python tools/delguard.py             # 列清单
    python tools/delguard.py --gate      # 有「无确认」的入口就非 0
    python tools/delguard.py --selftest  # 规则自检
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp")

FUN_RX = re.compile(r"^(\s*)(?:@\w+(?:\([^)]*\))?\s*)?(?:private |internal |public |override |suspend )*fun\s+(\w+)\s*\(")
ICON_RX = re.compile(r"Icons\.(?:Filled|Outlined|AutoMirrored\.Filled)\.(Delete\w*|RemoveCircle\w*)")
TEXT_RX = re.compile(r'"((?:去|一键|全部)?(?:删除|清理|抹掉|移除)[^"]{0,12})"')
ACK = re.compile(r"delguard:ok\s*\S+")
CONFIRM = "GlassConfirm("
DANGER = re.compile(r"删除|清理|抹掉|移除")
# 回调名：`onDelete` / `onRemovePhoto` …… 但不能是 onClick 本身
CB_NAME = re.compile(r"\b(on(?!Click\b)[A-Z]\w*)\b")
# 判断某行是不是"点击挂载点"
CLICK_LINE = re.compile(r"safeClickable|onClick\s*=|\.clickable")
# 实参里出现这些 ⇒ 是"真正执行删除"，不是"置状态等确认"
DIRECT = re.compile(r"dao\.delete|\.deleteById|\.deleteByRecord|PhotoStore\.delete|deleteAll|scope\.launch|"
                    r"onDeleteOne|onDeleteMany|onRemovePhoto|it\.delete\(\)|f\.delete\(\)")


def strip_strings(line):
    """去掉行内字符串字面量，避免把文案里的括号/关键字当成代码。"""
    out = []
    i = 0
    in_str = False
    while i < len(line):
        ch = line[i]
        if in_str:
            if ch == "\\":
                i += 2
                continue
            if ch == '"':
                in_str = False
            i += 1
            continue
        if ch == '"':
            in_str = True
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def load_files():
    """返回 {相对路径: [行]}，只收 ui/ 与 MainActivity（删除入口与装配点都在这）。"""
    out = {}
    for dirpath, _dn, fns in os.walk(SRC):
        for fn in sorted(fns):
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(dirpath, fn)
            rel = os.path.relpath(p, ROOT).replace("\\", "/")
            if "/ui/" not in rel and not rel.endswith("MainActivity.kt"):
                continue
            out[rel] = io.open(p, encoding="utf-8", errors="ignore").read().splitlines()
    return out


def func_spans(lines):
    """[(start, end, name)] —— 函数体的花括号范围。

    ★踩过的坑：直接全行数花括号，会把**函数签名里默认参数**的 `= {}`
      当成函数体开头（`onDelete: (T) -> Unit = {}` ⇒ 第 2 行就"平衡"了），
      函数范围被截断成只剩签名那几行 → 后面的判断全落空。
      正确做法：先跳过参数列表（圆括号平衡），再找第一个 `{` 开始计。
    """
    spans = []
    for i, ln in enumerate(lines):
        m = FUN_RX.match(ln)
        if not m:
            continue
        name = m.group(2)

        # ① 跳过参数列表
        open_col = ln.find("(")
        if open_col < 0:
            continue
        j, k = i, open_col
        depth_p = 0
        in_str = False
        esc = False
        body_at = None
        while j < len(lines) and body_at is None:
            line = lines[j]
            while k < len(line):
                ch = line[k]
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
                        depth_p += 1
                    elif ch == ")":
                        depth_p -= 1
                        if depth_p == 0:
                            body_at = (j, k)
                            break
                k += 1
            else:
                j += 1
                k = 0
        if body_at is None:
            continue

        # ② 从参数列表之后数花括号
        bj, bk = body_at
        depth = 0
        started = False
        end_line = len(lines) - 1
        in_str = False
        esc = False
        done = False
        while bj < len(lines) and not done:
            line = lines[bj]
            while bk < len(line):
                ch = line[bk]
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
                        started = True
                    elif ch == "}":
                        depth -= 1
                        if started and depth <= 0:
                            end_line = bj
                            done = True
                            break
                bk += 1
            bj += 1
            bk = 0

        spans.append((i, end_line, name))
    return spans


def owner(spans, line_no):
    best = None
    for s, e, name in spans:
        if s <= line_no <= e:
            best = (s, e, name)          # 取最内层
    return best


def arg_block(lines, open_line, open_col):
    """从 `Fn(` 的左括号位置起，返回括号内的原文（跨行拼起来）。

    ★这里不能先 strip_strings 再按索引切原行 —— 剥字符串会改变长度，
      切出来的片段会整体错位，参数就提不准（第一版就是这么错的：
      `PlanCalendarView(...onDelete = { pendingDeletePlan = it })` 提不到实参，
      于是被判成"无法判定"）。改成逐字符扫描，同时自己跟踪字符串状态。
    """
    depth = 0
    buf = []
    in_str = False
    esc = False
    for j in range(open_line, min(open_line + 60, len(lines))):
        line = lines[j]
        k = open_col if j == open_line else 0
        start = k
        while k < len(line):
            ch = line[k]
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
                        buf.append(line[start:k])
                        return "\n".join(buf)
            k += 1
        buf.append(line[start:])
    return "\n".join(buf)


def find_call_sites(files, comp):
    """找 `comp(` 的所有调用点（排除定义本身），返回 [(rel, argblock)]。"""
    out = []
    rx = re.compile(r"\b" + re.escape(comp) + r"\s*\(")
    for rel, lines in files.items():
        for i, ln in enumerate(lines):
            if FUN_RX.match(ln):
                continue
            m = rx.search(ln)
            if not m:
                continue
            out.append((rel, i, arg_block(lines, i, m.end() - 1)))
    return out


_MEMO = {}


def resolve_callback(files, comp, cb, depth=0, path=None):
    """
    追一条回调：返回 ("confirmed"|"direct"|"unknown", 证据)

    ★两个坑（都是实测踩出来的）：
      ① 转发时要换成「调用点所在的组件」，否则一直在同一个组件里找自己的调用点
      ② 同一个函数有**多个调用点**时（PlanDetailRow 在日历里有 2 处），
         第二处会再次递归到同一个 (组件, 回调) 对 —— 用"已访问"直接判成自引用
         就把正确答案盖掉了。改成：**路径栈**判循环 + **结果表**复用。
    """
    key = (comp, cb)
    if key in _MEMO:
        return _MEMO[key]
    path = path or []
    if depth > 4:
        return "unknown", "转发层数过多"
    if key in path:
        return "unknown", "回调自引用"
    path = path + [key]

    results = []
    for rel, line_idx, args in find_call_sites(files, comp):
        m = re.search(r"\b" + re.escape(cb) + r"\s*=\s*([^\n]*)", args)
        if not m:
            continue
        expr = m.group(1).strip()
        if expr.startswith("{"):
            results.append(("block", rel, line_idx, expr))
        elif re.search(r"pending|confirm", expr, re.I):
            results.append(("confirmed", rel, line_idx, expr))
        elif re.fullmatch(r"on[A-Z]\w*", expr):
            results.append(("forward", rel, line_idx, expr))
        elif DIRECT.search(expr):
            results.append(("direct", rel, line_idx, expr))
        else:
            results.append(("call", rel, line_idx, expr))

    if not results:
        ans = ("unknown", f"找不到 {comp} 的调用点")
        _MEMO[key] = ans
        return ans

    verdicts = []
    for kind, rel, line_idx, expr in results:
        if kind == "block":
            # 闭包里也可能直接删（`onDelete = { dao.deleteById(it) }`）
            verdicts.append("direct" if DIRECT.search(expr) else "confirmed")
        elif kind == "confirmed":
            verdicts.append("confirmed")
        elif kind == "direct":
            verdicts.append("direct")
        elif kind == "forward":
            spans = func_spans(files[rel])
            own = None
            for a, b, nm in spans:
                if a <= line_idx <= b:
                    own = nm
            if own:
                v, _why = resolve_callback(files, own, expr, depth + 1, path)
                verdicts.append(v)
            else:
                verdicts.append("unknown")
        else:
            verdicts.append("unknown")

    if "direct" in verdicts:
        ans = ("direct", "有调用点直接执行删除")
    elif all(v == "confirmed" for v in verdicts):
        ans = ("confirmed", "所有调用点都走确认")
    elif "unknown" in verdicts:
        ans = ("unknown", "有调用点无法判定")
    else:
        ans = ("confirmed", "ok")
    _MEMO[key] = ans
    return ans


def scan():
    files = load_files()
    spans_cache = {rel: func_spans(lines) for rel, lines in files.items()}
    hits = []

    for rel, lines in files.items():
        spans = spans_cache[rel]
        for i, ln in enumerate(lines):
            s = ln.strip()
            if s.startswith("//") or s.startswith("*"):
                continue
            m = ICON_RX.search(ln) or TEXT_RX.search(ln)
            if not m:
                continue
            near = " ".join(lines[max(0, i - 8):i + 9])
            # 两种情况都算入口：
            #   ① 图标/文案 + 附近有 clickable（行内按钮）
            #   ② 按钮组件调用：`SmallPill("删除选中") { ... }` —— clickable 在组件内部，
            #      行内看不到，但这种形式本身就是可点的（批量删除就是这种）
            is_component_call = bool(
                re.search(r"\w*(?:Pill|Button|Chip|Action)\s*\(\s*\"[^\"]*\"", ln)
            )
            if not (is_component_call or re.search(r"safeClickable|onClick\s*=|\.clickable", near)):
                continue
            if ACK.search(ln) or (i >= 1 and ACK.search(lines[i - 1])):
                continue

            own = owner(spans, i)
            if not own:
                continue
            start, end, fname = own
            body = "\n".join(lines[start:end + 1])

            # ①a 点击体里就是"置确认状态"（批量删除按钮常这么写：
            #    `SmallPill("删除选中") { confirmBatchDelete = true }`）
            if re.search(r"\b\w*(?:confirm|pending|ask|armed)\w*\s*=(?!=)", near, re.I):
                hits.append((rel, i + 1, fname, m.group(0).strip('"'), "confirmed",
                             "点击后置确认状态"))
                continue

            # ①b 同函数确认
            if CONFIRM in body and DANGER.search(body):
                hits.append((rel, i + 1, fname, m.group(0).strip('"'), "confirmed",
                             "同函数内有确认弹窗"))
                continue

            # ② 回调链：先找**离入口最近的那个点击挂载行**，再从它身上取回调名。
            #   为什么不是固定窗口：图标在 `Icon(...)` 里，和外层 safeClickable
            #   可能隔十几行（RecordRow 就隔了 4 行，RowIconAction 隔 1 行）。
            click_idx = None
            best = 999
            for j in range(start, end + 1):
                if CLICK_LINE.search(lines[j]):
                    d = abs(j - i)
                    if d < best:
                        best, click_idx = d, j
            verdict, why = "unknown", "没找到回调"
            if click_idx is not None:
                near_click = " ".join(lines[click_idx:min(click_idx + 3, end + 1)])
                for cb in CB_NAME.findall(near_click):
                    verdict, why = resolve_callback(files, fname, cb)
                    if verdict != "unknown":
                        why = f"{cb} → {why}"
                        break

            hits.append((rel, i + 1, fname, m.group(0).strip('"'), verdict, why))
    return hits


# ── 自检 ────────────────────────────────────────────────
def selftest() -> int:
    print("══ 规则自检 ══")
    ok = True

    # ① 同函数确认
    code = [
        "fun Demo() {",
        "    var confirm by remember { mutableStateOf(false) }",
        '    Icon(Icons.Filled.Delete, "删除").safeClickable { confirm = true }',
        '    if (confirm) GlassConfirm(title = "删除这条记录？", message = "不可恢复")',
        "}",
    ]
    spans = func_spans(code)
    own = owner(spans, 2)
    body = "\n".join(code[own[0]:own[1] + 1])
    hit = CONFIRM in body and DANGER.search(body)
    print(f"  {'✓' if hit else '✗'} 同函数确认能识别")
    ok = ok and hit

    # ② 单行实参：pending ⇒ 有确认
    args = "onDelete = { pendingDeleteRecord = it }"
    m = re.search(r"\bonDelete\s*=\s*([^\n]*)", args)
    hit2 = bool(re.search(r"pending|confirm", m.group(1), re.I))
    print(f"  {'✓' if hit2 else '✗'} pending 实参判成有确认")
    ok = ok and hit2

    # ③ 单行实参：直接执行 ⇒ 无确认
    args = "onDelete = onDeleteOne"
    m = re.search(r"\bonDelete\s*=\s*([^\n]*)", args)
    expr = m.group(1).strip()
    hit3 = bool(DIRECT.search(expr)) or bool(re.fullmatch(r"on[A-Z]\w*", expr))
    print(f"  {'✓' if hit3 else '✗'} 裸转发/直接执行能被识别")
    ok = ok and hit3

    print()
    if not ok:
        print("✗ 规则失效 —— 检查结果不可信")
        return 1
    print("✓ 规则正常")
    return 0


def main():
    if "--selftest" in sys.argv[1:]:
        return selftest()

    hits = scan()
    bad = [h for h in hits if h[4] != "confirmed"]

    print("══ 删除入口守卫（%d 个入口）══\n" % len(hits))
    for rel, no, fname, label, verdict, why in sorted(hits, key=lambda x: (x[0], x[1])):
        mark = {"confirmed": "✓", "direct": "✗"}.get(verdict, "?")
        short = rel.split("nativeapp/")[-1]
        print(f"  {mark} {short}:{no}  [{label}]  函数 {fname}")
        print(f"      {why}")

    print()
    if bad:
        print(f"✗ {len(bad)} 个入口没有二次确认")
        print("  改法：点击只置状态（pendingXxx = ...），父组件用 GlassConfirm 弹确认。")
        print("  确属误报（删的是缓存不算数据），在入口行写 `// delguard:ok 原因` 签收。")
        return 1 if "--gate" in sys.argv[1:] else 0
    print("✓ 所有删除入口都有二次确认")
    return 0


if __name__ == "__main__":
    sys.exit(main())
