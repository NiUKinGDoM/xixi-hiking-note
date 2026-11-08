#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GitHub 发布工具（2026-11-05）

为什么需要它
────────────
用户想把原生版当作网页版仓库里的**测试版**：先发 pre-release，
自己在手机上验几天，没问题再"提升"为正式版 —— 而不是每次都直接推给所有人。

GitHub 的 pre-release 语义正好对上：
    · `prerelease = true`   → 不在 `/releases/latest` 里，普通用户看不到
    · 但 release **列表**里能查到 → App 开了「测试版通道」才收得到
    · 把 prerelease 改成 false 就是"提升为正式版"，资产文件不用重传

这台机器没装 gh CLI，所以直接用 REST API（urllib，零依赖）。

用法
──────
    # 看现有发布，确认哪个是正式版、哪个是测试版
    python tools/ghrelease.py list

    # 发一个测试版（默认 prerelease=true）
    python tools/ghrelease.py upload --tag v1.0.9.11 \
        --apk "C:/Users/NIU-XC/Desktop/XiXi原生版-v1.0.9.11-xxx.apk" \
        --name "原生版 v1.0.9.11" --notes-file docs/last-notes.md

    # 验证没问题 → 提升为正式版（资产不重传，只改标记）
    python tools/ghrelease.py promote v1.0.9.11

    # 发错了想退回测试版 / 删掉
    python tools/ghrelease.py demote v1.0.9.11
    python tools/ghrelease.py delete v1.0.9.11 --yes

Token
──────
需要一个有 `repo` 权限的 GitHub token（classic PAT 即可）：
    · 环境变量：GITHUB_TOKEN 或 GH_TOKEN
    · 或写到仓库根目录的 `.gh_token`（一行，不要提交/外传）

为什么脚本要拦一道"标签必须是 v 开头四段版本号"
──────────────────────────────────────────────
App 的更新检查是**逐段比数字**（1.0.9.11 > 1.0.9.10）。标签写错（比如 `v1.0.9`），
比较逻辑会把它当成老版本，用户永远收不到更新 —— 而且没有任何报错。
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# ★2026-11-09 应用独立仓库：NiUKinGDoM/xixi-hiking-note
#   老的 NiUKinGDoM/xixi-hiking 是**网页版**的仓库，App 只在过渡期往那边补发一次
#   （原因见 app/src/.../data/Updater.kt 的 API_LEGACY 注释：老版本 App 硬编码了旧地址）。
#   发到旧仓库时显式传 --repo NiUKinGDoM/xixi-hiking。
DEFAULT_REPO = "NiUKinGDoM/xixi-hiking-note"
LEGACY_REPO = "NiUKinGDoM/xixi-hiking"
API = "https://api.github.com"
UPLOAD = "https://uploads.github.com"
TAG_RX = re.compile(r"^v\d+\.\d+\.\d+\.\d+$")


def token():
    for k in ("GITHUB_TOKEN", "GH_TOKEN"):
        v = os.environ.get(k)
        if v:
            return v.strip()
    f = os.path.join(ROOT, ".gh_token")
    if os.path.exists(f):
        return io_read(f).strip()
    print("✗ 没有 GitHub token。")
    print("  设环境变量 GITHUB_TOKEN=ghp_xxx，或把 token 写进 .gh_token（一行）。")
    print("  生成：GitHub → Settings → Developer settings → Personal access tokens → repo 权限")
    sys.exit(2)


def io_read(p):
    import io
    return io.open(p, encoding="utf-8").read()


def call(method, url, tok, data=None, content_type="application/json", raw=None):
    body = None
    if raw is not None:
        body = raw
    elif data is not None:
        body = json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Authorization", "Bearer " + tok)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    if body is not None:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            txt = r.read().decode("utf-8", "replace")
            return r.status, (json.loads(txt) if txt.strip() else {})
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8", "replace")
        try:
            j = json.loads(txt)
        except Exception:
            j = {"raw": txt[:400]}
        return e.code, j


def repo_of(args):
    return args[args.index("--repo") + 1] if "--repo" in args else DEFAULT_REPO


def find_release(tok, repo, tag):
    st, arr = call("GET", f"{API}/repos/{repo}/releases?per_page=100", tok)
    if st != 200 or not isinstance(arr, list):
        return None
    for r in arr:
        if r.get("tag_name") == tag:
            return r
    return None


def cmd_list(repo):
    tok = token()
    st, arr = call("GET", f"{API}/repos/{repo}/releases?per_page=100", tok)
    if st != 200:
        print(f"✗ 读取失败 {st}: {arr}")
        return 1
    print(f"══ {repo} 的发布（新→旧）══")
    for r in arr:
        kind = "草稿" if r.get("draft") else ("测试版" if r.get("prerelease") else "正式版")
        apks = [a["name"] for a in r.get("assets", []) if a["name"].lower().endswith(".apk")]
        native = [a for a in apks if "原生" in a or "native" in a.lower()]
        mark = "★原生" if native else "  网页"
        print(f"  {mark}  {kind}  {r['tag_name']:<14} APK {len(native)} 个  {r.get('name','')[:28]}")
    print()
    print("说明：App 默认只认「正式版」；开了测试版通道才看得到「测试版」。")
    return 0


def cmd_upload(repo, args):
    tok = token()
    tag = args[args.index("--tag") + 1] if "--tag" in args else ""
    apk = args[args.index("--apk") + 1] if "--apk" in args else ""
    if not TAG_RX.match(tag):
        print(f"✗ 标签必须像 v1.0.9.11（四段数字），你给的是「{tag}」")
        print("  理由：App 逐段比数字，标签格式不对就永远收不到更新，而且不会报错。")
        return 2
    if not os.path.exists(apk):
        print(f"✗ 找不到 APK：{apk}")
        return 2

    name = args[args.index("--name") + 1] if "--name" in args else f"原生版 {tag}"
    notes = ""
    if "--notes-file" in args:
        notes = io_read(args[args.index("--notes-file") + 1])
    elif "--notes" in args:
        notes = args[args.index("--notes") + 1]

    prerelease = "--official" not in args       # 默认就是测试版

    exist = find_release(tok, repo, tag)
    if exist:
        print(f"（{tag} 已存在，改成更新这条）")
        rid = exist["id"]
        st, j = call("PATCH", f"{API}/repos/{repo}/releases/{rid}", tok, {
            "name": name, "body": notes, "prerelease": prerelease, "draft": False
        })
        if st != 200:
            print(f"✗ 更新失败 {st}: {j}")
            return 1
    else:
        st, j = call("POST", f"{API}/repos/{repo}/releases", tok, {
            "tag_name": tag, "name": name, "body": notes,
            "prerelease": prerelease, "draft": False
        })
        if st not in (200, 201):
            print(f"✗ 创建失败 {st}: {j}")
            return 1
        rid = j["id"]

    # 上传资产（已存在同名资产时先删，避免 422）
    st, rel = call("GET", f"{API}/repos/{repo}/releases/{rid}", tok)
    for a in rel.get("assets", []):
        if a["name"] == os.path.basename(apk):
            call("DELETE", f"{API}/repos/{repo}/releases/assets/{a['id']}", tok)
            print(f"  已删同名旧资产 {a['name']}")

    data = open(apk, "rb").read()
    fname = os.path.basename(apk)
    st, j = call(
        "POST", f"{UPLOAD}/repos/{repo}/releases/{rid}/assets?name={urllib.parse.quote(fname)}",
        tok, raw=data, content_type="application/vnd.android.package-archive"
    )
    if st not in (200, 201):
        print(f"✗ 上传失败 {st}: {j}")
        return 1
    print(f"✓ 已上传 {fname}（{len(data)/1048576:.1f} MB）")
    print(f"✓ 这是一个「{'测试版' if prerelease else '正式版'}」：{tag}")
    if prerelease:
        print("   验证没问题后：python tools/ghrelease.py promote " + tag)
    return 0


def cmd_promote(repo, tag, official):
    tok = token()
    r = find_release(tok, repo, tag)
    if not r:
        print(f"✗ 找不到 {tag}")
        return 1
    st, j = call("PATCH", f"{API}/repos/{repo}/releases/{r['id']}", tok, {
        "prerelease": not official,
        # make_latest：正式版要成为 latest（App 的"检测更新"按列表第一条算，
        # 但网页版可能用 /latest，两边都照顾到）
        "make_latest": "true" if official else "false",
    })
    if st != 200:
        print(f"✗ 失败 {st}: {j}")
        return 1
    print(f"✓ {tag} 已改为{'正式版' if official else '测试版'}")
    return 0


def cmd_delete(repo, tag, args):
    if "--yes" not in args:
        print("要真的删掉这条发布，加 --yes")
        return 2
    tok = token()
    r = find_release(tok, repo, tag)
    if not r:
        print(f"✗ 找不到 {tag}")
        return 1
    st, _ = call("DELETE", f"{API}/repos/{repo}/releases/{r['id']}", tok)
    print(("✓ 已删除 " if st in (204, 200) else f"✗ 失败 {st} ") + tag)
    return 0 if st in (204, 200) else 1


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 0
    repo = repo_of(args)
    cmd = args[0]
    if cmd == "list":
        return cmd_list(repo)
    if cmd == "upload":
        return cmd_upload(repo, args)
    if cmd == "promote":
        return cmd_promote(repo, args[1], official=True)
    if cmd == "demote":
        return cmd_promote(repo, args[1], official=False)
    if cmd == "delete":
        return cmd_delete(repo, args[1], args)
    print(__doc__)
    return 0


if __name__ == "__main__":
    import urllib.parse  # noqa: E402  (放在这里避免顶部顺序问题)
    sys.exit(main())
