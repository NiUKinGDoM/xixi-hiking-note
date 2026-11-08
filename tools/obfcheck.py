#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
混淆体检（2026-11-05）

背景
────
一开 R8，资源名会被改成 `res/Vk.ttf` 这种短名，类名会变成 `q2.J`。
于是"东西还在不在"没法靠文件名看了，只能靠两个副产品：

    app/build/outputs/mapping/release/resources.txt   R8 的资源保留清单
    app/build/outputs/mapping/release/mapping.txt     类/方法 改名对照表

这个脚本做四件事：

1. **关键资源还在吗**：从 resources.txt 里找 `@pkg:drawable/share_bg : reachable=true`，
   逐项断言。被误删的话，表现是"分享卡背景空白 / 收款码打不开 / 字体退回黑体"，
   而且要等用户点到才发现。
2. **靠名字工作的类还在吗**：Room 用反射按名字加载 `AppDatabase_Impl`，
   清单里声明的 Activity 也不能改名。这两条一旦被混淆，**打开就崩**。
3. **mapping.txt 归档**：混淆后的崩溃堆栈是 `a.a.a(SourceFile:42)`，
   没有对应版本的 mapping 就永远还原不回来。每次发版按版本号存一份。
4. **体积对比**：和上一版比，掉了多少、dex 多少。

用法
──────
    python tools/obfcheck.py                    # 检查当前 release 产物
    python tools/obfcheck.py --baseline x.apk   # 顺便和某个旧包比体积
    python tools/obfcheck.py --retrace crash.txt  # 用最新 mapping 还原堆栈
"""
import io
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
MAP_DIR = os.path.join(ROOT, "app", "build", "outputs", "mapping", "release")
RES_TXT = os.path.join(MAP_DIR, "resources.txt")
MAPPING = os.path.join(MAP_DIR, "mapping.txt")
ARCHIVE = os.path.join(ROOT, "_debug", "mapping")

# ── 必须活下来的资源（每一条都对应一个"用户会点到"的功能）──
REQUIRED_RES = [
    ("drawable", "share_bg", "分享卡背景图"),
    ("drawable", "support_qr_wechat", "支持作者·微信收款码"),
    ("drawable", "support_qr_alipay", "支持作者·支付宝收款码"),
    ("font", "app_serif_regular", "宋体（标题字体，缺了会退回黑体）"),
    ("font", "app_serif_bold", "宋体粗体"),
    ("mipmap", "ic_launcher", "桌面图标"),
    ("xml", "file_paths", "FileProvider 路径（分享照片/诊断报告要用）"),
]

# ── 必须保持原名的类（改了名运行时找不到）──
REQUIRED_CLASSES = [
    "com.xixi.hiking.nativeapp.data.AppDatabase_Impl",  # Room 反射按名字加载
    "com.xixi.hiking.nativeapp.MainActivity",           # 清单声明
    "com.xixi.hiking.nativeapp.data.RecordEntity",      # Room 实体
    "com.xixi.hiking.nativeapp.data.PlannedTripEntity",
]

OK, BAD = "✓", "✗"


def version_name():
    g = io.open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    vc = int(re.search(r"val\s+vc\s*=\s*(\d+)", g).group(1))
    i = vc - 1
    return "1.%d.%d.%d" % ((i // 121) % 11, (i // 11) % 11, i % 11)


def check_resources(problems):
    if not os.path.exists(RES_TXT):
        problems.append("找不到 resources.txt —— 说明这次构建没开 minify，或者构建还没跑")
        return 0
    text = io.open(RES_TXT, encoding="utf-8").read()
    print("── 关键资源 ──")
    for kind, name, why in REQUIRED_RES:
        # resources.txt 里的格式：@com.xixi.hiking.nativeapp:drawable/share_bg : reachable=true
        pat = re.compile(r"@[^:\s]+:%s/%s\s*:\s*reachable=(\w+)" % (kind, re.escape(name)))
        m = pat.search(text)
        if m and m.group(1) == "true":
            print(f"  {OK} {kind}/{name}  —— {why}")
        else:
            print(f"  {BAD} {kind}/{name}  —— {why}")
            problems.append(f"资源被删或不可达：{kind}/{name}（{why}）")
    return len(REQUIRED_RES)


def check_classes(problems):
    if not os.path.exists(MAPPING):
        problems.append("找不到 mapping.txt —— 无法验证类是否保留，也无法还原崩溃堆栈")
        return 0
    text = io.open(MAPPING, encoding="utf-8", errors="ignore").read()
    print()
    print("── 必须保持原名的类 ──")
    for cls in REQUIRED_CLASSES:
        # 映射行：com.x.y.Z -> com.x.y.Z:   （右边同名 = 没被改名）
        pat = re.compile(r"^%s -> (\S+):" % re.escape(cls), re.M)
        m = pat.search(text)
        if not m:
            print(f"  ? {cls}  —— 映射表里没有（可能被整类删除了）")
            problems.append(f"类在映射表里找不到：{cls}")
        elif m.group(1) == cls:
            print(f"  {OK} {cls}")
        else:
            print(f"  {BAD} {cls} → {m.group(1)}（被改名，运行时会找不到）")
            problems.append(f"类被改名：{cls} → {m.group(1)}")
    return len(REQUIRED_CLASSES)


def check_apk(baseline):
    if not os.path.exists(APK):
        print(f"{BAD} 没有产物：{APK}")
        return 1
    size = os.path.getsize(APK)
    z = zipfile.ZipFile(APK)
    dex = sum(z.getinfo(n).file_size for n in z.namelist() if n.endswith(".dex"))
    print("── 产物 ──")
    print(f"  APK {size / 1048576:.1f} MB（压缩后） · dex {dex / 1048576:.1f} MB · "
          f"条目 {len(z.namelist())}")
    if baseline and os.path.exists(baseline):
        b = os.path.getsize(baseline)
        d = (size - b) / b * 100
        print(f"  对比 {os.path.basename(baseline)}：{b / 1048576:.1f} MB → "
              f"{size / 1048576:.1f} MB（{d:+.1f}%）")
    # 启动 Activity 的名字必须能在 dex 里找到（改名/删掉 = 打开就崩）
    # ★dex 里的类型描述符用斜杠：`Lcom/xixi/.../MainActivity;`，不是点号。
    #   （第一版这里写成了点号，明明保留了也报"查不到"，属于验证脚本自己错。）
    main_cls = "com/xixi/hiking/nativeapp/MainActivity"
    hit = any(main_cls in z.read(n).decode("latin-1", "ignore")
              for n in z.namelist() if n.endswith(".dex"))
    print(f"  {OK if hit else BAD} dex 里能查到 {main_cls.replace(chr(47), chr(46))}")
    return 0 if hit else 1


def archive_mapping():
    if not os.path.exists(MAPPING):
        return None
    os.makedirs(ARCHIVE, exist_ok=True)
    v = version_name()
    # ★gzip：一份 mapping 38MB，逐版累积很快上百 MB；压缩后约 4MB，还原时按需解压
    import gzip
    dst = os.path.join(ARCHIVE, f"mapping-v{v}.txt.gz")
    with io.open(MAPPING, "rb") as src, gzip.open(dst, "wb", compresslevel=9) as out:
        shutil.copyfileobj(src, out)
    mb = os.path.getsize(dst) / 1048576
    print()
    print("── mapping 归档 ──")
    print(f"  {OK} {os.path.relpath(dst, ROOT)}（{mb:.1f} MB）")
    print(f"    以后还原这一版的崩溃堆栈：python tools/obfcheck.py --retrace 崩溃.txt --mapping {os.path.relpath(dst, ROOT)}")
    return dst


def retrace(path, mapping_path):
    """极简还原：把堆栈里的 `a.a.a` 换回真实类名。

    不做完整的行号映射（那需要 proguard-retrace.jar），只解类名 ——
    定位问题时 90% 的信息来自"是哪个类崩的"，类名够了。
    """
    if not os.path.exists(mapping_path):
        print(f"{BAD} 没有 mapping：{mapping_path}")
        return 1
    def _open_text(path):
        if path.endswith(".gz"):
            import gzip
            return io.TextIOWrapper(gzip.open(path, "rb"), encoding="utf-8", errors="ignore")
        return io.open(path, encoding="utf-8", errors="ignore")

    cls_map = {}
    for ln in _open_text(mapping_path):
        m = re.match(r"^(\S+) -> (\S+):$", ln.rstrip("\n"))
        if m:
            cls_map[m.group(2)] = m.group(1)
    out = []
    for ln in io.open(path, encoding="utf-8", errors="ignore"):
        for obf in sorted(cls_map, key=len, reverse=True):
            if obf and obf in ln:
                ln = ln.replace(obf, cls_map[obf])
        out.append(ln)
    res = "".join(out)
    print(res[:4000])
    dst = os.path.splitext(path)[0] + ".retraced.txt"
    io.open(dst, "w", encoding="utf-8").write(res)
    print(f"\n{OK} 已写入 {dst}")
    return 0


def main():
    args = sys.argv[1:]
    if "--retrace" in args:
        p = args[args.index("--retrace") + 1]
        mp = MAPPING
        if "--mapping" in args:
            mp = os.path.join(ROOT, args[args.index("--mapping") + 1])
        return retrace(p, mp)

    baseline = None
    if "--baseline" in args:
        baseline = args[args.index("--baseline") + 1]

    problems = []
    print("══ 混淆体检 ══")
    n_res = check_resources(problems)
    n_cls = check_classes(problems)
    print()
    rc_apk = check_apk(baseline)
    archive_mapping()

    print()
    if problems or rc_apk != 0:
        print(f"{BAD} {len(problems) + (1 if rc_apk else 0)} 个问题：")
        for p in problems:
            print("   · " + p)
        return 1
    print(f"{OK} 通过：{n_res} 个关键资源 + {n_cls} 个关键类都在，mapping 已归档")
    return 0


if __name__ == "__main__":
    sys.exit(main())
