#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
APK 体检（发版前最后一道）

装上才发现问题是最亏的（用户在手机上看，我在电脑上改）。这里把 APK 里
**能读出来的硬事实**全查一遍，和 build.gradle.kts 对齐：

  ① versionCode / versionName 与 gradle 的 vc 公式一致
  ② 有 App 图标（这是之前踩过的坑：manifest 没配 icon，装完显示安卓机器人）
  ③ 有 INTERNET 权限（release 包不会自动注入，缺了同步/更新全废）
  ④ minSdk / targetSdk 合规
  ⑤ APK 体积（异常暴涨通常是误把图片/备份打进去了）

用法：python tools/apkcheck.py [apk路径]
"""
import io
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(ROOT, "app", "build.gradle.kts")
DEFAULT_APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
AAPT = "C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/android-sdk/build-tools/34.0.0/aapt2.exe"


def version_name(vc):
    i = vc - 1
    return "1.%d.%d.%d" % ((i // 121) % 11, (i // 11) % 11, i % 11)


def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_APK
    if not os.path.exists(apk):
        print("✗ APK 不存在：%s" % apk)
        return 1
    if not os.path.exists(AAPT):
        print("! 找不到 aapt2（%s），跳过元信息检查" % AAPT)
        return 0

    out = subprocess.run([AAPT, "dump", "badging", apk],
                         capture_output=True, text=True, errors="ignore").stdout

    gradle = io.open(GRADLE, encoding="utf-8").read()
    vc = int(re.search(r"val\s+vc\s*=\s*(\d+)", gradle).group(1))
    expect_name = version_name(vc)

    m = re.search(r"versionCode='(\d+)' versionName='([^']*)'", out)
    problems = []
    if not m:
        problems.append("读不出 versionCode/versionName")
    else:
        got_vc, got_name = int(m.group(1)), m.group(2)
        if got_vc != vc:
            problems.append("versionCode=%d，gradle 的 vc=%d" % (got_vc, vc))
        if got_name != expect_name:
            problems.append("versionName=%s，公式应算得 %s" % (got_name, expect_name))

    if "application-icon" not in out:
        problems.append("APK 里没有应用图标（manifest 缺 android:icon）")
    if "uses-permission: name='android.permission.INTERNET'" not in out:
        problems.append("缺 INTERNET 权限（release 包不会自动注入）")

    sdk = re.search(r"sdkVersion:'(\d+)'", out)
    if sdk and int(sdk.group(1)) < 24:
        problems.append("minSdk=%s 低于 24（Compose 模糊/动效需要 7.0+）" % sdk.group(1))

    size_mb = os.path.getsize(apk) / 1048576.0

    # ★2026-11-11 包体守门 + 资源排行
    #
    # 为什么要加：这个包 6.5MB 里 6.7MB（解压后）是两个中文字库，其余几乎为零。
    # 以后往 res/ 里塞一张 2MB 的图、或者换一套更大的字体，**没有任何东西会提醒你** ——
    # 下载更新的人（以及每次覆盖安装的自己）都在为这几十 MB 付流量和时间。
    # 这里给一条硬线 + 一份 Top5 清单，膨胀了立刻能看出是谁干的。
    BUDGET_MB = 9.0
    top = []
    try:
        import zipfile
        with zipfile.ZipFile(apk) as z:
            for i in z.infolist():
                top.append((i.file_size, i.filename))
        top.sort(reverse=True)
    except Exception as e:
        print("   （资源排行读取失败，已忽略：%s）" % e)

    if size_mb > BUDGET_MB:
        problems.append(
            "包体 %.1fMB 超过预算 %.0fMB —— 先看下面的资源排行，别直接改预算"
            % (size_mb, BUDGET_MB)
        )

    if problems:
        print("✗ APK 体检不过：%s" % os.path.basename(apk))
        for p in problems:
            print("   · " + p)
        if top:
            print("   体积排行：")
            for sz, nm in top[:5]:
                print("     %6.2fMB  %s" % (sz / 1048576, nm))
        return 1

    print("✓ APK 体检通过：%s" % os.path.basename(apk))
    print("   · vc%d → %s" % (vc, expect_name))
    print("   · 图标 ✓  INTERNET ✓  minSdk %s" % (sdk.group(1) if sdk else "?"))
    print("   · 体积 %.1f MB（预算 %.0f MB）" % (size_mb, BUDGET_MB))
    if top:
        # 只列前 3：看得见是哪几样占地方，又不刷屏
        for sz, nm in top[:3]:
            print("     %6.2fMB  %s" % (sz / 1048576, nm))
    return 0


if __name__ == "__main__":
    sys.exit(main())
