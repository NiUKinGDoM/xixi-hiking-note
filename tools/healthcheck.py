#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
原生版线上 + 稳定性体检（只读）—— 给每周定时任务用。

为什么单独写一个，而不是在 automation 的 prompt 里列一堆命令
──────────────────────────────────────────────────────────
web 版那份定时任务的 prompt 很长，因为「已知正常情况不要报」的降噪清单
全写在提示词里。提示词一长，每周跑的人（AI）就可能漏读、误报、
或者把环境抖动当成项目坏了 —— 等于每周制造一次假警报。

这里把判定写进代码：哪些算红、哪些算提示、哪些直接跳过，
都在下面写死。prompt 只剩「跑它 + 按输出说话」。

设计约束（★很重要）
────────────────────
1. **只读**：不改文件、不 commit、不 push、不生成任何东西。
   特别注意：绝不能调 `signpin.py` —— 那个脚本会**覆盖** IntegrityPin.kt。
   本脚本只检查那个文件「长什么样」，不重新生成它。
2. **有网没网都能出结论**：网络项失败一律标 SKIP 并说明原因，不算项目坏。
   这台机器 raw.githubusercontent.com 常年不通，api.github.com 靠 token 能通。
3. **不贴大日志**：每项只给结论一行，红项给最小修复建议。

用法
────
    python tools/healthcheck.py
    python tools/healthcheck.py --json      # 给机器读

退出码：0 = 没有红项；1 = 至少一项 FAIL。
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
sys.path.insert(0, TOOLS)

PY = sys.executable
CDN_HOSTS = ["cdn.jsdelivr.net", "fastly.jsdelivr.net", "gcore.jsdelivr.net"]
CDN_PATH = "/gh/NiUKinGDoM/xixi-hiking-note@main/version.json"
REPO = "NiUKinGDoM/xixi-hiking-note"
TAG_RX = re.compile(r"^v\d+\.\d+\.\d+\.\d+$")

RESULTS = []


def add(name, status, detail, fix=""):
    """status: OK / WARN / FAIL / SKIP"""
    RESULTS.append({"name": name, "status": status, "detail": detail, "fix": fix})


def run(cmd, timeout=180):
    """跑本地命令，只拿输出，不改任何东西"""
    try:
        p = subprocess.run(
            cmd, cwd=ROOT, capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=timeout
        )
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except subprocess.TimeoutExpired:
        return None, "超时 %ss" % timeout
    except Exception as e:
        return None, str(e)


def fetch(url, timeout=10):
    """GET，返回 (status, body)。不抛异常。"""
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "healthcheck/1.0"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, ""
    except Exception as e:
        return None, str(e)


# ═══════════════════════════════════════════════════════════
# 本地检查
# ═══════════════════════════════════════════════════════════

def check_local_version():
    """版本三处一致：build.gradle / README / 内置更新日志"""
    code, out = run([PY, os.path.join(TOOLS, "check_version.py")])
    if code == 0:
        first = next((l for l in out.splitlines() if l.strip()), "一致")
        add("版本三处一致", "OK", first.strip())
    else:
        add("版本三处一致", "FAIL", out.strip().splitlines()[-1] if out.strip() else "检查失败",
            "跑 python tools/bump.py 同步版本，或手工对齐 build.gradle / README / FullChangelog")


def check_doctor():
    """静态体检 12 项（崩溃点/气味/设计/迁移/删除确认…）

    ★Windows 下 .sh 不能当可执行文件直接跑（WinError 193），必须显式走 bash。
    """
    code, out = run(["bash", os.path.join(TOOLS, "doctor.sh")], timeout=300)
    if code is None:
        add("静态体检", "SKIP", out)
        return
    fails = []
    for line in out.splitlines():
        m = re.search(r"^\s*\S+\s+(fail)\s+(.+)$", line)
        if m:
            fails.append(m.group(2).strip())
    m = re.search(r"体检通过（(\d+) 项）", out)
    if code == 0 and m:
        # 系统审计的 WARN 是提示性质（20 条历史信号项），web 版同类项也不算失败
        warns = re.findall(r"\s+warn\s+(.+)$", out, re.M)
        note = ""
        if warns:
            note = "；%d 个提示项（非失败）" % len(warns)
        add("静态体检", "OK", "%s 项通过%s" % (m.group(1), note))
    else:
        add("静态体检", "FAIL", "；".join(fails[:4]) or out.strip()[-200:],
            "跑 bash tools/doctor.sh 看具体红项；发版门禁是 bash tools/gate.sh")


def check_git():
    """工作区是否干净、本地与远端是否齐平"""
    code, out = run(["git", "status", "--porcelain"])
    dirty = [l for l in out.splitlines() if l.strip()]
    if code != 0:
        add("工作区", "SKIP", out.strip()[:150])
    elif dirty:
        add("工作区", "WARN", "有 %d 个未提交改动" % len(dirty),
            "确认是不是上次改完忘了提交；不要在这里自动 commit")
    else:
        add("工作区", "OK", "干净")

    code, out = run(["git", "log", "-1", "--format=%H", "origin/main"])
    remote_sha = out.strip() if code == 0 else None
    code, out = run(["git", "log", "-1", "--format=%H", "HEAD"])
    local_sha = out.strip() if code == 0 else None
    if not remote_sha:
        add("远端齐平", "SKIP", "读不到 origin/main（可能是直推 URL、本地引用没更新）")
    elif remote_sha == local_sha:
        add("远端齐平", "OK", "本地 = origin/main")
    else:
        # 本项目常用 git push <URL>，本地 origin/main 可能滞后 —— 只提示
        add("远端齐平", "WARN", "本地与 origin/main 不同（若用直推 URL 属正常）",
            "先 git fetch origin 再比；确认远端确实少了才处理")


def check_pin():
    """签名 pin 只读核对：文件在、值是 64 位 hex。绝不重新生成。"""
    p = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi",
                     "hiking", "nativeapp", "data", "IntegrityPin.kt")
    if not os.path.exists(p):
        add("签名 pin", "FAIL", "IntegrityPin.kt 不存在",
            "用户升级会看到「不是官方包」；恢复该文件后再发版")
        return
    txt = open(p, encoding="utf-8", errors="replace").read()
    m = re.search(r'CERT_SHA256\s*=\s*"([0-9a-fA-F]{64})"', txt)
    if not m:
        add("签名 pin", "FAIL", "找不到 64 位 SHA-256",
            "检查 IntegrityPin.kt 是否被改坏（不要用 signpin.py 覆盖它来'修复'）")
    else:
        add("签名 pin", "OK", "指纹 %s…（只读核对，未重新生成）" % m.group(1)[:12])


def local_tag():
    p = os.path.join(ROOT, "version.json")
    try:
        return json.load(open(p, encoding="utf-8"))["tag"]
    except Exception:
        return None


# ═══════════════════════════════════════════════════════════
# 线上检查
# ═══════════════════════════════════════════════════════════

def check_cdn(tag):
    """三个 jsDelivr 节点是否都指向本地 tag。

    降噪：刚发完版节点传播有先后，只要**都不比本地新**，就算 WARN 不算 FAIL。
    真正要报警的是「某个节点比本地还新」—— 说明有人绕过流程发了版，
    或者本地落后了。
    """
    if not tag:
        add("更新清单", "FAIL", "本地 version.json 读不到")
        return
    got = {}
    unreachable = []
    for h in CDN_HOSTS:
        st, body = fetch("https://" + h + CDN_PATH)
        if st != 200:
            unreachable.append(h)
            continue
        try:
            got[h] = json.loads(body).get("tag")
        except Exception:
            unreachable.append(h)

    if not got:
        add("更新清单", "SKIP", "三个 CDN 节点都不可达（网络环境）")
        return

    stale = {h: t for h, t in got.items() if t != tag}
    if not stale:
        add("更新清单", "OK", "%d/%d 节点 = %s" % (len(got), len(CDN_HOSTS), tag))
        return

    # 有没有节点比本地还新？
    def ver_tuple(t):
        if not t or not TAG_RX.match(t):
            return (0, 0, 0, 0)
        return tuple(int(x) for x in t[1:].split("."))

    newer = {h: t for h, t in stale.items() if ver_tuple(t) > ver_tuple(tag)}
    detail = "、".join("%s=%s" % (h, t) for h, t in stale.items())
    if newer:
        add("更新清单", "FAIL", "有节点比本地新：" + detail,
            "本地 version.json 落后，或有人绕过 release.py 发了版 —— 先查 GitHub Releases")
    else:
        add("更新清单", "WARN", "节点传播滞后：" + detail + "（通常几十分钟内自愈）",
            "若超过 12 小时仍滞后，跑 purge：见 docs/PIPELINE.md 第 6 节")


def check_github_release(tag):
    """GitHub 最新正式版 = 本地 tag？资产在不在？（HEAD，不下载）"""
    try:
        import ghrelease as g
        tok = g.token()
    except Exception as e:
        add("GitHub Release", "SKIP", "token 读取失败：%s" % e)
        return
    if not tok:
        add("GitHub Release", "SKIP", "没有 GitHub token，跳过线上 Release 核对")
        return

    st, data = g.call("GET", "%s/repos/%s/releases/latest" % (g.API, REPO), tok)
    if st != 200:
        add("GitHub Release", "SKIP", "查询失败（HTTP %s，可能限速/网络）" % st)
        return

    remote = data.get("tag_name")
    if remote != tag:
        add("GitHub Release", "FAIL", "线上 latest=%s，本地=%s" % (remote, tag),
            "本地没推上去，或线上发版后没同步 version.json —— 先查 GitHub Releases 页面")
        return

    assets = [a.get("name", "") for a in data.get("assets", [])]
    apk = "tuxiaoji-%s.apk" % tag
    if apk not in assets:
        add("GitHub Release", "FAIL", "%s 已发但资产缺 %s" % (remote, apk),
            "重新上传资产；文件名必须是纯 ASCII，否则 App 识别不到")
        return
    add("GitHub Release", "OK", "latest=%s，资产 %s 在" % (remote, apk))


def check_apk_reachable(tag):
    """更新包直链能不能 HEAD 到（不下载，省流量）"""
    if not tag:
        return
    url = ("https://github.com/%s/releases/download/%s/tuxiaoji-%s.apk"
           % (REPO, tag, tag))
    try:
        req = urllib.request.Request(url, method="HEAD",
                                     headers={"User-Agent": "healthcheck/1.0"})
        with urllib.request.urlopen(req, timeout=12) as r:
            size = r.headers.get("Content-Length", "?")
            add("更新包可达", "OK", "HTTP %s，Content-Length=%s" % (r.status, size))
    except urllib.error.HTTPError as e:
        add("更新包可达", "FAIL", "HTTP %s" % e.code,
            "资产被删/权限变了；App 会下载失败")
    except Exception as e:
        add("更新包可达", "SKIP", "网络不可达：%s" % str(e)[:80])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()

    tag = local_tag()
    check_local_version()
    check_doctor()
    check_git()
    check_pin()
    check_cdn(tag)
    check_github_release(tag)
    check_apk_reachable(tag)

    if a.json:
        print(json.dumps({"tag": tag, "results": RESULTS}, ensure_ascii=False, indent=2))
    else:
        print("════ 原生版线上 + 稳定性体检（只读）════")
        if tag:
            print("本地版本：%s\n" % tag)
        for r in RESULTS:
            print("  %-4s %-16s %s" % (
                r["status"], r["name"], r["detail"]))
            if r["status"] == "FAIL" and r["fix"]:
                print("       → 建议：%s" % r["fix"])
        n = {}
        for r in RESULTS:
            n[r["status"]] = n.get(r["status"], 0) + 1
        print("\n  合计 %s" % " / ".join("%s %d" % (k, v) for k, v in sorted(n.items())))
        fails = n.get("FAIL", 0)
        if fails:
            print("✗ 有 %d 项红，需要人工确认" % fails)
        elif n.get("WARN"):
            print("✓ 无红项（有提示项，通常不用管）")
        else:
            print("✓ 全绿")

    return 1 if any(r["status"] == "FAIL" for r in RESULTS) else 0


if __name__ == "__main__":
    sys.exit(main())
