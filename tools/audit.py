#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
审计脚本 —— 系统级（Android 平台） + 软件层（代码质量）双向排查

用法：
    python tools/audit.py            # 全量检查
    python tools/audit.py --strict   # WARN 也当失败（发版前用）

和其它脚本的分工（不重复）：
    gate.sh       → 版本/文档一致性 + 单测
    designcheck   → emoji / 字号 / 弹窗宽度 / 颜色散落
    apkcheck.py   → 打出来的 APK 体检（图标/权限/minSdk）
    本脚本        → **静态代码 + 清单 + 工程配置**，找"跑起来才炸"的那类问题
"""
import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp")
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")
GRADLE = os.path.join(ROOT, "app", "build.gradle.kts")

errors, warns, infos = [], [], []


def E(msg, fix=""):
    errors.append((msg, fix))


def W(msg, fix=""):
    warns.append((msg, fix))


def I(msg, fix=""):
    infos.append((msg, fix))


def read(p):
    try:
        return open(p, encoding="utf-8").read()
    except Exception:
        return ""


def kt_files():
    out = []
    for base, _, files in os.walk(SRC):
        for f in files:
            if f.endswith(".kt"):
                out.append(os.path.join(base, f))
    return sorted(out)


# ══════════════════════════════════════════════════════════════
#  一、系统级：Android 平台行为/权限/存储/崩溃兜底
# ══════════════════════════════════════════════════════════════
def audit_system():
    man = read(MANIFEST)
    gradle = read(GRADLE)

    # 1) SDK 版本
    m = re.search(r"minSdk\s*=\s*(\d+)", gradle)
    t = re.search(r"targetSdk\s*=\s*(\d+)", gradle)
    c = re.search(r"compileSdk\s*=\s*(\d+)", gradle)
    if m and int(m.group(1)) < 23:
        W("minSdk < 23：FileProvider / 运行时权限分支会变多")
    if t and c and int(t.group(1)) < int(c.group(1)):
        I(f"targetSdk {t.group(1)} < compileSdk {c.group(1)}：新系统行为不会被强制启用（保守，可接受）")

    # 2) 权限：只允许必要的
    perms = re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', man)
    allowed = {
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
    }
    for p in perms:
        if p not in allowed:
            W(f"声明了非白名单权限：{p}", "确认是否真的需要，多余权限会被应用商店和用户质疑")
    if "android.permission.INTERNET" not in perms:
        E("缺少 INTERNET 权限：检查更新 / 云备份会直接失败")
    # Android 13+ 通知权限
    if any("Notifier" in read(f) for f in kt_files()) and not any("POST_NOTIFICATIONS" in x for x in perms):
        W("用了通知但没声明 POST_NOTIFICATIONS（Android 13+ 通知会被静默丢弃）",
          "在 Manifest 加 <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\"/> 并运行时申请")

    # 3) 明文流量：WebDAV 地址可能是 http
    if "usesCleartextTraffic" not in man:
        W("未声明 usesCleartextTraffic：用户填 http:// 的 WebDAV 地址会连不上",
          "要么在 Manifest 显式允许，要么在 UI 层明确提示只支持 https")

    # 4) FileProvider（分享/导出照片）
    if "FileProvider" not in man:
        W("没有 FileProvider：分享照片走 FileProvider.getUriForFile 会崩")
    elif "file_paths" not in man and "provider_paths" not in man:
        W("FileProvider 没配 meta-data 路径文件")

    # 5) 崩溃兜底
    has_guard = any("CrashGuard" in read(f) for f in kt_files())
    if not has_guard:
        E("没有全局崩溃兜底（CrashGuard）：任何未捕获异常都会直接闪退，且不留痕")

    # 6) 明文密码：WebDAV 密码是否加密存储
    webdav = read(os.path.join(SRC, "data", "WebDavClient.kt"))
    if webdav:
        if ("EncryptedSharedPreferences" not in webdav) and ("Cipher" not in webdav) and ("crypt" not in webdav.lower()):
            W("WebDAV 密码疑似明文存 SharedPreferences", "至少用 AndroidX Security 或自己做 AES 加密")
        else:
            I("WebDAV 密码有加密处理")

    # 7) 存储权限与 MediaStore 分支
    gallery = read(os.path.join(SRC, "data", "GallerySaver.kt"))
    gallery2 = read(os.path.join(SRC, "ui", "photos", "PhotoLightbox.kt"))
    if ("MediaStore" in gallery or "MediaStore" in gallery2) and "SDK_INT >= 29" not in (gallery + gallery2):
        W("保存相册没有按 API 29 分叉：低版本会写不进去 / 高版本会要 WRITE 权限")

    # 8) 大图资源（OOM 风险）
    for base, _, files in os.walk(os.path.join(ROOT, "app", "src", "main", "res")):
        for f in files:
            if f.lower().endswith((".png", ".jpg", ".jpeg", ".webp")):
                p = os.path.join(base, f)
                sz = os.path.getsize(p)
                if sz > 2 * 1024 * 1024:
                    W(f"资源图 > 2MB：{os.path.relpath(p, ROOT)}（{sz/1048576:.1f}MB）",
                      "大图放 drawable-nodpi 并在解码时降采样，否则低端机 OOM")

    # 9) minify 状态
    if "isMinifyEnabled = false" in gradle:
        I("release 未开混淆（体积偏大，但排障友好）")

    # 10) 网络安全：是否有 networkSecurityConfig
    if "networkSecurityConfig" not in man and "usesCleartextTraffic" not in man:
        I("未使用 networkSecurityConfig（默认只允许系统信任证书，够用）")

    # 11) 备份开关（allowBackup 会把本地数据同步到云）
    if 'android:allowBackup="true"' in man:
        I("allowBackup=true：系统会自动备份 App 数据（用户换机可恢复，但也意味着数据出本机）")


# ══════════════════════════════════════════════════════════════
#  二、软件层：代码质量 / 死代码 / 风险点
# ══════════════════════════════════════════════════════════════
def audit_code():
    files = kt_files()
    total_lines = 0
    big_files = []
    long_fun = []
    todo = []
    empty_catch = 0
    println = []
    unused_import = []

    for p in files:
        src = read(p)
        lines = src.split("\n")
        total_lines += len(lines)
        rel = os.path.relpath(p, ROOT)

        if len(lines) > 900:
            big_files.append((rel, len(lines)))

        # 长函数（粗略：fun 到下一个同缩进 } 之间）
        for m in re.finditer(r"^(?P<ind>\s*)(?:private |internal |public )?fun\s+(?P<name>\w+)", src, re.M):
            ind = len(m.group("ind"))
            start = src[: m.start()].count("\n")
            body = src[m.start():]
            end = len(body)
            for mm in re.finditer(r"^" + " " * ind + r"\}", body, re.M):
                end = mm.end()
                break
            n = body[:end].count("\n")
            if n > 140:
                long_fun.append((rel, m.group("name"), n))

        for i, ln in enumerate(lines, 1):
            if re.search(r"\b(TODO|FIXME|XXX|HACK)\b", ln):
                todo.append(f"{rel}:{i}  {ln.strip()[:90]}")
            if re.search(r"catch\s*\(\s*\w*\s*:\s*Throwable\s*\)\s*\{\s*\}", ln) or \
               re.search(r"catch\s*\([^)]*\)\s*\{\s*\}\s*$", ln):
                empty_catch += 1
            # 只把 println / Log.d|v|i 当调试残留；Log.e / Log.w 是错误日志，应当保留
            if re.search(r"\bprintln\(|\bLog\.[dvi]\(", ln):
                println.append(f"{rel}:{i}")

        # 未使用的 import（只查单行 import，且名字在文件其它位置没出现）
        for m in re.finditer(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", src, re.M):
            fq = m.group(1)
            alias = m.group(2) or fq.split(".")[-1]
            body = src.replace(m.group(0), "")
            if not re.search(r"\b" + re.escape(alias) + r"\b", body):
                unused_import.append(f"{rel}: {fq}")

    if big_files:
        for rel, n in big_files:
            W(f"超大源文件 {n} 行：{rel}", "拆成多个文件（>900 行时定位和 review 成本陡增）")
    if long_fun:
        for rel, name, n in long_fun[:12]:
            I(f"长函数 {n} 行：{rel} → {name}()")
    if todo:
        for t in todo[:15]:
            W("遗留标记 " + t)
    if empty_catch > 8:
        W(f"空 catch/finally 共 {empty_catch} 处", "确认每处都真的可以吞；吞掉的异常要进 Trace/软错误统计")
    if println:
        for x in println[:10]:
            W("调试输出残留 " + x, "用 Trace/AppToast 代替（Log.e/w 属于错误日志，不算）")
    if unused_import:
        for x in unused_import[:20]:
            I("未使用的 import → " + x)

    I(f"源码规模：{len(files)} 个 kt 文件 / {total_lines} 行")

    # 单测覆盖
    test_dir = os.path.join(ROOT, "app", "src", "test")
    n_test = 0
    if os.path.isdir(test_dir):
        for base, _, fs in os.walk(test_dir):
            for f in fs:
                if f.endswith(".kt"):
                    n_test += len(re.findall(r"@Test", read(os.path.join(base, f))))
    if n_test < 20:
        W(f"单测只有 {n_test} 项")
    else:
        I(f"单测 {n_test} 项")

    # 数据层一致性：Room 实体字段与导出 payload 是否都覆盖
    ents = read(os.path.join(SRC, "data", "Entities.kt"))
    export = read(os.path.join(SRC, "data", "BackupExport.kt"))
    for field in ["companions", "notes", "photosJson", "elevation"]:
        if field in ents and field not in export:
            W(f"导出没带字段 {field}：换机导入会丢这一列")

    # Compose 侧的高风险写法
    all_src = "\n".join(read(f) for f in files)
    if "Modifier.shadow(" in all_src:
        W("又出现了 Modifier.shadow —— 它和 clip 叠加会漏白条（弹窗踩过一次）")
    if "HorizontalPager" in all_src:
        I("项目里还有 HorizontalPager：它的手势会抢点击（主页面踩过一次）")


# ══════════════════════════════════════════════════════════════
#  三、工程层：备份目录 / 构建产物 / 文档
# ══════════════════════════════════════════════════════════════
def audit_project():
    # 备份目录占用
    for d in os.listdir(ROOT):
        p = os.path.join(ROOT, d)
        if os.path.isdir(p) and d.startswith("_backup"):
            size = sum(os.path.getsize(os.path.join(b, f)) for b, _, fs in os.walk(p) for f in fs)
            I(f"备份目录 {d}：{size/1048576:.1f}MB（确认无误后可删）")

    # 根目录散落的 APK
    apks = [f for f in os.listdir(ROOT) if f.endswith(".apk")]
    if len(apks) > 3:
        W(f"根目录堆了 {len(apks)} 个 APK", "只留最新版，其余归档到 _apk_archive/")

    # 文档
    for doc in ["docs/REGRESSION.md", "README.md"]:
        if not os.path.exists(os.path.join(ROOT, doc)):
            W(f"缺少文档 {doc}")


# ══════════════════════════════════════════════════════════════
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="WARN 也当失败")
    args = ap.parse_args()

    print("════ 系统级检查（Android 平台）════")
    audit_system()
    print("════ 软件层检查（代码质量）════")
    audit_code()
    print("════ 工程层检查 ════")
    audit_project()

    def dump(title, items, mark):
        if not items:
            return
        print(f"\n{mark} {title}（{len(items)}）")
        for msg, fix in items:
            print(f"  · {msg}")
            if fix:
                print(f"    → {fix}")

    dump("错误", errors, "✗")
    dump("警告", warns, "!")
    dump("提示", infos, "·")

    print("\n──────── 汇总 ────────")
    print(f"错误 {len(errors)} · 警告 {len(warns)} · 提示 {len(infos)}")
    if errors or (args.strict and warns):
        print("✗ 审计未通过")
        return 1
    print("✓ 审计通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
