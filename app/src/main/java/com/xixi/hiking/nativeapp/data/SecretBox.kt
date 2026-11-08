package com.xixi.hiking.nativeapp.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * ★★敏感短字符串加密（WebDAV 应用密码）
 *
 * 审计发现：`WebDavClient.save()` 直接 `putString("pass", c.pass)` —— 密码**明文**
 * 躺在应用私有目录的 shared_prefs XML 里。虽然不 root 读不到，
 * 但：① 备份/迁移工具能整目录拷走；② 与设置页"加密存储"的文案不符。
 *
 * 这里用 Android Keystore 里的 AES-GCM 密钥加密（密钥不出 TEE/StrongBox，
 * 即使拿到 xml 也解不开）。不引入新依赖。
 *
 * 兼容：老数据是明文，[decrypt] 遇到没有 `enc1:` 前缀就原样返回；
 * 下次保存时自动升级成密文。
 */
object SecretBox {

    private const val ALIAS = "xixi.webdav.v1"
    private const val PREFIX = "enc1:"

    /** 降级明文的标记（见 [encrypt] 注释） */
    private const val PREFIX_PLAIN = "enc0:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    /**
     * 加密；空串原样返回。
     *
     * ★2026-11-05 改：异常降级时**留一个明文标记** `xk0:`。
     *
     *   原来的实现是"加密失败就原样返回明文"—— 数据不会丢，但**没有任何痕迹**：
     *   用户在设置页看到的是"凭据加密存储"，实际躺在 SharedPreferences 里是明文，
     *   连诊断报告都发现不了。这类"静默降级"是最难查的一种。
     *
     *   现在三种形态可以区分：
     *     enc1:iv:body  已加密（正常）
     *     enc0:明文     明确标记的明文（Keystore 不可用时的降级，可被诊断发现）
     *     无前缀        老版本遗留明文（兼容读，下次保存自动升级成 enc1）
     */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val body = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            PREFIX + iv + ":" + body
        } catch (t: Throwable) {
            android.util.Log.w("hiking", "凭据加密不可用，降级为标记明文", t)
            PREFIX_PLAIN + plain
        }
    }

    /** 这条存储值是不是"明确降级的明文"（诊断报告用） */
    fun isPlaintext(stored: String): Boolean = stored.startsWith(PREFIX_PLAIN)

    /** 解密；兼容历史明文（无前缀）。解不开返回空串，由调用方当作"没配密码"。 */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        if (stored.startsWith(PREFIX_PLAIN)) return stored.removePrefix(PREFIX_PLAIN)
        if (!stored.startsWith(PREFIX)) return stored   // 老版本遗留明文
        return try {
            val parts = stored.removePrefix(PREFIX).split(":")
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val body = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(),
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (t: Throwable) {
            ""
        }
    }
}
