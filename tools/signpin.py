#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
签名指纹钉住（2026-11-05）

为什么要它
──────────
web 版的做法是 `ResGuard`：把核心资源的 SHA-256 写进 Java，启动时比对，
不一致就弹「资源校验提示」。搬到 Android 上行不通 —— 原生版的"资源"都编进了
APK，改任何一个字节都必须重新签名，否则**装不上**。

所以 Android 上真正有效、而且更硬的那一层是：**钉住签名证书**。

  · 有人改了代码/资源再重新打包 → 只能用他自己的密钥签 → 证书指纹变了 → 提示
  · 有人想用原证书签 → 他没有私钥，签不出来
  · 所以"证书指纹一致"等价于"这个包是我构建出来的"

和 web 版一样，这是**软校验**：发现问题只提示，不退出。理由：万一哪天换签名
（比如以后上架换正式密钥），误报不应该把用户锁在门外。

用法
──────
    python tools/signpin.py                 # 读 release 产物，生成 IntegrityPin.kt
    python tools/signpin.py --apk x.apk
    python tools/signpin.py --keystore      # 没有 APK 时直接读 debug keystore

产物：app/src/main/java/.../data/IntegrityPin.kt（自动生成，勿手改）

说明
──────
Android 的 `apkContentsSigners[0].toByteArray()` 给的就是**证书 DER**，
对它算 SHA-256 就是这个脚本输出的值。脚本直接从 APK 的 v2 签名块里
取同一段 DER，所以两边必然一致 —— 这点很重要，否则会稳定误报。
"""
import hashlib
import io
import os
import re
import struct
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
OUT_KT = os.path.join(ROOT, "app", "src", "main", "java", "com", "xixi", "hiking",
                      "nativeapp", "data", "IntegrityPin.kt")
JDK = os.environ.get("JAVA_HOME", "C:/Users/NIU-XC/Desktop/buddy/2026-08-07-13-58-04/jdk-21.0.12")

MAGIC = b"APK Sig Block 42"
V2_ID = 0x7109871A


def read_cert_from_v2(apk_path):
    """从 APK Signing Block 里取出第一张证书的 DER。

    结构（依次）：
        [zip 内容] [APK Signing Block] [Central Directory] [EOCD]
    Signing Block 尾部 24 字节 = 块长度(8) + 魔数(16)，从 EOCD 里拿到 CD 起点就能反推。
    """
    data = io.open(apk_path, "rb").read()

    # ① 找 EOCD（End of Central Directory）：签名 0x06054b50，从尾部往前找
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise RuntimeError("不是合法 zip：找不到 EOCD")
    cd_off = struct.unpack_from("<I", data, eocd + 16)[0]

    # ② Signing Block 的尾部就在 CD 之前 24 字节处
    foot = cd_off - 24
    if foot < 0 or data[foot + 8:foot + 24] != MAGIC:
        raise RuntimeError("没有 APK Signing Block（可能只用了 v1 签名，或未签名）")
    block_size = struct.unpack_from("<Q", data, foot)[0]
    block_start = cd_off - block_size - 8

    # ③ 遍历 id-value 对，找 v2（0x7109871a）
    pos = block_start + 8
    v2 = None
    while pos < foot:
        pair_len = struct.unpack_from("<Q", data, pos)[0]
        pair_id = struct.unpack_from("<I", data, pos + 8)[0]
        if pair_id == V2_ID:
            v2 = data[pos + 12:pos + 8 + pair_len]
            break
        pos += 8 + pair_len
    if v2 is None:
        raise RuntimeError("APK 里没有 v2 签名块（v3 才有？）")

    # ④ v2 是嵌套的长度前缀结构；证书是其中一段 DER。
    #    不逐层解析 protobuf，直接找第一段「以 0x30 0x82 开头、长度自洽」的 DER 就好。
    #    这段一定在 signer 的 certificates 里，而且只有它长得像 X.509。
    for i in range(len(v2) - 4):
        if v2[i] == 0x30 and v2[i + 1] == 0x82:
            total = struct.unpack_from(">H", v2, i + 2)[0] + 4
            cert = v2[i:i + total]
            if 200 < len(cert) < 8192 and len(cert) == total:
                # 粗校验：能解析出 SEQUENCE 且结尾完整
                if cert[0] == 0x30 and total <= len(v2) - i:
                    return cert
    raise RuntimeError("v2 块里没找到证书 DER")


def sha256_hex(b):
    return hashlib.sha256(b).hexdigest()


def from_keystore():
    """兜底：直接从 debug keystore 读（APK 还没构建时用）。"""
    home = os.path.expanduser("~")
    ks = os.path.join(home, ".android", "debug.keystore")
    if not os.path.exists(ks):
        raise RuntimeError("找不到 ~/.android/debug.keystore")
    kt = os.path.join(JDK, "bin", "keytool.exe")
    if not os.path.exists(kt):
        kt = os.path.join(JDK, "bin", "keytool")
    out = subprocess.run(
        [kt, "-list", "-v", "-keystore", ks, "-storepass", "android",
         "-alias", "androiddebugkey"],
        capture_output=True
    ).stdout.decode("utf-8", "replace")
    m = re.search(r"SHA256:\s*([0-9A-F:]{95})", out)
    if not m:
        raise RuntimeError("keytool 输出里没有 SHA256 指纹")
    return m.group(1).replace(":", "").lower()


def write_kt(digest, source):
    kt = '''package com.xixi.hiking.nativeapp.data

/**
 * ★自动生成，勿手改 —— `python tools/signpin.py`（2026-11-05）
 *
 * 内容：官方安装包的**签名证书 SHA-256**（小写、无冒号）。
 * 来源：%s
 *
 * 用途：启动时软校验（[Integrity]）。签名变了说明这个包不是我用这套代码构建的，
 * 只提示、不退出 —— 和网页版 ResGuard 的"资源校验提示"同一个态度。
 *
 * 换签名（比如以后上架换正式密钥）必须重新跑 signpin 生成这个文件，
 * 否则老用户升级后会看到"不是官方安装包"的误报。
 */
object IntegrityPin {
    const val CERT_SHA256 = "%s"
}
''' % (source, digest)
    io.open(OUT_KT, "w", encoding="utf-8").write(kt)
    print(f"✓ 已生成 {os.path.relpath(OUT_KT, ROOT)}")
    print(f"  证书 SHA-256 = {digest}")


def current_pin():
    """读已生成的 IntegrityPin.kt 里的值（没生成返回 None）"""
    if not os.path.exists(OUT_KT):
        return None
    m = re.search(r'CERT_SHA256\s*=\s*"([0-9a-f]{64})"', io.open(OUT_KT, encoding="utf-8").read())
    return m.group(1) if m else None


def verify(apk_path):
    """发版后校验：APK 的签名 == 代码里钉的指纹。

    这条不做的话，一旦换了签名（或 pin 生成错），装上去会**每个用户**都看到
    「安装包已被改动」—— 而且只在真机上发现，测试跑不出来。
    """
    if not os.path.exists(apk_path):
        print(f"✗ 没有产物：{apk_path}")
        return 1
    pin = current_pin()
    if not pin:
        print("✗ IntegrityPin.kt 里没有 CERT_SHA256（先跑 python tools/signpin.py）")
        return 1
    actual = sha256_hex(read_cert_from_v2(apk_path))
    if actual == pin:
        print(f"✓ 签名指纹与代码一致：{actual[:16]}…")
        return 0
    print("✗ 签名指纹不一致 —— 装上去会误报「安装包已被改动」")
    print(f"    代码里：{pin}")
    print(f"    APK 里：{actual}")
    print("  修法：python tools/signpin.py 重新生成，然后重新打包")
    return 1


def main():
    args = sys.argv[1:]
    apk = APK
    if "--apk" in args:
        apk = args[args.index("--apk") + 1]

    if "--verify" in args:
        return verify(apk)

    if "--keystore" in args or not os.path.exists(apk):
        if not os.path.exists(apk):
            print(f"（没有 {os.path.relpath(apk, ROOT)}，改用 keystore）")
        digest = from_keystore()
        write_kt(digest, "debug.keystore（keytool）")
        return 0

    cert = read_cert_from_v2(apk)
    digest = sha256_hex(cert)
    write_kt(digest, f"{os.path.basename(apk)} 的 v2 签名块")

    # 自校验：如果 keystore 在，比对一下，防止解析错位导致钉错值
    try:
        ks_digest = from_keystore()
        if ks_digest == digest:
            print("  ✓ 与 debug.keystore 的指纹一致（解析正确）")
        else:
            print("  ⚠ 与 debug.keystore 不一致 —— 可能用了别的签名配置，请确认")
            print(f"    keystore: {ks_digest}")
    except Exception as e:
        print(f"  （跳过 keystore 交叉校验：{e}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
