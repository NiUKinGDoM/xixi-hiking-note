#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把本地 `_apk_archive/` 里的历史安装包补传到对应的 GitHub Release。

★2026-11-09 用户：「在历史发布里都把 apk 文件补上啊」

背景：`_apk_archive/` 里攒了 81 个历史包（每版一个），但 GitHub 上只有最近一版有附件。
补上之后 Releases 页面就能真正"翻到任意版本并下载"，而不是只有一行说明。

几个必须注意的点：
  · 资产名统一 `tuxiaoji-vX.Y.Z.apk`（纯 ASCII）—— GitHub 会剥掉中文字符，
    而 App 靠资产名识别安装包（详见 ghrelease.py 里的注释）。
  · 已经挂了同名资产的跳过，可重复跑。
  · 老版本没有 APK 的（归档里缺），只提示，不创建空附件。

用法：
    python tools/backfill_apks.py --dry-run
    python tools/backfill_apks.py
"""
import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ghrelease as g  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ARCHIVE = os.path.join(ROOT, "_apk_archive")
DESKTOP = os.path.join(os.path.expanduser("~"), "Desktop")
# ★2026-11-09 不能只扫 _apk_archive：那里面是"发过的"，而每版打完包是先在
#   项目根目录生成、桌面也有一份 —— 归档只覆盖了一部分，直接导致 38 个版本漏传。
SEARCH_DIRS = [ARCHIVE, ROOT, DESKTOP]
VER_RX = re.compile(r"v(\d+\.\d+\.\d+\.\d+)")


def scan_archive():
    """{版本号: apk 路径}；同一版本多处存在时取**最新修改**的那个文件"""
    found = {}
    for d in SEARCH_DIRS:
        if not os.path.isdir(d):
            continue
        try:
            names = os.listdir(d)
        except Exception:
            continue
        for fn in names:
            if not fn.lower().endswith(".apk"):
                continue
            m = VER_RX.search(fn)
            if not m:
                continue
            ver = m.group(1)
            p = os.path.join(d, fn)
            try:
                mt = os.path.getmtime(p)
            except Exception:
                continue
            if ver not in found or mt > os.path.getmtime(found[ver]):
                found[ver] = p
    return found


def desc_of(path):
    """
    从归档文件名里抽出版本描述：`XiXi原生版-v1.0.2.0-自绘指示块.apk` → `自绘指示块`。

    有些版本当年没写进内置更新日志（只出了包），补 release 时用文件名里的
    一句话当说明 —— 总比留空白强。
    """
    fn = os.path.basename(path)[:-4]
    m = re.search(r"v[\d.]+[-_ ]*(.*)$", fn)
    d = (m.group(1) if m else "").strip(" -_+")
    return d or "该版本未留更新说明"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=g.DEFAULT_REPO)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    local = scan_archive()
    print("本地归档共 %d 个版本" % len(local))

    tok = g.token()
    repo = a.repo

    # 拉全部 release（tag → release）
    rels = {}
    page = 1
    while page <= 5:
        st, arr = g.call("GET", f"{g.API}/repos/{repo}/releases?per_page=100&page={page}", tok)
        if st != 200 or not isinstance(arr, list) or not arr:
            break
        for r in arr:
            rels[r.get("tag_name")] = r
        if len(arr) < 100:
            break
        page += 1
    print("仓库 %s 有 %d 个 release" % (repo, len(rels)))

    uploaded = skipped = norelease = failed = 0
    for ver in sorted(local.keys(), key=lambda v: [int(x) for x in v.split(".")]):
        tag = "v" + ver
        apk = local[ver]
        r = rels.get(tag)
        if not r:
            # 这个版本当年只出了包、没写进内置日志 ⇒ 补一个 release 给它
            if a.dry_run:
                print("  [DRY] 需补建 %s（%s）" % (tag, desc_of(apk)))
                norelease += 1
                continue
            st, j = g.call("POST", f"{g.API}/repos/{repo}/releases", tok, {
                "tag_name": tag,
                "name": "徒步小记 " + tag,
                "body": "【记录】\n- " + desc_of(apk),
                "prerelease": False,
                "make_latest": "false",     # 绝不抢 Latest
                "draft": False,
            })
            if st not in (200, 201):
                print("  ✗ %s 建 release 失败 %s: %s" % (tag, st, str(j)[:100]))
                failed += 1
                continue
            r = j
            rels[tag] = r
            print("  ＋ 补建 release %s（%s）" % (tag, desc_of(apk)))
        asset = "tuxiaoji-%s.apk" % tag
        names = [x.get("name") for x in r.get("assets", [])]
        if asset in names:
            skipped += 1
            continue
        size_mb = os.path.getsize(apk) / 1048576
        if a.dry_run:
            print("  [DRY] %s ← %s（%.1f MB）" % (tag, os.path.basename(apk), size_mb))
            uploaded += 1
            continue
        data = open(apk, "rb").read()
        import urllib.parse
        st, j = g.call(
            "POST",
            f"{g.UPLOAD}/repos/{repo}/releases/{r['id']}/assets?name={urllib.parse.quote(asset)}",
            tok, raw=data, content_type="application/vnd.android.package-archive",
        )
        if st in (200, 201):
            uploaded += 1
            print("  ✓ %s（%.1f MB）" % (tag, size_mb))
        else:
            failed += 1
            print("  ✗ %s 失败 %s: %s" % (tag, st, str(j)[:100]))

    print()
    print("完成：上传 %d，已存在 %d，无 release %d，失败 %d"
          % (uploaded, skipped, norelease, failed))
    if a.dry_run:
        print("（--dry-run：什么都没传）")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
