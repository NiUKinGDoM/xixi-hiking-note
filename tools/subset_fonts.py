#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
字体子集化：把 3.4MB 的中文字库砍到能用的最小集合。

★2026-11-11 背景：
    APK 6.5MB，其中两个宋体字库占 **6.7MB**（解压后）—— 包里除了字体几乎没别的。
    全量中文字库有 2 万多字，而这个 App 用到的地方只有：
      · 界面文案（从 .kt 源码里能全量提取）
      · 用户输入（山名/日记）—— 常用汉字覆盖
      · 数字、英文、标点

   ⇒ 保留「GB2312 全集 6763 字 + 源码里出现的所有字符 + ASCII + 常用符号」，
     其余丢掉。缺字时 Android 会自动回退系统字体（不会出豆腐块），
     只是字形跟宋体不一致 —— 对生僻山名是可接受的代价。

   预期：3.4MB → 1.2~1.6MB，包体 6.5MB → 3MB 左右。

用法：
    python tools/subset_fonts.py --dry-run    # 只算不写
    python tools/subset_fonts.py              # 真正替换（原字体备份到 _backup_fonts/）
"""
import argparse
import glob
import io
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_DIR = os.path.join(ROOT, "app", "src", "main", "res", "font")
BACKUP = os.path.join(ROOT, "_backup_fonts")
SRC_DIRS = [
    os.path.join(ROOT, "app", "src", "main", "java"),
    os.path.join(ROOT, "app", "src", "main", "res", "values"),
]

# GB2312 全集：一级 3755 + 二级 3008 = 6763 个常用汉字（含全角标点区）
def gb2312_chars():
    out = set()
    for b1 in range(0xB0, 0xF8):
        for b2 in range(0xA1, 0xFF):
            try:
                out.add(bytes([b1, b2]).decode("gb2312"))
            except Exception:
                pass
    # 全角标点 / 符号区（A1A1~A3FE）
    for b1 in range(0xA1, 0xA4):
        for b2 in range(0xA1, 0xFF):
            try:
                out.add(bytes([b1, b2]).decode("gb2312"))
            except Exception:
                pass
    return out


def source_chars():
    """源码/资源里出现过的所有字符 —— 保证界面文案一个字都不缺"""
    out = set()
    for d in SRC_DIRS:
        for p in glob.glob(os.path.join(d, "**", "*.*"), recursive=True):
            if not p.lower().endswith((".kt", ".xml", ".java")):
                continue
            try:
                out |= set(io.open(p, encoding="utf-8", errors="ignore").read())
            except Exception:
                pass
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    try:
        from fontTools import subset
    except Exception:
        print("✗ 需要 fonttools：pip install fonttools")
        return 2

    chars = gb2312_chars() | source_chars()
    chars |= set(chr(c) for c in range(0x20, 0x7F))          # ASCII
    chars |= set("　·—…“”‘’《》〈〉【】、。，；：？！（）×÷≤≥±°％‰")
    chars.discard("\n")
    chars.discard("\r")
    print("保留字符数：%d" % len(chars))

    if not os.path.isdir(BACKUP):
        os.makedirs(BACKUP)

    total_before = total_after = 0
    for path in sorted(glob.glob(os.path.join(FONT_DIR, "*.ttf"))):
        name = os.path.basename(path)
        before = os.path.getsize(path)
        total_before += before

        out = os.path.join(BACKUP, name)
        if not os.path.exists(out):
            shutil.copyfile(path, out)      # 原件备份（_backup_* 已被 gitignore 忽略）

        tmp = path + ".sub"
        args = [
            path,
            "--output-file=" + tmp,
            "--text=" + "".join(sorted(chars)),
            # ★只留中文排版真正用得到的特性：
            #   原来用 `*` 保留全部 OpenType 布局表（GSUB/GPOS 里塞了几百条
            #   拉丁/阿拉伯/印度语的规则），中文一个字都用不上，却占了近 1MB。
            "--layout-features=kern,liga,vert,vrt2,palt,halt",
            "--glyph-names",
            "--no-hinting",                 # 去 hinting：屏幕渲染影响极小，能省一截
            "--drop-tables+=DSIG",
            "--name-IDs=*",
            "--recalc-bounds",
        ]
        try:
            subset.main(args)
            after = os.path.getsize(tmp)
            total_after += after
            print("  %s  %.2fMB → %.2fMB（省 %.0f%%）"
                  % (name, before / 1048576, after / 1048576,
                     (1 - after / before) * 100))
            if a.dry_run:
                os.remove(tmp)
            else:
                os.replace(tmp, path)
        except Exception as e:
            print("  ✗ %s 失败：%s" % (name, e))
            if os.path.exists(tmp):
                os.remove(tmp)
            return 1

    print("\n合计 %.2fMB → %.2fMB（省 %.1fMB）"
          % (total_before / 1048576, total_after / 1048576,
             (total_before - total_after) / 1048576))
    if a.dry_run:
        print("（--dry-run：没有改任何文件）")
    else:
        print("原件备份在 _backup_fonts/，要还原直接拷回去")
    return 0


if __name__ == "__main__":
    sys.exit(main())
