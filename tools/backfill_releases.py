#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把 App 内置更新日志里的**全部历史版本**补成 GitHub Release（只补记录，不传安装包）。

★2026-11-09 用户：「在 github 里补齐原生版的所有更新记录和内容」

为什么要这个：
  App 里有 82 条版本的更新日志（FullChangelog.kt），但 GitHub 上只有最近几条 ——
  用户想翻"上一版改了什么"只能进 App 里看。Releases 页面本该是完整的产品历史。

为什么**不传安装包**：
  历史 APK 有的在本地 `_apk_archive/`、有的早就没了；而且装回老版本没有任何好处
  （数据库迁移只前进不后退）。这里只补"说明"，能下到的最新版始终是主版本。
  ⇒ 每个补出来的 release 都没有 .apk 资产，App 的检查更新会自动跳过它们
    （`Updater.nativeApkUrl()` 拿不到资产就 continue），不会造成"提示有更新但下不到"。

用法：
    python tools/backfill_releases.py --dry-run     # 先看要创建哪些
    python tools/backfill_releases.py               # 真创建
    python tools/backfill_releases.py --repo NiUKinGDoM/xixi-hiking   # 换仓库（一般不用）
"""
import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ghrelease as g  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHANGELOG_KT = os.path.join(
    ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp",
    "ui", "settings", "FullChangelog.kt",
)


def parse_changelog():
    """
    从 FullChangelog.kt 抽出 [(version, text), ...]，保持文件里的顺序（新→旧）。

    结构长这样（Kotlin 字符串拼接）：
        "v1.1.0.5" to
            "【优化】\\n" +
            "- 一句话\\n",
    """
    src = open(CHANGELOG_KT, encoding="utf-8").read()
    out = []
    # 每个版本块：从 "vX" to 开始，到下一个 "vX" to 或列表结束
    for m in re.finditer(
        r'"v(\d+(?:\.\d+)+)"\s*to\s*(.*?)(?=\n\s*"v\d|\n\s*\))', src, re.S
    ):
        ver, block = m.group(1), m.group(2)
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', block)
        text = "".join(parts)
        text = (text.replace("\\n", "\n")
                    .replace('\\"', '"')
                    .replace("\\\\", "\\")).strip()
        if text:
            out.append((ver, text))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=g.DEFAULT_REPO)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    entries = parse_changelog()
    print("内置日志共 %d 个版本（%s → %s）" % (len(entries), entries[0][0], entries[-1][0]))

    tok = g.token()
    repo = a.repo

    # 已存在的 tag 不重复创建
    exist = set()
    page = 1
    while page <= 5:
        st, arr = g.call("GET", f"{g.API}/repos/{repo}/releases?per_page=100&page={page}", tok)
        if st != 200 or not isinstance(arr, list) or not arr:
            break
        exist |= {r.get("tag_name") for r in arr}
        if len(arr) < 100:
            break
        page += 1
    print("仓库 %s 现有 %d 个 release" % (repo, len(exist)))

    created = skipped = failed = 0
    for ver, text in entries:
        tag = "v" + ver
        if tag in exist:
            skipped += 1
            continue
        if a.dry_run:
            print("  [DRY] 将创建 %s" % tag)
            created += 1
            continue
        st, j = g.call("POST", f"{g.API}/repos/{repo}/releases", tok, {
            "tag_name": tag,
            "name": "徒步小记 " + tag,
            "body": text,
            "prerelease": False,
            # ★关键：补历史时绝不能抢 Latest（否则最新版会变成 v1.0.0.0）
            "make_latest": "false",
            "draft": False,
        })
        if st in (200, 201):
            created += 1
            print("  ✓ %s" % tag)
        elif st == 422:
            skipped += 1
            print("  · %s 已存在，跳过" % tag)
        else:
            failed += 1
            print("  ✗ %s 失败 %s: %s" % (tag, st, str(j)[:120]))

    print()
    print("完成：新建 %d，跳过 %d，失败 %d" % (created, skipped, failed))
    if a.dry_run:
        print("（--dry-run：什么都没写）")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
