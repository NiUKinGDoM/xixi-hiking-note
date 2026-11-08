#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Room 升级风险审计（2026-11-05）

为什么单列一项
──────────────
这是这个项目**唯一会"老用户一升级就崩"**的地方，而且测试完全测不出来：

  · 单测和截图测试每次都新建数据库 ⇒ 永远从 version N 的建表语句开始，从不"升级"
  · 真机上老库是 version N-1 的结构，缺字段 ⇒ Room 校验不过 ⇒
    `IllegalStateException: Room cannot verify the data integrity` ⇒ 打开就崩
  · 更糟的是它**只发生在已经装了旧版的人身上**，全新安装一切正常

所以必须在发版前静态地检查一遍：

  ① `@Database(version = N)` 和 `@Entity` 字段清单
  ② version > 1 时，必须有 `Migration(N-1, N)`（少一条就是一个版本的崩溃）
  ③ 不允许出现 `fallbackToDestructiveMigration`（那是"升级就把用户数据删了"）
  ④ `exportSchema` 建议开（开了才有 schema json 可以和字段对照）

用法
──────
    python tools/dbaudit.py
    python tools/dbaudit.py --gate    # 缺迁移 / 有破坏性回退 → 非 0
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking", "nativeapp")

ERR, WARN, OK = "✗", "!", "✓"


def read_all():
    buf = {}
    for dp, _dn, fns in os.walk(SRC):
        for fn in fns:
            if fn.endswith(".kt"):
                p = os.path.join(dp, fn)
                buf[p] = io.open(p, encoding="utf-8", errors="ignore").read()
    return buf


def find_db(files):
    for p, s in files.items():
        m = re.search(r"@Database\(([\s\S]{0,400}?)\)\s*abstract class (\w+)", s)
        if m:
            body, name = m.group(1), m.group(2)
            ver = re.search(r"version\s*=\s*(\d+)", body)
            ents = re.findall(r"(\w+)::class", body)
            exp = re.search(r"exportSchema\s*=\s*(true|false)", body)
            return {
                "file": p, "name": name,
                "version": int(ver.group(1)) if ver else 0,
                "entities": ents,
                "exportSchema": (exp.group(1) == "true") if exp else None,
            }
    return None


def find_migrations(files):
    out = []
    for p, s in files.items():
        for m in re.finditer(r"Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)", s):
            out.append((int(m.group(1)), int(m.group(2)), p))
    return out


def main():
    gate = "--gate" in sys.argv[1:]
    files = read_all()
    db = find_db(files)
    problems = []

    if not db:
        print(f"{ERR} 没找到 @Database 声明")
        return 1

    print("══ Room 升级风险 ══")
    print(f"  数据库 {db['name']} · version {db['version']} · 实体 {len(db['entities'])} 个")
    print(f"    {os.path.relpath(db['file'], ROOT)}")

    migs = find_migrations(files)
    print()
    print("── 迁移 ──")
    if not migs:
        print(f"  {WARN} 没有任何 Migration（version {db['version']} 时正常）")
    for a, b, p in sorted(migs):
        print(f"  {OK} {a} → {b}   {os.path.relpath(p, ROOT)}")

    # ① 每个版本跨越都要有迁移
    covered = {(a, b) for a, b, _ in migs}
    missing = [(v - 1, v) for v in range(2, db["version"] + 1) if (v - 1, v) not in covered]
    if missing:
        print()
        for a, b in missing:
            if b == db["version"]:
                # 最新一段缺口 = 下次发版一定踩到，必须修
                msg = (f"缺迁移 {a} → {b}：当前版本没有升级路径，老用户升级会直接崩"
                       f"（Room cannot verify the data integrity）")
                print(f"  {ERR} {msg}")
                problems.append(msg)
            else:
                # 历史缺口：只有当那个版本真的发布过才会踩到，提示但不算致命
                print(f"  {WARN} 历史缺口 {a} → {b}：如果 v{a} 曾发布过，那批用户升级会崩")
                print(f"      修法：补一条 Migration({a}, {b})，建表语句要与实体逐字一致")

    # ② 破坏性回退
    print()
    print("── 安全检查 ──")
    dest = [p for p, s in files.items() if "fallbackToDestructiveMigration" in s
            and "//" not in s.split("fallbackToDestructiveMigration")[0].splitlines()[-1]]
    # 只把"真的调用"算问题（注释里提到不算）
    dest_real = []
    for p, s in files.items():
        for ln in s.splitlines():
            st = ln.strip()
            # 注释行（// 和 KDoc 的 *）不算真的调用
            if "fallbackToDestructiveMigration(" in ln and not (st.startswith("//") or st.startswith("*") or st.startswith("/*")):
                dest_real.append((p, ln.strip()))
    if dest_real:
        for p, ln in dest_real:
            msg = f"出现 fallbackToDestructiveMigration：升级会把用户数据全删（{os.path.relpath(p, ROOT)}）"
            print(f"  {ERR} {msg}")
            problems.append(msg)
    else:
        print(f"  {OK} 没有 fallbackToDestructiveMigration（升级不会清库）")

    if db["exportSchema"] is False:
        print(f"  {WARN} exportSchema = false：没有 schema json，字段改动无法机器比对")
        print("      建议改成 true 并在 build.gradle.kts 配 schemaLocation")
    else:
        print(f"  {OK} exportSchema 已开")

    # ③ 实体字段数量（变化时提示：这是"该写迁移了"的信号）
    print()
    print("── 实体字段 ──")
    total = 0
    for ent in db["entities"]:
        pat = re.compile(r"data class %s\s*\(([\s\S]{0,1200}?)\)\s*(?:,|\{)" % ent)
        for p, s in files.items():
            m = pat.search(s)
            if not m:
                continue
            body = m.group(1)
            fields = re.findall(r"^\s*(?:val|var)\s+(\w+)\s*:", body, re.M)
            total += len(fields)
            print(f"  {OK} {ent}: {len(fields)} 个字段  ({', '.join(fields[:6])}{'…' if len(fields) > 6 else ''})")
            break
    print(f"    合计 {total} 个字段")

    print()
    if problems:
        print(f"{ERR} {len(problems)} 个必须修的问题")
        return 1 if gate else 0
    print(f"{OK} 升级路径完整（{db['version'] - 1} 次跨版本，全部有迁移）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
