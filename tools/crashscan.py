#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
崩溃点扫描器 v2（2026-11-05）

为什么要有它
────────────
这个项目真机上出过的崩溃，绝大多数不是"逻辑写错"，而是**在数据不可控的地方
用了会抛异常的写法**：

  · 导入别人（或网页版）的备份 → 缺字段 / 类型变了 → `toInt()` 抛 NumberFormatException
  · 记录被删到只剩 0 条 → `list.first()` / `photos[0]` 抛 IndexOutOfBounds
  · 日期字符串是空 / "2026-11" → `SimpleDateFormat.parse` 抛 ParseException
  · `!!` 在一个 nullable 恰好为 null 的路径上 → NullPointerException

这些写法在"数据一定合法"时没错，错在**这里的数据不一定合法**。

三条设计原则（v1 噪音太大，重写时定下的）
────────────────────────────────────────
1. **要认得上游守卫**：`if (all.isEmpty()) return` 之后紧跟的 `all.first()`
   是安全的，不能报。所以判定要看"当前语句 + 前两行"。
2. **要能人工签收**：确实安全但机器看不出来的（比如上游函数保证非空），
   在行尾写 `// crashscan:ok 原因`，从此不再报。签名要留原因，不许裸写。
3. **要准，不要全**：宁可漏报也不刷屏。刷屏的扫描器第二次就没人跑了。

用法
──────
    python tools/crashscan.py            # 正式代码，列 HIGH/MED
    python tools/crashscan.py --all      # 连 INFO
    python tools/crashscan.py --tests    # 连测试代码（测试里 !! 是常态）
    python tools/crashscan.py --gate     # 有未签收的 HIGH 就非 0（接门禁）
    python tools/crashscan.py --file X   # 只看某个文件
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java")
TEST_SRC = os.path.join(ROOT, "app", "src", "test", "java")

# 行尾签收标记：crashscan:ok 理由
ACK = re.compile(r"crashscan:ok")

# 上游守卫：出现在「当前语句 + 上一行 + 上上行」里，说明已经判过
GUARDS = (
    "isNotEmpty()", "isNotBlank()", "isNullOrEmpty()", "isNullOrBlank()",
    "isEmpty()", "isBlank()", "coerceIn(", "coerceAtLeast(",
    "startsWith(", "endsWith(",
    "size >", "size >=", "size !=", ".size >", ".size ==", "size ==",
    "< 0", "indexOf(", "lastIndex",
    "!= null", "?:", "orEmpty()", "orNull()", "firstOrNull", "lastOrNull",
    "getOrNull", "?." , "runCatching", "catch",
)

# 只对「明显来自外部字符串」的 toInt 报错，避免把 cur.toInt()（Double 转 Int）也报
STR_RECV = r"(?:getString\([^()]*\)|optString\([^()]*\)|\.trim\(\)|text\.toString\(\)|\.toString\(\)|readText\(\)|readLine\(\))"

RULES = [
    ("HIGH", "double-bang", re.compile(r"[A-Za-z0-9_\)\]]!!(?!\s*=)"),
     "`!!` 在 null 时直接 NPE。历史战绩：导入缺字段的备份、照片文件已删、计划没选日期。",
     "`?.let {}` / `?: 默认值` / `requireNotNull(x) { \"场景\" }`（后者至少能在崩溃日志里留下场景）"),

    ("HIGH", "first-last", re.compile(r"\.(first|last)\(\)"),
     "空列表上 `first()/last()` 抛 NoSuchElementException。列表来自数据库或过滤结果时长度不可控。",
     "`firstOrNull()` / `lastOrNull()`，再判空"),

    ("HIGH", "index-access", re.compile(r"\b[A-Za-z_]\w*\[\s*(?:0|\w+)\s*\](?!\s*=[^=])"),
     "下标访问不检查长度：列表为空就是 IndexOutOfBounds。",
     "`getOrNull(i)`，或先 `if (list.size > i)`"),

    ("HIGH", "string-to-number", re.compile(STR_RECV + r"\s*\.(toInt|toLong|toDouble|toFloat)\(\)"),
     "字符串直转数字，遇到空串 / 带单位 / 全角数字抛 NumberFormatException。"
     "导入备份和手填输入框正是这种字符串的来源。",
     "`toIntOrNull() ?: 默认值`"),

    ("MED", "date-parse", "DATE_SENTINEL",
     "日期解析对格式极其敏感：多一个空格、少一位数就抛。日期来自用户手输、旧备份、网页版导入三种来源。",
     "整段包 `runCatching {}.getOrNull()`，失败回退到今天并提示用户"),

    ("MED", "substring", re.compile(r"\.substring\("),
     "`substring` 越界抛 StringIndexOutOfBounds（`s.substring(0,10)` 而 s 是空串）。",
     "`s.take(10)` 天然安全（不足长度返回全部）"),

    ("MED", "require-check", re.compile(r"(?:^|[^.\w])(require|check)\s*\((?!\))"),
     "`require/check` 失败抛异常。放在 UI 回调里就是闪退；放在组合期更糟（整棵树重建都会崩）。",
     "非「不可能发生」的前提，改用 `if (!cond) { 提示; return }`"),

    ("INFO", "lateinit", re.compile(r"\blateinit\s+var\b"),
     "`lateinit` 忘了初始化就是 UninitializedPropertyAccessException。",
     "能改成 `by lazy` 或 nullable + 默认值就改"),
]


DATE_RX = re.compile(r"\.parse\(")
DATE_DECL = re.compile(r"(SimpleDateFormat|DateTimeFormatter)")


def is_date_parse(line: str, lines, i: int) -> bool:
    """只把"确实在解析日期"的 .parse( 当风险点。

    `Uri.parse(...)` / `BackupParser.parse(...)` 同名但完全无关，v2 把它们
    也报了 —— 这类误报会让人不再相信扫描结果。
    """
    if not DATE_RX.search(line):
        return False
    if DATE_DECL.search(line):
        return True
    # 形如 `val f = SimpleDateFormat(...)` 在前几行，之后 `f.parse(s)`
    back = " ".join(lines[max(0, i - 4):i + 1])
    return bool(DATE_DECL.search(back))


def iter_files(only=None, with_tests=False):
    bases = [SRC] + ([TEST_SRC] if with_tests else [])
    for base in bases:
        if not os.path.isdir(base):
            continue
        for dirpath, _dn, fns in os.walk(base):
            for fn in sorted(fns):
                if not fn.endswith(".kt"):
                    continue
                p = os.path.join(dirpath, fn)
                rel = os.path.relpath(p, ROOT).replace("\\", "/")
                if only and only not in rel:
                    continue
                if "/ui/theme/" in rel:
                    continue
                yield p, rel


def scan(only=None, show_all=False, with_tests=False):
    hits, acked = [], 0
    for path, rel in iter_files(only, with_tests):
        try:
            lines = io.open(path, encoding="utf-8").read().splitlines()
        except Exception:
            continue
        # 这个文件里自己定义了 check()/require() 的话，同名的调用不算 Kotlin 的断言
        local_checks = set()
        for ln in lines:
            mm = re.search(r"\bfun\s+(check|require)\s*\(", ln)
            if mm:
                local_checks.add(mm.group(1))

        for i, raw in enumerate(lines):
            s = raw.strip()
            if not s or s.startswith("//") or s.startswith("*") or s.startswith("/*"):
                continue
            if s.startswith("import ") or s.startswith("package "):
                continue
            for level, name, rx, why, fix in RULES:
                if rx == "DATE_SENTINEL":
                    if not is_date_parse(raw, lines, i):
                        continue
                elif not rx.search(raw):
                    continue
                # `fun check(...)` / `fun require(...)` 是声明，不是抛异常
                if name == "require-check" and re.search(r"\bfun\s+(require|check)\b", raw):
                    continue
                if level == "INFO" and not show_all:
                    continue
                if ACK.search(raw):
                    acked += 1
                    continue
                if name == "require-check":
                    mm = re.search(r"(?:^|[^.\w])(check|require)\s*\(", raw)
                    if mm and mm.group(1) in local_checks:
                        continue
                # 上游守卫：把当前语句 + 前两行拼起来看（够覆盖 if (...) return 的拆行写法）
                # 前 3 行（守卫常写在"取下标"的上两行）+ 后 4 行（try/catch 常写在下面）
                ctx = " ".join(x.strip() for x in lines[max(0, i - 3):i + 4])
                if any(g in ctx for g in GUARDS):
                    continue
                hits.append({
                    "level": level, "rule": name, "file": rel,
                    "line": i + 1, "code": s[:140], "why": why, "fix": fix,
                })
                break
    order = {"HIGH": 0, "MED": 1, "INFO": 2}
    hits.sort(key=lambda h: (order[h["level"]], h["file"], h["line"]))
    return hits, acked


def main():
    args = sys.argv[1:]
    only = None
    gate = "--gate" in args
    show_all = "--all" in args
    with_tests = "--tests" in args
    if "--file" in args:
        only = args[args.index("--file") + 1]

    hits, acked = scan(only, show_all, with_tests)
    by_level = {}
    for h in hits:
        by_level.setdefault(h["level"], []).append(h)

    print("══ 崩溃点扫描 ══")
    if acked:
        print(f"（已人工签收 {acked} 处 crashscan:ok，不再列出）")
    for lv in ("HIGH", "MED", "INFO"):
        items = by_level.get(lv, [])
        if not items:
            continue
        print()
        print(f"[{lv}] {len(items)} 处")
        for h in items:
            print(f"  {h['file']}:{h['line']}  ({h['rule']})")
            print(f"      {h['code']}")
            print(f"      为什么：{h['why']}")
            print(f"      建议：{h['fix']}")

    total = len(hits)
    high = len(by_level.get("HIGH", []))
    print()
    print(f"合计 {total} 处（HIGH {high} / MED {len(by_level.get('MED', []))} / "
          f"INFO {len(by_level.get('INFO', []))}）")
    if high == 0:
        print("✓ 没有未签收的 HIGH 级崩溃点")
    else:
        print("⚠ HIGH 要逐条处理：要么改写法，要么在行尾写 `// crashscan:ok 为什么安全`。")

    if gate and high > 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
