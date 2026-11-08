#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
升版本号（原生版）

用法：
    python tools/bump.py                 # vc +1，自动算 versionName
    python tools/bump.py --to 60         # 直接指定 vc
    python tools/bump.py --note "改了什么"  # 顺便在原生版更新日志最前面插一条

做的事（顺序固定，缺一步都算失败）：
  ① 读 app/build.gradle.kts 的 `val vc`，+1（或 --to）
  ② 按 1.x.x.x 公式算出 versionName，写回
  ③ README 的「版本对照表」补一行、给当前版加粗
  ④ 原生版更新日志最前面插一条（有 --note 就带内容，否则放占位）
  ⑤ 打印结果，交给 tools/gate.sh 验证

★为什么要有它：这三处以前全靠手抄，抄错一次就是「设置页显示 1.0.4.9、APK 里是 1.0.4.10」。
"""
import argparse
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
    i = vc - 1
    return "1.%d.%d.%d" % ((i // 121) % 11, (i // 11) % 11, i % 11)


def read_text(p):
    return io.open(p, encoding="utf-8").read()


def write_text(p, s):
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--to", type=int, default=0, help="直接指定 vc")
    ap.add_argument("--note", default="", help="更新日志正文（可省略）")
    ap.add_argument("--dry-run", action="store_true", help="只看会改成什么，不落盘")
    args = ap.parse_args()

    gradle = read_text(GRADLE)
    m = re.search(r"val\s+vc\s*=\s*(\d+)", gradle)
    if not m:
        print("✗ build.gradle.kts 里找不到 val vc")
        return 1
    old_vc = int(m.group(1))
    new_vc = args.to if args.to else old_vc + 1
    if new_vc <= old_vc and not args.to:
        print("✗ 新 vc 必须大于旧值")
        return 1

    if args.dry_run:
        print("（--dry-run：不写盘）将改 vc %d → %d、versionName %s → %s，并补 README 表 + 更新日志"
              % (old_vc, new_vc, version_name(old_vc), version_name(new_vc)))
        return 0

    # ② 写回 gradle
    gradle = gradle.replace("val vc = %d" % old_vc, "val vc = %d" % new_vc)
    write_text(GRADLE, gradle)

    old_name, new_name = version_name(old_vc), version_name(new_vc)
    print("① vc %d → %d" % (old_vc, new_vc))
    print("② versionName %s → %s" % (old_name, new_name))

    # ③ README 对照表：去重、给新行加粗、旧行的加粗去掉
    readme = read_text(README)
    readme = readme.replace("**%s**" % old_name, old_name)   # 旧行取消加粗
    row = "| %d | %d | **%s** |" % (new_vc, new_vc - 1, new_name)
    nxt = new_vc + 1
    nxt_row = "| %d | %d | %s |" % (nxt, nxt - 1, version_name(nxt))
    if row not in readme:
        # 在表格最后一行（当前最新）之后插入
        lines = readme.split("\n")
        idx = None
        for i, l in enumerate(lines):
            if re.match(r"\|\s*%d\s*\|" % new_vc, l) or re.match(r"\|\s*%d\s*\|" % old_vc, l):
                idx = i
        if idx is not None:
            # 删掉同 vc 的旧行，再插入新行 + 下一版预告
            lines = [l for l in lines if not re.match(r"\|\s*%d\s*\|" % new_vc, l)]
            pos = next((i for i, l in enumerate(lines) if re.match(r"\|\s*%d\s*\|" % old_vc, l)), None)
            if pos is not None:
                lines.insert(pos + 1, nxt_row)
                lines.insert(pos + 1, row)
                # 去掉"预告"重复：下一版若已存在则只留一行
                seen = set()
                out = []
                for l in lines:
                    mm = re.match(r"\|\s*(\d+)\s*\|", l)
                    key = mm.group(1) if mm else None
                    if key and key in seen:
                        continue
                    if key:
                        seen.add(key)
                    out.append(l)
                lines = out
                readme = "\n".join(lines)
        write_text(README, readme)
        print("③ README 对照表：+ %s" % new_name)

    # ④ 更新日志插一条
    cl = read_text(CHANGELOG)
    if ('"v%s"' % new_name) not in cl:
        body = args.note.strip() or "（待补：这版改了什么）"
        # ★2026-11-03：note 里常写成「1. xx 2. xx 3. xx」一整行，
        #   直接塞进更新日志会变成一条读不动的长句（弹窗里就是一大坨）。
        #   这里按编号拆成多条，每条一行 —— 弹窗按行渲染，才是一条条列出来。
        # ★按「1. / 2. / 3.」切开；不能只认"空格后面的数字"——
        #   笔记里常写成「…效果）2. 底栏…」，2 前面是中文括号，不是空格。
        parts = re.split(r"(?<!\d)(?=\d+[.、]\s)", body)
        items = []
        for part in parts:
            t = re.sub(r"^\d+[.、]\s*", "", part.strip()).strip()
            if t:
                items.append(t.replace('"', '\\"'))
        if not items:
            items = [body]
        # 每行 Kotlin：        "- 文案\n" +
        # （注意：这里要产出**字面**的 \n 给 Kotlin，所以是 \\n）
        items_src = " +\n".join(['        "- %s\\n"' % t for t in items])
        entry = '    "v%s" to\n        "【优化】\\n" +\n%s,\n\n' % (new_name, items_src)
        cl = cl.replace("val NativeChangelog: List<Pair<String, String>> = listOf(\n",
                        "val NativeChangelog: List<Pair<String, String>> = listOf(\n" + entry, 1)
        write_text(CHANGELOG, cl)
        print("④ 更新日志：已插 v%s" % new_name)

    print("\n下一步：bash tools/gate.sh")
    return 0


if __name__ == "__main__":
    sys.exit(main())
