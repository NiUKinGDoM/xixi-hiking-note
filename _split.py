# -*- coding: utf-8 -*-
"""第 1 步搬运（修正切分点：KDoc 的 /** 在第 110 行）"""
import io
import os

ROOT = r"C:\Users\NIU-XC\Desktop\buddy\hiking-native-20261102"
SRC = os.path.join(
    ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp",
    "ui", "settings", "SettingsScreen.kt",
)
DST = os.path.join(os.path.dirname(SRC), "SettingsWidgets.kt")

lines = io.open(SRC, encoding="utf-8").read().split("\n")
print("原文件行数:", len(lines))
print("第110行:", repr(lines[109]))
print("第209行:", repr(lines[208]))
print("第210行:", repr(lines[209]))
print("第1472行:", repr(lines[1471]))
print("第1473行:", repr(lines[1472]))

seg1 = lines[109:209]      # 110..209：ioScope KDoc + openUrl + AboutLink + ExportOpt + ioScope
seg2 = lines[1472:]        # 1473..末尾：InfoTile / StatInline / SettingRow / modifier

imports = [l for l in lines[:109] if l.startswith("import ")]
print("带过去 import:", len(imports))

body = "\n".join(seg1 + seg2)
body = body.replace("private fun ", "internal fun ")
body = body.replace("private val ", "internal val ")

header = (
    "package com.xixi.hiking.nativeapp.ui.settings\n"
    "\n"
    "// ══════════════════════════════════════════════════════════════\n"
    "//  ★2026-11-11 从 SettingsScreen.kt 拆出来（第 1 步：纯搬移，未改逻辑）\n"
    "//\n"
    "//  SettingRow（分组里的一行）、InfoTile / StatInline（关于页信息块）、\n"
    "//  AboutLink / ExportOpt（关于页链接、导出勾选行）、ioScope（带异常兜底的\n"
    "//  IO 作用域）、androidxClip 等 modifier 扩展。\n"
    "//\n"
    "//  ⚠️ 这里一律保持**无状态**：只接值 + 回调，不持有 remember。\n"
    "//     状态留在 SettingsScreen —— 否则切页再回来状态会被重建（填一半的配置全丢）。\n"
    "// ══════════════════════════════════════════════════════════════\n"
    "\n"
)
new_file = header + "\n".join(imports) + "\n\n" + body
if not new_file.endswith("\n"):
    new_file += "\n"
io.open(DST, "w", encoding="utf-8", newline="\n").write(new_file)
print("SettingsWidgets.kt 行数:", new_file.count("\n") + 1)

keep = lines[:109] + lines[209:1472]
out = "\n".join(keep)
if not out.endswith("\n"):
    out += "\n"
io.open(SRC, "w", encoding="utf-8", newline="\n").write(out)
print("SettingsScreen.kt 行数:", out.count("\n") + 1)
