#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
说明文案长度检查（2026-11-05）

用户最初的规矩是「说明性文字不超过 10 个字」。执行一轮之后用户反馈：
「有些文字说明，不必要的就不用只保留十个字，你改回来，现在好多看着别扭」
⇒ 10 字是**标签**的尺度，压说明段会丢信息、读起来电报味。

现在的规则（2026-11-05 调整）
──────────────────────────────
  · 单行标签 / 副标题：10 字左右最舒服（不强制报）
  · 说明段：上限 **16 个汉字**（约一行半，读着连贯）
  · 更长的（帮助说明、迁移引导、法务正文）：行上方写 `// textcheck:ok 原因` 签收

保留这条检查的目的不是逼短句，而是防「一句话塞满一屏」那种失控。

为什么定这条（我的理解，写下来免得以后忘）
──────────────────────────────────────
设置页的每一行本来就只有一个标题，说明文字是"标题的补充"。字一多就变成一段
小作文，扫一眼读不完 —— 而设置页的使用方式是"扫过去找那一项"。
短说明（"先收测试版""官方签名"）能被一眼扫到，长说明（"支持 zip 完整备份、
纯数据备份（.json / .html）与云端备份文件；同一条记录按最后修改时间取新…"）
则必须逐字读，等于没有。

不在这条规则里的（正文，不是说明）
────────────────────────────────
· 隐私政策 / 免责声明条文
· 更新日志
· 数据管理说明这类**只有一个入口**的长说明弹窗（它是内容本身，不是标签）

检查范围
────────
`ui/` 下所有 `desc = "..."`、`subtitle = "..."`、`hint = "..."`，
以及 `Text("...")` 里明显是提示语的短行（长度 11~40 且不含标点句号）。
超长的一律报出来，人工改写。

用法
──────
    python tools/textcheck.py          # 列出超标项
    python tools/textcheck.py --gate   # 有超标就非 0（发版门禁用）
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UI = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp", "ui")
# 单行标签（设置项副标题这类）：一行读得完
LIMIT_LABEL = 16
# 弹窗正文 / 帮助说明：三行以内。到这里不再逼短句 ——
# 「这一步会永久删除…」这类话不能说成「删了就找不回」，丢的是安全信息。
LIMIT_BODY = 48

# 这些字段是"单行标签"，用严一点的尺度
LABEL_FIELDS = re.compile(r"\b(desc|subtitle|titleSuffix|hint|placeholder)\s*=")

# 扫所有中文字面量（不再只查 desc —— 用户的原话是"整个软件的说明性文字"）
FIELD_RX = re.compile(r'"([^"\\]*[\u4e00-\u9fff][^"\\]*)"')
# 正文/长文，不是"说明"：隐私政策、免责声明、更新日志、回忆册 HTML
SKIP_FILES = {
    "AboutDialogs.kt",
    "FullChangelog.kt",
    "MemoirHtml.kt",
}
# 行尾/上一行写 `// textcheck:ok 理由` 的放过（示例数据正文、诊断 txt）
ACK = re.compile(r"textcheck:ok")


def cn_len(s: str) -> int:
    """按"字"算：中文一字算 1，ASCII 连续单词算 1 个，标点算 1。"""
    n = 0
    i = 0
    while i < len(s):
        ch = s[i]
        if ch.isascii() and (ch.isalnum() or ch in "._-/:+%"):
            # 连续 ASCII 片段算 1
            while i < len(s) and s[i].isascii() and (s[i].isalnum() or s[i] in "._-/:+%"):
                i += 1
            n += 1
            continue
        if ch.isspace():
            i += 1
            continue
        n += 1
        i += 1
    return n


def scan():
    hits = []
    for dirpath, _dn, fns in os.walk(UI):
        for fn in sorted(fns):
            if not fn.endswith(".kt") or fn in SKIP_FILES:
                continue
            p = os.path.join(dirpath, fn)
            rel = os.path.relpath(p, ROOT).replace("\\", "/")
            lines = io.open(p, encoding="utf-8").read().splitlines()
            for no, raw in enumerate(lines, 1):
                s = raw.strip()
                if s.startswith("//") or s.startswith("*"):
                    continue
                # 签收可以写在行尾、上一行，或上两行 —— 长字符串常被拆成两三行拼接，
                # 签注只会写在第一行上方，卡死"上一行"会让后半段永远报错。
                if ACK.search(raw) or any(
                    ACK.search(lines[k]) for k in range(max(0, no - 3), no - 1)
                ):
                    continue
                is_label = bool(LABEL_FIELDS.search(raw))
                limit = LIMIT_LABEL if is_label else LIMIT_BODY
                for m in FIELD_RX.finditer(raw):
                    field, text = ("副标题" if is_label else "正文"), m.group(1)
                    # 模板插值不算（长度不可知）
                    if "$" in text:
                        continue
                    n = cn_len(text)
                    if n > limit:
                        hits.append((rel, no, field, text, n, limit))
                    # 正文按"累计长度"再兜一层：同一段被拆成多行拼接时，
                    # 单行都不超，合起来却是一屏 —— 那才是真正的失控
    return hits


def selftest() -> int:
    """给规则喂正负样本。

    「全部达标」有两种可能：文案真的都合格，或者检查器坏了。
    上一轮就出现过这种假阴性（smellscan 的规则没注册，一直"0 处命中"），
    所以这里也补一条自检。
    """
    print("══ 规则自检 ══")
    bad = 0
    cases = [
        # (说明, 行内容, 应该报?)
        ("副标题超 16", 'desc = "记录和计划列表每页显示多少条（5到50）"', True),
        ("副标题达标", 'desc = "顶栏显示实时帧率"', False),
        ("正文超 48", 'Text("这一步会永久删除全部徒步数据与照片，并解除网盘绑定；云端备份文件不受影响。而且还要再多说两句凑够长度才能触发检查。")', True),
        ("正文达标", 'Text("这张照片会从记录里删除，无法恢复")', False),
        ("模板插值跳过", 'desc = "$a 和 $b 和 $c 和 $d 和 $e 和 $f 和 $g 和 $h"', False),
    ]
    for name, line, expect in cases:
        is_label = bool(LABEL_FIELDS.search(line))
        limit = LIMIT_LABEL if is_label else LIMIT_BODY
        got = any(cn_len(m.group(1)) > limit for m in FIELD_RX.finditer(line) if "$" not in m.group(1))
        ok = got == expect
        print(f"  {'✓' if ok else '✗'} {name}：{'报' if got else '没报'}")
        if not ok:
            bad += 1
    print()
    if bad:
        print(f"✗ {bad} 条规则失效 —— 检查结果不可信")
        return 1
    print("✓ 规则正常")
    return 0


def main():
    if "--selftest" in sys.argv[1:]:
        return selftest()
    hits = scan()
    print("══ 说明文案长度（副标题 ≤%d / 正文 ≤%d 字）══" % (LIMIT_LABEL, LIMIT_BODY))
    if not hits:
        print("✓ 全部达标")
        return 0
    for rel, no, field, text, n, limit in hits:
        print(f"  {rel}:{no}  {field} = \"{text}\"  （{n} 字，超 {n - limit}）")
    print()
    print(f"合计 {len(hits)} 条超标")
    print("改法：副标题压到一句读完；正文留信息量最大的那句。")
    print("确有必要更长的，行上方写 `// textcheck:ok 原因` 签收。")
    return 1 if "--gate" in sys.argv[1:] else 0


if __name__ == "__main__":
    sys.exit(main())
