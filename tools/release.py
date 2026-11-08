#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
一键发版（原生版）

一条命令做完「升版本 → 门禁 → 打包 → 体检 → 拷到桌面并规范命名」。

用法：
    python tools/release.py                       # 升一版并发布
    python tools/release.py --note "顶栏羽化"       # 顺带写更新日志
    python tools/release.py --no-bump             # 不升版本，重打当前版
    python tools/release.py --build-only          # 只打包+体检，不跑门禁（调试用）

命名规范：徒步小记-v{versionName}-{描述}.apk，同时拷到桌面。
          （2026-11-07 应用定名前是 XiXi原生版-v…，历史产物保留原名不动）
"""
import argparse
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESKTOP = os.path.join(os.path.expanduser("~"), "Desktop")
# ★2026-11-04 修「release.py 在 Windows 上直接崩（WinError 193）」
#
#   bin/gradle 是一个 **sh 脚本**，Windows 上 subprocess 直接执行它会报
#   `WinError 193: %1 不是有效的 Win32 应用程序`。
#   Windows 必须执行同目录的 gradle.bat，所以这里按平台补扩展名
#   （也兼容别人把 GRADLE_BIN 指到别处的情况）。
def _resolve_gradle() -> str:
    default = "C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/gradle-8.11.1/bin/gradle"
    path = os.environ.get("GRADLE_BIN", default)
    if os.name == "nt":
        if os.path.isfile(path + ".bat"):
            return path + ".bat"
        if os.path.isfile(path) and path.lower().endswith(".bat"):
            return path
    return path


GRADLE = _resolve_gradle()
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")


def run(cmd, **kw):
    print("$ " + " ".join(cmd))
    r = subprocess.run(cmd, cwd=ROOT, text=True, errors="ignore", **kw)
    return r.returncode


def version_name():
    gradle = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    vc = int(re.search(r"val\s+vc\s*=\s*(\d+)", gradle).group(1))
    i = vc - 1
    return "1.%d.%d.%d" % ((i // 121) % 11, (i // 11) % 11, i % 11)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--note", default="")
    ap.add_argument("--desc", default="", help="APK 文件名里的描述，默认用 --note")
    ap.add_argument("--no-bump", action="store_true")
    ap.add_argument("--build-only", action="store_true")
    a = ap.parse_args()

    env = dict(os.environ)
    env.setdefault("JAVA_HOME", "C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/jdk-21.0.12")

    # ① 升版本
    if not a.no_bump:
        cmd = [sys.executable, "tools/bump.py"]
        if a.note:
            cmd += ["--note", a.note]
        if run(cmd, env=env) != 0:
            return 1

    # ② 门禁（版本一致性 + 全量测试）
    if not a.build_only:
        if run(["bash", "tools/gate.sh"], env=env) != 0:
            print("✗ 门禁没过，停止发版")
            return 1

    # ③ 打包
    rc = subprocess.run(
        [GRADLE, ":app:assembleRelease", "--console=plain"],
        cwd=ROOT, env=env, text=True, errors="ignore", capture_output=True
    )
    if rc.returncode != 0:
        # 失败时把编译错误和最后几行打出来 —— 否则「打包失败」是个黑盒
        for l in rc.stdout.splitlines() + rc.stderr.splitlines():
            if l.startswith("e:") or "FAILED" in l or "What went wrong" in l:
                print(l)
        return 1
    print("✓ assembleRelease ok")

    # ④ 体检
    if run([sys.executable, "tools/apkcheck.py", APK], env=env) != 0:
        return 1

    # ③b 混淆体检（R8 开启后必跑）
    #      · 关键资源（分享底图 / 收款码 / 宋体 / 图标 / FileProvider 路径）没被误删
    #      · 靠名字工作的类（Room 的 AppDatabase_Impl、清单里的 Activity）没被改名
    #      · mapping.txt 按版本归档，否则以后的崩溃堆栈永远还原不回来
    # ③c 签名指纹自洽（换了签名/pin 生成错，会导致每个用户都看到"安装包已被改动"）
    if run([sys.executable, "tools/signpin.py", "--verify"], env=env) != 0:
        return 1

    baseline = os.path.join(ROOT, "_apk_archive", "baseline-last.apk")
    cmd = [sys.executable, "tools/obfcheck.py"]
    if os.path.exists(baseline):
        cmd += ["--baseline", baseline]
    if run(cmd, env=env) != 0:
        return 1

    # ⑤ 命名 + 拷桌面
    name = version_name()
    # ★2026-11-04 文件名别再用整条 note —— 多行 note 会拼出一个 80 字的文件名。
    #   规则：显式 --desc 优先；否则取 note 的**第一个分句**，再去掉序号、限 18 字。
    raw = (a.desc or a.note or "更新").strip()
    raw = re.split(r"[；;，,\n]", raw)[0]
    raw = re.sub(r"^[\d\s.、]+", "", raw).replace(" ", "").replace("/", "-")
    desc = raw[:18] or "更新"
    # ★2026-11-07 应用正式定名「徒步小记」，产物名同步改掉（用户：
    #   「删掉软件名字后的原生版…正式改名为徒步小记」）。
    #   ★改名必须和 app/src/.../data/Updater.kt 的 NATIVE_MARKERS 一起改 ——
    #   那边靠资产名里的关键词挑安装包，名字变了却不加关键词，
    #   「检查更新」会永远认为没有新版本。
    out = os.path.join(ROOT, "徒步小记-v%s-%s.apk" % (name, desc))
    shutil.copyfile(APK, out)
    print("✓ 已生成 %s" % os.path.basename(out))
    if os.path.isdir(DESKTOP):
        shutil.copyfile(out, os.path.join(DESKTOP, os.path.basename(out)))
        print("✓ 已拷到桌面")
    return 0


if __name__ == "__main__":
    sys.exit(main())
