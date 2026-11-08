#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
气味扫描器（2026-11-05）

和 crashscan 的分工
──────────────────
· crashscan 找的是「一定会崩」的写法。
· smellscan 找的是「不一定崩，但已经在别处崩过 / 卡过」的写法 —— 这个项目里
  每一条规则都对应一次真实事故，不是抄来的通用规范。

四条规则（全部有案底）
──────────────────────
1. `runBlocking`            —— 主线程阻塞。真机表现是"点了没反应"或 ANR。
2. `GlobalScope` / `Thread(` —— 活过界面的协程：用户退出页面后还在写 state，
   回来就是"数据错乱 / 弹窗自己又冒出来"。
3. 组合期写 state           —— 案底：灯箱的 `if (idx > lastIndex) idx = ...`
   在组合期改 state，触发重组风暴；也曾让"删除照片后索引越界"反复闪。
4. `LazyColumn/LazyRow` 的 item 没有 key —— 案底：删除列表中间一条时，
   下面的行会"串位"：勾选态、展开态跟着错行。

用法
──────
    python tools/smellscan.py           # 只列 ERROR/WARN
    python tools/smellscan.py --gate    # 有 ERROR 就非 0
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java")

# 行尾签收：`// smellscan:ok 理由`（理由必填，因为"为什么不改"本身是重要信息）
ACK = re.compile(r"smellscan:ok\s*\S+")

RULES = []


def rule(level, code, why, fix):
    def deco(fn):
        RULES.append({"level": level, "code": code, "why": why, "fix": fix, "fn": fn})
        return fn
    return deco


# ── ① 主线程阻塞 ────────────────────────────────────────────────
@rule("ERROR", "run-blocking",
      "`runBlocking` 会把当前线程钉死。放在点击回调/组合里就是主线程阻塞，"
      "真机表现是「点了没反应」「转圈都不转」，严格模式直接 ANR。",
      "suspend 函数 + `rememberCoroutineScope().launch {}`，或 `LaunchedEffect`")
def check_run_blocking(line, ctx):
    return "runBlocking" in line


# ── ② 活过界面的协程 ────────────────────────────────────────────
@rule("ERROR", "globalscope",
      "`GlobalScope` 没有生命周期：界面销毁后它还活着，回来写一个已经废弃的 state，"
      "轻则无效，重则把旧数据盖到新页面上。",
      "`rememberCoroutineScope()`（跟随组合）或 `lifecycleScope`")
def check_globalscope(line, ctx):
    return "GlobalScope" in line and "import" not in line


@rule("WARN", "raw-thread",
      "裸 `Thread` 不受生命周期管理，也不受协程取消。这个项目所有 IO 都走协程，"
      "混一个 Thread 进来以后没人能统一取消。",
      "`withContext(Dispatchers.IO) { }`")
def check_thread(line, ctx):
    return re.search(r"\bThread\s*\{|\bThread\(\)\s*\.start", line) is not None


# ── ③ 组合期写 state ───────────────────────────────────────────
# 判定方式：同一文件里 `var x by remember`（或 mutableStateOf）声明的变量，
# 如果后面出现「与该声明同缩进的赋值语句」，几乎一定是组合期写入：
# 事件回调里的赋值一定比声明更深一层（在 lambda 里）。
DECL = re.compile(r"^(\s*)var\s+([A-Za-z_]\w*)\s+by\s+remember")


@rule("ERROR", "composition-write",
      "在组合期直接写 state（不是在事件回调里）：会触发「写→重组→再写」的循环，"
      "或者让 UI 读到一个刚被改过的值。案底：灯箱的 `if (idx > last) idx = last`，"
      "当时的症状是「删完照片闪一下、偶尔越界」。",
      "改成 `LaunchedEffect(key) { ... }`，或把 clamp 放进 remember/derivedStateOf 的计算里")
def check_composition_write(line, ctx):
    """
    返回 True 的条件：
      · 本行的缩进 == 某个 remember 变量的声明缩进
      · 本行是 `x = ...` / `x += ...` / `x -= ...`
      · 不是声明行本身、不是注释
    ctx 里带 decls: {缩进字符串: [变量名]}
    """
    stripped = line.strip()
    if stripped.startswith("//"):
        return False
    indent = re.match(r"^(\s*)", line).group(1)
    # 两种形状：
    #   ① 裸赋值：            idx = 0
    #   ② if 里赋值（真实事故）：if (idx > last) idx = last   ← 灯箱那次就是这个
    m = re.match(r"^(\s*)([A-Za-z_]\w*)\s*(\+|-)?=\s*\S", line) or \
        re.match(r"^(\s*)(?:if\s*\(.*?\)\s*)([A-Za-z_]\w*)\s*(\+|-)?=\s*\S", line)
    if not m:
        return False
    name = m.group(2)
    if indent not in ctx["decls"]:
        return False
    return name in ctx["decls"][indent]



# ── ⑤ 空点击处理器 ─────────────────────────────────────────────
@rule("ERROR", "empty-click",
      "点击处理器是空的（`safeClickable { }` / `onClick = { }`）：按钮看起来能点、"
      "点了毫无反应。这个项目出过一次「保存/分享/关闭全部失效」，虽然后来查明是"
      "手势冲突，但**空 lambda 是完全同款的表现**，而且只有点下去才发现。",
      "要么把逻辑补上，要么删掉这个按钮；确实需要占位的，写 onClick = {} 时加注释说明为什么")
def check_empty_click(line, ctx):
    st = line.strip()
    if st.startswith("//"):
        return False
    # safeClickable { }  /  onClick = { }  /  onClick = {  }  中间允许有空格
    if re.search(r"\b(safeClickable|onClick)\s*(=\s*)?\{\s*\}\s*\)?\s*,?\s*$", st):
        return True
    return bool(re.search(r"\b(safeClickable|onClick)\s*(=\s*)?\{\s*\}\s*\)", st))


# ── ④ Lazy 列表缺 key ──────────────────────────────────────────
@rule("WARN", "lazy-no-key",
      "`items(...)` 不给 key：删除中间一条时，Compose 按位置复用，下面的行会串位"
      "（勾选态/展开态跟错行）；列表项里有状态时尤其明显。",
      "`items(list, key = { it.id })` / `itemsIndexed(list, key = { _, it -> it.id })`")
def check_lazy_key(line, ctx):
    if not ctx["in_lazy"]:
        return False
    if not re.search(r"\b(items|itemsIndexed)\s*\(", line):
        return False
    # 单行就写了 key 的放过
    if "key =" in line:
        return False
    # `items(10) { i -> }` 这种按下标生成的也是位置复用，同样有问题
    return True


def iter_files():
    for dirpath, _dn, fns in os.walk(SRC):
        for fn in sorted(fns):
            if fn.endswith(".kt"):
                yield os.path.join(dirpath, fn)


def scan():
    hits = []
    for path in iter_files():
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        try:
            lines = io.open(path, encoding="utf-8").read().splitlines()
        except Exception:
            continue
        if rel.endswith("/ui/theme/Theme.kt"):
            continue

        # 先扫一遍收集 remember 变量（整个文件级，够用：同名变量缩进通常不同）
        decls = {}
        for ln in lines:
            m = DECL.match(ln)
            if m:
                decls.setdefault(m.group(1), []).append(m.group(2))

        # Lazy 容器深度：进 LazyColumn/LazyRow 后若干行内的 items 才算
        lazy_depth = 0
        for i, raw in enumerate(lines, 1):
            s = raw.strip()
            ctx = {"decls": decls, "in_lazy": lazy_depth > 0}

            if re.search(r"\b(LazyColumn|LazyRow|LazyVerticalGrid|LazyHorizontalGrid)\s*[({]", raw):
                lazy_depth = 1
            elif lazy_depth > 0:
                # 用缩进粗略估算是否还在 Lazy 块里：回到 0 缩进就退出
                if raw and not raw.startswith(" ") and not raw.startswith(")"):
                    lazy_depth = 0
                elif re.match(r"^\s{0,4}(fun|private fun|@Composable)\b", raw):
                    lazy_depth = 0

            if not s or s.startswith("//") or s.startswith("*"):
                continue
            # 签收可以写在行尾，也可以写在上一行（长语句换行时更自然）
            if ACK.search(raw) or (i >= 2 and ACK.search(lines[i - 2])):
                continue
            if s.startswith("import "):
                continue

            for r in RULES:
                try:
                    if not r["fn"](raw, ctx):
                        continue
                except Exception:
                    continue
                hits.append({
                    "level": r["level"], "rule": r["code"], "file": rel,
                    "line": i, "code": s[:130], "why": r["why"], "fix": r["fix"],
                })
                break
    order = {"ERROR": 0, "WARN": 1}
    hits.sort(key=lambda h: (order[h["level"]], h["file"], h["line"]))
    return hits


SELFTEST = [
    # (规则 code, 应该报的样本, 不该报的样本)
    ("run-blocking", "                    runBlocking { dao.count() }",
     "                    viewModelScope.launch { dao.count() }"),
    ("globalscope", "        GlobalScope.launch { save() }",
     "        scope.launch { save() }"),
    ("composition-write", "    if (idx > photos.lastIndex) idx = 0",
     "    Box(Modifier.clickable { idx = 0 })"),
    ("empty-click", "                    .safeClickable { }",
     "                    .safeClickable { pendingDelete = r }"),
    ("lazy-no-key", "        items(photos) { p -> PhotoThumb(p) }",
     "        items(photos, key = { it.id }) { p -> PhotoThumb(p) }"),
]


def selftest() -> int:
    """给每条规则喂一个正样本 / 负样本。

    没有这一步的话，规则写错了（正则差一个字符）也只会安静地"0 处命中"，
    看起来像"代码很干净"—— 这种假阴性比误报危险得多。
    """
    print("══ 规则自检 ══")
    bad = 0
    decls = {"    ": ["idx"], "        ": ["x"]}
    for code, positive, negative in SELFTEST:
        r = next((x for x in RULES if x["code"] == code), None)
        if r is None:
            print(f"  ✗ {code}：规则不存在")
            bad += 1
            continue
        ctx = {"decls": decls, "in_lazy": True}
        try:
            got_pos = bool(r["fn"](positive, ctx))
            got_neg = bool(r["fn"](negative, ctx))
        except Exception as e:
            print(f"  ✗ {code}：跑样本时抛异常 {e}")
            bad += 1
            continue
        ok = got_pos and not got_neg
        print(f"  {'✓' if ok else '✗'} {code}：正样本 {'报' if got_pos else '没报'}"
              f" / 负样本 {'报了（误报）' if got_neg else '没报'}")
        if not ok:
            bad += 1
    print()
    if bad:
        print(f"✗ {bad} 条规则失效 —— 扫描结果不可信")
        return 1
    print("✓ 全部规则都能正常命中，且不误伤")
    return 0


def main():
    if "--selftest" in sys.argv[1:]:
        return selftest()
    gate = "--gate" in sys.argv[1:]
    hits = scan()
    by = {}
    for h in hits:
        by.setdefault(h["level"], []).append(h)

    print("══ 代码气味扫描 ══")
    for lv in ("ERROR", "WARN"):
        items = by.get(lv, [])
        if not items:
            continue
        print()
        print(f"[{lv}] {len(items)} 处")
        for h in items:
            print(f"  {h['file']}:{h['line']}  ({h['rule']})")
            print(f"      {h['code']}")
            print(f"      为什么：{h['why']}")
            print(f"      建议：{h['fix']}")

    err = len(by.get("ERROR", []))
    print()
    print(f"合计 {len(hits)} 处（ERROR {err} / WARN {len(by.get('WARN', []))}）")
    if err == 0:
        print("✓ 没有 ERROR 级气味")
    if gate and err > 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
