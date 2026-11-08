package com.xixi.hiking.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.ui.settings.LegalClauseCard
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState

/**
 * 条款版本 —— 改条款必改这个值，用户会被重新征求同意。
 *
 * ★2026-11-07：应用正式定名「徒步小记」、隐私政策与免责声明按当前版本重写
 * （权限清单对齐 Manifest、崩溃诊断改为"本机生成不自动上传"），
 * 因此版本号跟着跳到今天 —— 老用户下次打开会重新看到一次同意弹窗，这是政策要求的。
 */
const val LEGAL_VERSION = "2026-11-07"

/**
 * 使用前确认 —— ★★2026-11-04 按 Web `showLegalConsentModal` 重做。
 *
 * 用户原话：「首次进应用的隐私政策说明和免责声明确认，也做成和 web 版一样的，
 * 这次更新完让使用者重新确认」。
 *
 * ── Web 到底是什么样 ────────────────────────────────────────
 *   它是一个**弹窗**（不是整页）：
 *     · 标题：verified_user + 「使用前请确认」
 *     · 第一张条文卡（shield）「关于你的数据」：纯本地、没有服务器、唯一联网是检查更新
 *     · 第二张条文卡（description）「请先阅读以下条款」：正文里嵌两个可点的
 *       《隐私政策》《免责声明》
 *     · 一个**必须勾选**的复选框：「我已阅读并同意《隐私政策》与《免责声明》」
 *     · 脚注：「会记下同意时间」
 *     · 按钮：「暂不同意」/「同意并继续」；没勾就点同意 → 复选框描红 + 提示，不放行
 *
 * ── 和上一版的区别 ──────────────────────────────────────────
 *   上一版是我自己编的整页：一个绿色大卡「你的数据只在你自己的手机上」+ 两条链接行 +
 *   一个默认就能点的「同意并开始使用」—— **没有勾选动作**，等于没有"明确同意"的痕迹，
 *   法务上站不住；样式也和 web 完全是两套。现在整块换成条形卡 + 勾选的弹窗形态。
 */
@Composable
fun LegalConsentScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onReadPolicy: () -> Unit,
    onReadDisclaimer: () -> Unit
) {
    val isDark = LocalIsDark.current
    var checked by remember { mutableStateOf(false) }
    var warn by remember { mutableStateOf(false) }

    // 背景：和 App 页面同一套底色，弹窗才像"浮在应用上"（Web 就是这样）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) Brush.verticalGradient(
                    listOf(Color(0xFF0B1120), Color(0xFF101A2E), Color(0xFF16203A))
                ) else Brush.linearGradient(
                    listOf(Color(0xFFF7F8FD), Color(0xFFF5F6FC), Color(0xFFF8F4FC))
                )
            )
    ) {
        GlassOverlay(
            hazeState = remember { HazeState() },
            onDismiss = onDecline,
            maxWidthFraction = 0.92f,
            corner = 20,
            maxHeightFraction = 0.88f,
            // 底部自己给「暂不同意 / 同意并继续」，不要再挂一条"关闭"
            bottomClose = false
        ) {
            Column(Modifier.fillMaxWidth()) {
                // ── 标题 ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.VerifiedUser, null,
                        tint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "使用前请确认", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(10.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        // ★★2026-11-05 修「没勾选就点同意 → 底部按钮被压扁、字都看不见」
                        //
                        //   这段是条文 + 勾选 + 错误提示的滚动区，之前**没有高度约束**：
                        //   一旦多出「请先勾选…」那一行，整列就撑过弹窗的 72% 限高，
                        //   而最后的按钮行是普通子项 ⇒ 被压成一条缝（文字直接不可见）。
                        //   现在给它一个明确上限，超出的部分内部滚动，按钮行再保一个最小高度，
                        //   无论提示出现与否，两个按钮都完整可读。
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ── ① 关于你的数据（Web 第一条 dmi-group）──
                    LegalClauseCard(Icons.Filled.Shield, "关于你的数据") {
                        Text(
                            "记录与照片只保存在本机；只有你主动配置网盘后，数据才会同步到你自己的账号。" +
                                "本应用没有开发者服务器，不收集、不上传任何使用数据。" +
                                "只连 GitHub 查版本，" +
                                "传输 IP 与设备标识，不含你的记录与照片，详见《隐私政策》第四条。",
                            fontSize = 12.sp, lineHeight = 19.sp,
                            color = if (isDark) Color(0xFFA3B1C6) else Color(0xFF1E293B)
                        )
                    }

                    // ── ② 请先阅读以下条款（正文里嵌两个可点链接）──
                    LegalClauseCard(Icons.Filled.Description, "请先阅读以下条款") {
                        LegalLinkLine("《隐私政策》", "数据存在哪、联网做什么、你有哪些权利。", onReadPolicy)
                        Spacer(Modifier.height(4.dp))
                        LegalLinkLine("《免责声明》", "它是记录工具不是领队，户外风险与备份责任由你自负。", onReadDisclaimer)
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── 勾选行（Web .chk-row）：没勾就不放行 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .safeClickable {
                                checked = !checked
                                if (checked) warn = false
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (checked) {
                                        if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
                                    } else Color.Transparent
                                )
                                .border(
                                    if (warn) 2.dp else 1.5.dp,
                                    when {
                                        warn -> Color(0xFFDC2626).copy(alpha = 0.65f)
                                        checked -> if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
                                        isDark -> Color.White.copy(alpha = 0.40f)
                                        else -> Color(0xFF0F172A).copy(alpha = 0.35f)
                                    },
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checked) {
                                Icon(
                                    Icons.Filled.Check, null,
                                    tint = Color.White, modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            buildCheckText(),
                            fontSize = 12.5.sp, lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (warn) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "请先勾选「我已阅读并同意」",
                            fontSize = 11.5.sp,
                            color = Color(0xFFDC2626)
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    // Web `.legal-stamp` 脚注
                    Text(
                        "勾选并同意后，会在本机记录同意时间与条款版本，便于日后查证。",
                        fontSize = 10.5.sp, lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        GlassButton("暂不同意", onClick = onDecline)
                    }
                    Box(Modifier.weight(1f)) {
                        GlassButton(
                            "同意并继续",
                            primary = true,
                            onClick = {
                                if (!checked) warn = true else onAgree()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 条款正文里的一行可点链接：「《隐私政策》」+ 说明 */
@Composable
private fun LegalLinkLine(title: String, desc: String, onClick: () -> Unit) {
    Text(
        buildStateTitle(title, desc),
        fontSize = 12.sp, lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .safeClickable(onClick = onClick)
    )
}

@Composable
private fun buildCheckText(): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append("我已阅读并同意")
        withStyle(
            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)
        ) { append("《隐私政策》") }
        append("与")
        withStyle(
            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)
        ) { append("《免责声明》") }
    }

@Composable
private fun buildStateTitle(title: String, desc: String): androidx.compose.ui.text.AnnotatedString {
    val isDark = LocalIsDark.current
    return androidx.compose.ui.text.buildAnnotatedString {
        withStyle(
            androidx.compose.ui.text.SpanStyle(
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFFA5B4FC) else Color(0xFF3730A3)
            )
        ) { append(title) }
        append("：$desc")
    }
}
