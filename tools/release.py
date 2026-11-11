#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
一键发版（徒步小记 Android）

一条命令做完「升版本 → 门禁 → 打包 → 体检 → 上传 Release」。

用法：
    python tools/release.py --note "顶栏羽化"          # 升一版 + 门禁 + 出包 + 自动发布
    python tools/release.py --no-bump --desc "重打包"  # 不升版本，重打当前版并发布
    python tools/release.py --no-publish              # 只出包，不上传（临时拿包用）
    python tools/release.py --desktop                 # 额外拷一份到桌面（默认不拷）
    python tools/release.py --build-only              # 只打包+体检，不跑门禁不上传（调试）

★2026-11-09 用户：「以后更新后，自动发布 release，发布」
  ⇒ 默认就把 upload + promote 串进来了，不用再单独敲 ghrelease。

★2026-11-09 用户：「以后更新后，自动发布 release」「不需要双仓库更新，
  只需要更新本仓库就行了」⇒ 只发 `NiUKinGDoM/xixi-hiking-note` 一个仓库。
  之前那套"过渡期同时发旧仓库"已经拆掉，旧仓库里的 App 发布也清理干净了
  （那边只留网页版）。

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

# 仓库地址以 ghrelease.py 为准（那里是发布逻辑的唯一入口），这里只是取来用
sys.path.insert(0, os.path.join(ROOT, "tools"))
try:
    from ghrelease import DEFAULT_REPO  # type: ignore
except Exception:  # 兜底：import 失败也不能让发版脚本挂掉
    DEFAULT_REPO = "NiUKinGDoM/xixi-hiking-note"

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


CHANGELOG_KT = os.path.join(
    ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp",
    "ui", "settings", "FullChangelog.kt",
)


def changelog_for(version):
    """
    从 App 内置更新日志（FullChangelog.kt）里取出某个版本的正文。

    ★2026-11-09 用户：「release 里的更新内容写好啊」——
      之前 Release 正文只有一个「更新」两个字，等于没写。
      而 App 里本来就有一份**面向用户**的日志（NativeChangelog），
      同一个版本发到 GitHub 上却什么都没有，很荒唐。

    取的是 `"v1.1.0.5" to "【优化】\\n" + "- ..."` 这样的 Kotlin 字符串拼接：
    把该版本块里的所有双引号串按顺序拼起来，再把 \\n 还原成换行。
    解析失败就返回空串（调用方会退回 --note）。
    """
    try:
        src = open(CHANGELOG_KT, encoding="utf-8").read()
    except Exception:
        return ""
    # 块边界：本版本 "vX" to 之后 → 下一个 "v" to 之前 / 列表结束
    pat = r'"v%s"\s*to\s*(.*?)(?=\n\s*"v\d|\n\s*\))' % re.escape(version)
    m = re.search(pat, src, re.S)
    if not m:
        return ""
    # 只抓双引号串（忽略中间的 + 与换行缩进）
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
    text = "".join(parts)
    # 还原 Kotlin 转义里最常见的那几个
    text = (text.replace("\\n", "\n")
                .replace('\\"', '"')
                .replace("\\\\", "\\"))
    return text.strip()


def write_version_json(tag, name, apk_url, notes, version):
    """
    写 `version.json`（仓库根）—— App 的**主**更新检查源。

    ★★2026-11-09 为什么要这个（实测结论）：
      手机直连 `api.github.com` 在国内基本不通（本机 curl 三次全是 1ms 断连，
      HTTP=000；只有走代理才通）。所以 App 里"检测更新老是失败"不是 bug，
      是网络到不了。
      ⇒ 检查更新改读 jsDelivr 上的这个静态文件（国内可直连，实测 1.3s），
        GitHub API 降级为兜底。

    文件很小（1KB），放仓库根就行，不影响 clone 体积。
    """
    import json as _json
    import datetime
    data = {
        "tag": tag,
        "version": version,
        "apk": "tuxiaoji-%s.apk" % tag,
        "url": apk_url,
        "notes": notes,
        "published": datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    p = os.path.join(ROOT, "version.json")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        _json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("✓ 已写 version.json（%s）" % tag)
    return p


def purge_jsdelivr():
    """
    让 jsDelivr 立刻重新拉 version.json。

    jsDelivr 对 `@main` 分支引用最多缓存 12 小时 —— 不清的话，发完版
    用户点"检测更新"还是旧版本，得等半天。purge 接口是公开的，一个 GET 就够。

    ★★2026-11-11 实测：**清一次不够**。同一个 version.json，三个边缘节点
    可能返回三个不同版本（cdn → v1.1.1.1 / fastly → v1.1.1.2 / gcore → v1.1.1.4），
    因为 purge 是异步传播的。所以这里连清 3 次、每次间隔 5 秒，把命中率拉高。
    清完仍可能有个别节点滞后几十分钟 —— 影响到的是"部分用户暂时看不到新版"，
    不会误报，可以接受。
    """
    url = ("https://purge.jsdelivr.net/gh/NiUKinGDoM/xixi-hiking-note"
           "@main/version.json")
    import time as _t
    for i in range(3):
        try:
            import urllib.request
            with urllib.request.urlopen(url, timeout=10) as r:
                print("✓ 已刷新 jsDelivr 缓存（第 %d 次，%s）" % (i + 1, r.status))
        except Exception as e:
            print("（第 %d 次刷新失败：%s）" % (i + 1, e))
        if i < 2:
            _t.sleep(5)


def publish(env, tag, title, apk, notes, repo):
    """
    发到指定仓库：upload（默认 pre-release）→ promote（转正式版）。

    ★★★这里**不能**用 subprocess 调 ghrelease.py —— 2026-11-09 踩过：
      Windows 上把含中文的参数（`--apk 徒步小记-v1.1.0.5-更新.apk`、`--name 徒步小记 …`）
      传给子进程，中文字符**全部丢失**，最终上传的资产名变成 `-v1.1.0.5-.apk`。
      而 App 的更新检查是**按资产名里的「徒步小记」识别安装包**的 ——
      名字一坏，用户永远收不到更新，界面上还显示"已是最新"，没有任何报错。

    ⇒ 改成**同进程调用** `ghrelease.main(argv)`：字符串原样传，不经过命令行编码转换。
      （ghrelease.main 用 sys.argv，所以这里临时改一下再还原。）

    为什么要 promote：upload 默认打 prerelease 标记，而 App 端
    `Updater.allowBeta = false` **只认正式版** —— 只 upload 不 promote，用户看不到。
    """
    print("\n── 发布到 %s ──" % repo)
    import ghrelease  # 同目录，sys.path 已在文件顶部插好
    old_argv = sys.argv
    # ★资产名用纯 ASCII：`tuxiaoji-v1.1.0.5.apk`。
    #   GitHub 会把中文资产名里的中文字符**整段剥掉**（徒步小记-v1.1.0.5-更新.apk
    #   → -v1.1.0.5-.apk），而 App 正是按资产名认安装包的 —— 名字坏了就等于永远没更新。
    #   桌面上给用户看的文件名仍然是中文，两者互不影响。
    asset = "tuxiaoji-%s.apk" % tag
    try:
        sys.argv = [
            "ghrelease", "upload",
            "--tag", tag, "--apk", apk, "--name", title,
            "--notes", notes, "--repo", repo,
            "--asset-name", asset,
        ]
        if ghrelease.main() != 0:
            print("✗ 上传失败")
            return False
        sys.argv = ["ghrelease", "promote", tag, "--repo", repo]
        if ghrelease.main() != 0:
            print("✗ 转为正式版失败（资产已上传，手动 promote 即可）")
            return False
        return True
    except SystemExit:
        # ghrelease 在没有 token 时会 sys.exit(2)，不能在发版流程里直接退出
        print("✗ 发版中止：ghrelease 退出（多半是没有 GitHub token）")
        return False
    finally:
        sys.argv = old_argv


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--note", default="")
    ap.add_argument("--desc", default="", help="APK 文件名里的描述，默认用 --note")
    ap.add_argument("--no-bump", action="store_true")
    ap.add_argument("--build-only", action="store_true")
    # ★2026-11-09 默认自动发布；这两个开关是"临时只拿包"和"过渡期收尾"用的
    ap.add_argument("--no-publish", action="store_true", help="只出包，不上传 Release")
    ap.add_argument("--desktop", action="store_true", help="额外拷一份到桌面（默认不拷，App 内直接更新）")
    ap.add_argument("--no-push", action="store_true", help="只写 version.json，不自动 git push")
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
    # ★2026-11-09 用户：「软件内可以检测更新了，拷贝一份到桌面这一步就不需要了」
    #   默认不再往桌面扔包 —— 以后直接在 App 里更新。
    #   真要手动装（给朋友、没法联网时）加 --desktop。
    if a.desktop and os.path.isdir(DESKTOP):
        shutil.copyfile(out, os.path.join(DESKTOP, os.path.basename(out)))
        print("✓ 已拷到桌面")

    # ⑥ 自动发布 Release
    #   ★2026-11-09 用户：「以后更新后，自动发布 release，发布」
    if a.no_publish:
        print("\n（--no-publish：只出包，没有上传）")
        return 0

    tag = "v" + name
    title = "徒步小记 " + tag
    # Release 正文：**先用 App 内置日志里这一版的条目**（那是面向用户写的），
    # 没有才退回 --note / 描述。★2026-11-09 用户：「release 里的更新内容写好啊」
    notes = changelog_for(name) or (a.note or desc or "更新").strip()

    ok = publish(env, tag, title, out, notes, DEFAULT_REPO)

    if not ok:
        print("\n✗ 发布没走完。APK 已经出包，可以重试：")
        print("   python tools/ghrelease.py upload --tag %s --apk \"%s\" --name \"%s\"" % (tag, out, title))
        return 1

    # ⑦ 更新 version.json —— App 的检查更新**主源**（jsDelivr），并把新版推上去
    #   为什么必须推：jsDelivr 读的是仓库里的文件，不 push 等于没更新。
    apk_asset = "tuxiaoji-%s.apk" % tag
    apk_url = "https://github.com/%s/releases/download/%s/%s" % (DEFAULT_REPO, tag, apk_asset)
    write_version_json(tag, title, apk_url, notes, name)
    purge_jsdelivr()
    if not a.no_push:
        env["GIT_TERMINAL_PROMPT"] = "0"
        if run(["git", "add", "version.json"], env=env) == 0:
            # 内容没变时 commit 会返回非 0，这不算失败
            run(["git", "commit", "-m", "版本清单：%s" % tag], env=env)
            if run(["git", "push"], env=env) != 0:
                print("⚠ version.json 已写好但没推上去 —— 手动 git push 一次，否则 App 读不到新版")

    print("\n✓ 发布完成：%s" % tag)
    return 0


if __name__ == "__main__":
    sys.exit(main())
