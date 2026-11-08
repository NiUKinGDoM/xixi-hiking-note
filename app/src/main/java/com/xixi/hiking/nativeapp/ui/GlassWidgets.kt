package com.xixi.hiking.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import com.xixi.hiking.nativeapp.ui.liquid.liquidGlassChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.ui.theme.Radii
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.AccentIndigo
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeState

/**
 * ★自绘控件集 —— 2026-11-01
 * 用户要求「替换所有原生的东西」：不再用 Material 的 AlertDialog / OutlinedTextField / Button，
 * 全部改成与现有 App 一致的玻璃风格（细边框 + 半透明底 + 12 圆角）。
 */

/** 玻璃输入框（对齐 Web 版 .edit-input：浅底 + 细边 + 10 圆角 + 13px 字） */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    numeric: Boolean = false,
    password: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    center: Boolean = false,
    /**
     * 水平内边距 —— 默认 12dp。
     * ★2026-11-05：窄框（记录编辑里「时 / 分」这种并排小格）用 12dp 两边一共吃掉 24dp，
     *   框本身才 50 来 dp 时，文本区只剩不到 30dp，两位数字就显示不全（还会被裁）。
     *   这类窄格传 8dp，把省下的宽度留给数字。
     */
    paddingH: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val isDark = LocalIsDark.current
    val bg = if (isDark) Color(0xFF1A2436).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.90f)
    val line = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE2E8F0)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.INPUT.dp))
            .background(bg)
            .border(1.dp, line, RoundedCornerShape(Radii.INPUT.dp))
            .padding(horizontal = paddingH, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = textStyle.copy(
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center
                else androidx.compose.ui.text.style.TextAlign.Start
            ),
            cursorBrush = SolidColor(AccentIndigo),
            visualTransformation = if (password) VisualTransformation.None else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number
                else androidx.compose.ui.text.input.KeyboardType.Text
            ),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                inner()
            }
        )
    }
}

/** 玻璃主按钮（次要=白底描边 / 主要=靛紫渐变） */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    // ★★全玻璃按钮（2026-11-02 用户要求：「所有按钮都要液态玻璃质感，不要实色」）
    //   主次不再靠「实色块 vs 白底」区分，而是靠**染色深浅 + 棱的亮度 + 阴影**：
    //   两者都是同一套玻璃配方，只是 primary 的 tint 更重、阴影带强调色。
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // ★震动要在这里取 View（Local.current 只能在 composable 作用域读，不能在 onClick 里读）
    val tapView = androidx.compose.ui.platform.LocalView.current
    val tint = if (primary) AccentIndigo else null

    Box(
        modifier = modifier
            .liquidGlassChip(corner = Radii.BUTTON, strong = primary,
                pressed = pressed,
                enabled = enabled
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                // ★GlassButton 是全站主操作的唯一入口（保存/删除/确认都在这里）——
                //   必须同样走兜底，否则「按保存就闪退」永远治不干净。
                // ★★2026-11-03 用户：「震动反馈有好多都没有，补全」——
                //   这里原来只兜异常、**没震**：全站所有玻璃按钮（保存/确认/取消/危险操作）
                //   反而一个都不震。补 SafeTap.tap，跟其它入口一致。
                onClick = {
                    SafeTap.tap(tapView)
                    SafeTap.run("按钮") { onClick() }
                }
            )
            .padding(vertical = 11.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            // ★对齐 Web：弹窗内按钮统一 15px（原来 14，比容器的 15 小一号）
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            // 玻璃上不能用白字（浅色玻璃底 + 白字 = 看不见），
            // primary 用强调色、次要用品色文字
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                // ★主操作的字不能只是 AccentIndigo —— 紫玻璃 + 紫字几乎同色。
                //   浅色用更深的靛（压得住淡紫底），深色用更亮的靛（压得住深玻璃）。
                primary -> com.xixi.hiking.nativeapp.ui.theme.Ink
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * 玻璃确认弹窗 —— 替代 Material 的 AlertDialog（那个太"安卓原生"了）。
 * 用同一个 GlassOverlay，跟其他弹窗的观感保持一致。
 */
@Composable
fun GlassConfirm(
    hazeState: HazeState,
    title: String,
    message: String,
    confirmText: String = "确定",
    cancelText: String = "取消",
    danger: Boolean = false,
    /** 正向确认（完成 / 恢复）：绿色系，和 danger 对称 */
    positive: Boolean = false,
    /**
     * ★2026-11-05 单按钮形态（用户：「数据管理说明那两个按钮功能一样啊，
     * 关闭和知道了，改为一个关闭就行了，样式和别的统一」）。
     * 纯告知类的弹窗只需要一个「关闭」，别再摆两个同义按钮让人猜。
     */
    singleButton: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    GlassOverlay(
        bottomClose = false,
        hazeState = hazeState,
        onDismiss = onCancel,
        // 字号放大后需要更多横向空间
        maxWidthFraction = 0.86f,
        corner = 22
    ) {
        val closeOverlay = com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current
        Column(Modifier.fillMaxWidth()) {
            // ★★2026-11-03：标题/正文走全站统一样式（DialogTitle / DialogMessage）
            DialogTitle(title)
            Spacer(Modifier.height(8.dp))
            DialogMessage(message)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (!singleButton) {
                    Box(Modifier.weight(1f)) {
                        GlassButton(cancelText, onClick = closeOverlay)
                    }
                }
                Box(Modifier.weight(if (singleButton) 1f else 1f)) {
                    // ★★主操作也是液态玻璃（2026-11-02 用户第二次强调「按钮也别有颜色」）：
                    //   原来是实色靛蓝/红底 + 白字。现在整块是中性玻璃，
                    //   主次只靠 strong（更亮的棱 + 更强高光）区分，不靠染色。
                    // ★★★按钮语义按 Web 版三套（2026-11-03 第二次对齐）：
                    //   普通确认 = .glass-btn       中性玻璃
                    //   危险确认 = .confirm-btn-delete **实心红 + 白字**（不是我上一版的淡红描边）
                    //   正向确认 = 「完成了 ✓」       淡绿底 rgba(22,163,74,.16) + 0.5 绿边 + #15803D 字
                    Box(
                        modifier = Modifier
                            .liquidGlassChip(
                                corner = Radii.BUTTON,
                                strong = !singleButton && !danger && !positive,
                                // ★2026-11-03 用户：「抹掉所有足迹的弹窗按钮还是实色没改」
                                //   之前这里是 solidDanger（rgba(239,68,68,.85) 实心红 + 白字），
                                //   在整套玻璃里像贴了块塑料。改成 danger：**淡红底 + 0.9 红边 + 红字**，
                                //   和全站行内删除、抹掉足迹按钮同一套语义。
                                danger = danger,
                                positive = positive
                            )
                            .safeClickable(onClick = onConfirm)
                            .padding(vertical = 11.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            // Web：弹窗内按钮统一 15px
                            confirmText, fontSize = 15.sp,
                            fontWeight = if (danger) FontWeight.Bold else FontWeight.SemiBold,
                            color = when {
                                // 淡红底 ⇒ 红字（Web .check-go-btn 的 #b91c1c / 深 #fecaca）
                                danger -> if (LocalIsDark.current) Color(0xFFFECACA) else Color(0xFFB91C1C)
                                positive -> if (LocalIsDark.current) Color(0xFF6EE7A8) else Color(0xFF15803D)
                                else -> Ink
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 玻璃分段选择器（替代 Material Slider 那种原生控件） */
@Composable
fun GlassSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDark.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            // ★★2026-11-05 加深凹槽底（用户：「难度五个数字看着散」）
            //
            //   原来是 White.copy(0.60) 铺在白弹窗上 —— 白叠白，等于没有底。
            //   未选中项又只有透明 + 纯文字 ⇒ 5 个候选数字看起来是「散落的数字」，
            //   不像一个分段控件，尤其这一列加宽后间距一拉开更明显。
            //   ⇒ 改成可见的浅灰凹槽（选中项的白玻璃块浮在上面，形成「凹槽 + 凸起」
            //     的层次，跟 App 其它玻璃控件是同一套语言），再补一道 1dp 内描边把
            //     控件的边界说清楚。不额外加竖分隔线 —— 未选中项本来就不该有格子，
            //     有了凹槽底就够读了，再画线反而吵。
            .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color(0xFFEFF2F9))
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.07f) else Color(0xFFE3E9F4),
                RoundedCornerShape(10.dp)
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    // ★选中态不能用实色 AccentIndigo（2026-11-02 用户要求：不要实色按钮，
                    //   一律最高级液态玻璃）—— 改成「重染色玻璃」与主按钮完全同一套材质。
                    .then(
                        if (on) {
                            Modifier.liquidGlassChip(corner = 9, strong = true)
                        } else {
                            Modifier.clip(RoundedCornerShape(10.dp))
                        }
                    )
                    .safeClickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 12.sp,
                    // ★选中态现在是「染色玻璃」，白字会糊在淡紫玻璃上 ⇒ 与主按钮同一套文字色：
                    //   浅色用深靛 #3730A3，深色用亮靛 #C7D2FE（对比度才是够的）
                    color = when {
                        !on -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> com.xixi.hiking.nativeapp.ui.theme.Ink
                    },
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 表单字段标签（小字灰色，贴在输入框上方）。
 * ★原来只在记录编辑弹窗里 private 存在 —— 计划弹窗就一直没标签，
 *   用户面对一个「3767」不知道是海拔还是里程。现在提出来给所有表单共用。
 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 2.dp, bottom = 1.dp)
    )
}

/**
 * ★★弹窗标题（全站唯一写法，2026-11-03）
 *
 * 对齐 Web 版 .confirm-modal-title：18~20px / 900 / 深色 #f3f4f6，
 * 下方 6px 间距。之前每个弹窗自己写 14/15/16sp + Bold，摆在一起一大一小。
 */
@Composable
fun DialogTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val isDark = LocalIsDark.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon, null,
                tint = if (isDark) Color(0xFFA5B4FC) else Color(0xFF4F46E5),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = if (isDark) Color(0xFFF3F4F6) else Color(0xFF0F172A)
        )
    }
}

/** ★★弹窗正文：14sp / 行高 21 / #1F2937（深 #D1D5DB），对齐 Web .confirm-modal-message */
@Composable
fun DialogMessage(text: String, modifier: Modifier = Modifier) {
    val isDark = LocalIsDark.current
    Text(
        text,
        modifier = modifier,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF1F2937)
    )
}

/**
 * ★★弹窗统一「收口」：底部关闭按钮（2026-11-03 用户要求「统一为关闭按钮，X 去掉」）
 *
 * 全站弹窗不再有右上角 ✕ —— 关闭动作只有一处：卡片底部的这枚按钮。
 * 由 GlassOverlay 自动渲染，各弹窗不用自己写。
 */
@Composable
fun DialogCloseBar(onDismiss: () -> Unit, text: String = "关闭") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassChip(corner = Radii.BUTTON, flat = true)
            .safeClickable(onClick = onDismiss)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}

