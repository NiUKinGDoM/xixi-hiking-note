#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
真机日志抓取（2026-11-05）

为什么需要它
────────────
"点了没反应""弹窗自己关了""打开就白屏"这类反馈，只靠描述定位不到根因 ——
有一次真凶是 HorizontalPager 抢手势，光看界面怎么都想不到。

这个脚本把三类信号从 logcat 里捞出来并分类，交给人直接看：

  FATAL    未捕获异常（含堆栈），真崩溃
  ANR      主线程卡死（应用无响应），以及 ANR 的 trace 摘要
  SOFT     应用自己记的软错误（tag=hiking 的 Log.w/Log.e —— CrashGuard / Trace /
           各种 catch 都会往这里写，是"没崩但不对劲"的唯一线索）
  GLASS    液态玻璃 / RenderEffect 降级或失败（真机上模糊、折射出问题时看这条）

用法
──────
    python tools/logcat.py              # 抓当前缓冲区，分析
    python tools/logcat.py --clear      # 先清空，然后让你在手机上复现
    python tools/logcat.py --wait 20    # 清空后等 20 秒（配合复现）
    python tools/logcat.py --raw        # 连原始行一起打（默认只打摘要）

产物
──────
    _debug/logcat-<时间戳>.txt          完整原始日志（发给别人排查用）

前提
──────
电脑要有 adb，手机开「开发者选项 → USB 调试」并授权。
脚本会自己找 adb（PATH / ANDROID_HOME / 常见 SDK 目录），找不到就告诉你怎么装。
"""
import os
import re
import shutil
import subprocess
import sys
import time
import io

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = "com.xixi.hiking.nativeapp"
OUT_DIR = os.path.join(ROOT, "_debug")

ADB_CANDIDATES = [
    "adb",
    os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb.exe"),
    os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb"),
    r"C:\Android\platform-tools\adb.exe",
    os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
]

# 分类规则（顺序有意义：先认最严重的）
RULES = [
    ("FATAL", re.compile(r"FATAL EXCEPTION|AndroidRuntime.*E|-----( beginning of crash)")),
    ("ANR", re.compile(r"ANR in |Reason: Input dispatching timed out|Application Not Responding")),
    ("SOFT", re.compile(r"\b[WE]/(hiking|XiXi)\b|\bhiking\s*:\s")),
    ("GLASS", re.compile(r"RenderEffect|RuntimeShader|AGSL|liquid|LiquidGlass|Backdrop")),
]


def find_adb():
    for c in ADB_CANDIDATES:
        p = shutil.which(c) if os.path.sep not in c else (c if os.path.exists(c) else None)
        if p:
            return p
    return None


def run(adb, args, timeout=60):
    try:
        r = subprocess.run([adb] + args, capture_output=True, timeout=timeout)
        return r.returncode, (r.stdout or b"").decode("utf-8", "replace"), \
            (r.stderr or b"").decode("utf-8", "replace")
    except Exception as e:
        return -1, "", str(e)


def main():
    args = sys.argv[1:]
    do_clear = "--clear" in args or "--wait" in args
    raw = "--raw" in args
    wait_s = 0
    if "--wait" in args:
        wait_s = int(args[args.index("--wait") + 1])

    adb = find_adb()
    if not adb:
        print("✗ 没找到 adb。")
        print("  装法（Windows，最省事）：")
        print("      winget install --id Google.PlatformTools -e")
        print("  或者下载 Android SDK Platform-Tools 解压，把 platform-tools 加进 PATH；")
        print("  或者设置 ANDROID_HOME 指向 SDK 根目录。")
        print("  手机侧：开发者选项 → USB 调试 → 插线 → 弹窗里点「允许」。")
        return 1

    code, out, _ = run(adb, ["version"])
    if code != 0:
        print(f"✗ adb 跑不起来：{out.strip()[:200]}")
        return 1

    code, out, err = run(adb, ["devices"])
    devices = [l.split("\t")[0] for l in out.splitlines()[1:] if l.strip().endswith("device")]
    if not devices:
        print("✗ 没有已授权的设备。")
        print("  · 插线后在手机弹窗点「允许 USB 调试」")
        print("  · 无线调试：开发者选项 → 无线调试 → 配对后 adb connect <ip:port>")
        print(f"  （adb 输出：{(out + err).strip()[:300]}）")
        return 1
    dev = devices[0]
    print(f"设备：{dev}")

    # 应用是否装着 / 是否在跑（信息用，不阻塞）
    code, out, _ = run(adb, ["-s", dev, "shell", "pidof", PKG])
    pid = out.strip()
    print(f"进程：{pid if pid else '未运行（先打开 App 再抓，能抓到启动期日志）'}")

    if do_clear:
        run(adb, ["-s", dev, "logcat", "-c"])
        print("已清空日志缓冲区。")
        if wait_s:
            print(f"请在手机上复现问题，{wait_s} 秒后自动抓取…")
            time.sleep(wait_s)

    code, out, err = run(adb, ["-s", dev, "logcat", "-d", "-v", "threadtime"], timeout=120)
    if code != 0:
        print(f"✗ 抓日志失败：{err.strip()[:200]}")
        return 1

    lines = out.splitlines()
    os.makedirs(OUT_DIR, exist_ok=True)
    stamp = time.strftime("%Y%m%d_%H%M%S")
    path = os.path.join(OUT_DIR, f"logcat-{stamp}.txt")
    io.open(path, "w", encoding="utf-8").write(out)
    print(f"原始日志 {len(lines)} 行 → {os.path.relpath(path, ROOT)}")

    # ── 分类（只看和我们包相关、或者明显是崩溃的行） ──
    buckets = {"FATAL": [], "ANR": [], "SOFT": [], "GLASS": []}
    for i, ln in enumerate(lines):
        if PKG not in ln and "AndroidRuntime" not in ln and "ANR in" not in ln:
            # 软错误用 tag 认（hiking），不强制带包名
            if not re.search(r"\b[WE]/hiking\b", ln):
                continue
        for name, rx in RULES:
            if rx.search(ln):
                buckets[name].append((i, ln))
                break

    total = sum(len(v) for v in buckets.values())
    print()
    print("══ 摘要 ══")
    if total == 0:
        print("没有崩溃 / ANR / 软错误。")
        print("如果界面上确实有问题，说明它没抛异常 —— 换下面两条路：")
        print("  · 在 App 里「设置 → 诊断报告 → 分享」，里面有最近 20 步操作轨迹")
        print("  · 用 --raw 看原始日志里有没有卡顿（Choreographer skipped frames）")
    for name in ("FATAL", "ANR", "SOFT", "GLASS"):
        items = buckets[name]
        if not items:
            continue
        print()
        print(f"[{name}] {len(items)} 行")
        show = items if raw else items[:12]
        for idx, ln in show:
            print("  " + ln.strip()[:200])
        if not raw and len(items) > 12:
            print(f"  …还有 {len(items) - 12} 行，用 --raw 看全")

    # FATAL 里把第一段堆栈打出来（关键是 "Caused by" 和第一行 at）
    fatal = [ln for _i, ln in buckets["FATAL"]]
    if fatal:
        print()
        print("── 崩溃首因（前 25 行堆栈）──")
        start = next((i for i, l in enumerate(lines) if "FATAL EXCEPTION" in l), 0)
        for ln in lines[start:start + 25]:
            print("  " + ln.strip()[:200])
    return 0


if __name__ == "__main__":
    sys.exit(main())
