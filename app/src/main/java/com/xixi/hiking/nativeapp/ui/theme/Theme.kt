package com.xixi.hiking.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * ★当前是否深色 —— 2026-11-01 新增。
 *
 * 问题：设置页可以「强制浅色 / 强制深色」，但各处玻璃与自绘控件里用的是
 *   `isSystemInDarkTheme()`，它只看系统设置 ⇒ 用户强制深色时控件仍按浅色画
 *   （白底压白字 = 看不见），反之亦然。
 * 解法：HikingTheme 把「最终生效的 dark」通过 CompositionLocal 往下传，
 *   所有自绘控件一律读 LocalIsDark，不再自己去问系统。
 */
val LocalIsDark = staticCompositionLocalOf { false }

// ★配色对齐现有 Web 版：
//   主色靛蓝 #4f46e5、文字 #334155 / #1e293b、卡片半透明白
val Indigo = Color(0xFF4F46E5)

/**
 * ★强调色（随主题变）—— 2026-11-01 新增。
 *
 * 问题：全 App 直接写死 `Indigo`（#4F46E5）。它在浅色背景上很清楚，但深色背景
 *   #0F172A 上对比度只有 ~2.4:1，字和图标都发暗看不清。
 * 解法：统一走这个「随主题变」的强调色 —— 浅色仍是 Indigo，深色自动换成浅靛 #A5B4FC。
 * 新代码请一律用 AccentIndigo，不要再写裸 Indigo。
 *
 * ⚠️ 它是 @Composable 的 getter，只能用在 @Composable 作用域（含 Modifier.composed 的工厂块里）。
 */
val AccentIndigo: Color
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = if (LocalIsDark.current) Color(0xFFA5B4FC) else Indigo

/**
 * ★强调色上的文字色（2026-11-02）
 *
 * 深色模式下 [AccentIndigo] 是浅靛 #A5B4FC —— 这时白字几乎看不见（对比度 ≈1.6:1）。
 * 所以「用强调色做底的按钮」不能用固定的 Color.White，必须用这个：
 * 浅色模式返回白色，深色模式返回深靛。
 *
 * 凡是 `.background(AccentIndigo)` 的地方，文字都应该是 [OnAccent]。
 */
val OnAccent: Color
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = if (LocalIsDark.current) Color(0xFF1E1B4B) else Color.White
/**
 * 危险色 —— 全站统一（2026-11-11 第 1 项：设计统一）
 *
 * 原来 `Color(0xFFDC2626)` 在 8 个文件里硬编码了 27 次：删除按钮、错误提示、
 * 过期徽章、签名校验失败……同一个"红"写了 27 遍。改一次颜色要在整个工程搜一遍，
 * 而且很容易漏掉一两处（比如某个弹窗里的红字还是旧色）。
 *
 * ⚠️ 这是普通 val，不是 @Composable getter —— 深色模式下也用同一个红，
 *    和浅色模式的对比度都够（不随主题变）。
 */
val DangerRed = Color(0xFFDC2626)

/**
 * ★★文字只有黑白（2026-11-02 用户要求）
 *
 * 原话：「别加那么多颜色，把现有的颜色全删掉……只需存在白色字和黑色字」。
 * 所以文字/图标一律走黑白体系，**层次只靠透明度 + 字重**，不再靠色相：
 *   [Ink]        主文字 —— 浅色主题纯黑、深色主题纯白
 *   [InkSecondary] 次要 —— 62% 不透明度（视觉是灰，本质还是黑/白）
 *   [InkMuted]     更弱 —— 40%
 *
 * ⚠️ 这三个是 @Composable getter，只能在 @Composable 作用域用。
 *    背景、玻璃、装饰色块不受影响（那些不属于"文字颜色"）。
 */
val Ink: Color
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = if (LocalIsDark.current) Color.White else Color.Black

val InkSecondary: Color
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = Ink.copy(alpha = if (LocalIsDark.current) 0.70f else 0.62f)

val InkMuted: Color
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = Ink.copy(alpha = if (LocalIsDark.current) 0.48f else 0.40f)

// ★文字色常量（colorScheme 用，主题无关的静态值）
//   浅色主题下它们是黑系，深色主题单独给白系。
val TextPrimary = Color.Black
val TextSecondary = Color.Black.copy(alpha = 0.78f)
val TextMuted = Color.Black.copy(alpha = 0.55f)

private val LightColors = lightColorScheme(
    // ★★颜色恢复（2026-11-02 用户：「app 不用全是黑白的，色带什么的就正常加颜色」）
    //   primary 回到靛蓝 —— 它被当作"强调色"用于图标、色带、进度、选中态。
    //   正文（onSurface / onBackground）仍然是黑/白，这是用户明确要求过的分工：
    //   **文字黑白、装饰有颜色**。
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color.Black.copy(alpha = 0.06f),
    onPrimaryContainer = Color.Black,
    background = Color(0xFFF7F9FC),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF1F5F9),
    // ★2026-11-02 加深：0.64 → 0.78（用户反馈"字太浅"）
    onSurfaceVariant = Color.Black.copy(alpha = 0.78f),
    outline = Color(0xFFCBD5E1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color.Black,
    primaryContainer = Color.White.copy(alpha = 0.10f),
    onPrimaryContainer = Color.White,
    background = Color(0xFF0F172A),
    onBackground = Color.White,
    surface = Color(0xFF162032),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color.White.copy(alpha = 0.84f),
    outline = Color(0xFF334155)
)

// 字重层级：标题 700、正文 500、次要 400（对齐 Web 版的 font-weight 用法）
// ★2026-11-02 整体收一档（用户：「字统一小一点」「有些内容显示不全」）：
//   标题 19→17、16→15、15→14，正文 15→14、14→13、13→12，标签 12→11.5。
//   不是随手调小 —— 配合下面的**全局字号缩放**，保证系统字体放大时也不会把卡片撑破。
//
// ★★2026-11-03 换成宋体衬线（用户：「字体和 web 端统一一下」→「就是换字体」）
//
//   Web 的 body 字体是（app.css:22）：
//     font-family: 'SimSun','宋体','STSong','Songti SC','Noto Serif SC', serif;
//     font-weight: 900;  letter-spacing: 0.02em;
//   —— 即**宋体衬线**，不是原生一直在用的系统黑体。所以同一套字号在两边的观感
//   完全不是一回事：黑体方、宋体尖，标题尤其明显。
//
//   Android 上对应 `FontFamily.Serif` → Typeface.SERIF，中文会落到系统的
//   Noto Serif CJK / 思源宋体（AOSP 自带，国产 ROM 一般也有）。万一某台机器缺这个
//   字族，系统会回退到默认字体 —— 退化但不会乱码。
//
//   ⚠️ 实测：`FontFamily.Serif` 在 Android 上**中文仍然是黑体**。
//   AOSP 的 fonts.xml 里 serif family 只挂了拉丁的 NotoSerif，中文 fallback 链
//   全局共用 NotoSansCJK（黑体）—— 所以只有英文字母和数字会变衬线，汉字纹丝不动。
//   要做到"真的是宋体"，必须**自带字体文件**。这里内嵌 Noto Serif SC
//   （SIL OFL 1.1，可商用），Regular + Bold 两个字重，正文/标题都能用。
//
//   ⚠️ 全站只有 4 处 Monospace（帧率胶囊 / 版本号 / 堆栈）保持等宽，其余一律继承这里，
//   不要在别处再写 fontFamily，否则就是又一处"和 Web 对不上"。
val AppSerif = FontFamily(
    Font(R.font.app_serif_regular, FontWeight.Normal),
    Font(R.font.app_serif_regular, FontWeight.Medium),
    Font(R.font.app_serif_bold, FontWeight.SemiBold),
    Font(R.font.app_serif_bold, FontWeight.Bold)
)

/** Web 的 letter-spacing: 0.02em —— 宋体字面窄，加一点字距才有那个呼吸感 */
private val AppLetterSpacing = 0.02.em

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
).let { t ->
    // 统一套上字体族 + 字距（每个 style 单独 copy，避免漏掉任何一个档位）
    fun TextStyle.serif() = copy(fontFamily = AppSerif, letterSpacing = AppLetterSpacing)
    t.copy(
        displayLarge = t.displayLarge.serif(),
        displayMedium = t.displayMedium.serif(),
        displaySmall = t.displaySmall.serif(),
        headlineLarge = t.headlineLarge.serif(),
        headlineMedium = t.headlineMedium.serif(),
        headlineSmall = t.headlineSmall.serif(),
        titleLarge = t.titleLarge.serif(),
        titleMedium = t.titleMedium.serif(),
        titleSmall = t.titleSmall.serif(),
        bodyLarge = t.bodyLarge.serif(),
        bodyMedium = t.bodyMedium.serif(),
        bodySmall = t.bodySmall.serif(),
        labelLarge = t.labelLarge.serif(),
        labelMedium = t.labelMedium.serif(),
        labelSmall = t.labelSmall.serif()
    )
}

/**
 * ★★全局字号缩放（2026-11-02 —— 修「内容显示不全」的根因）
 *
 * 之前所有字号都是写死的 sp，而 sp 会跟随**系统字体大小**放大。
 * 用户把系统字体调大一档（或开了"大号字体"），卡片标题、统计数值、导航文字
 * 就会顶出固定高度的容器 —— 表现就是"有些内容显示不全"，但截图里（fontScale=1）永远正常。
 *
 * 这里做两件事：
 *   ① 系统 fontScale **封顶 1.0**：宁可字小，也不能把布局撑破；
 *   ② 再统一缩到 0.94：这是用户要的"整体小一点"（19→约 17.9、13→约 12.2）。
 *
 * 用 LocalDensity 统一缩放，比去改几百处字面量安全 —— 一处生效、不会漏。
 */
@Composable
private fun AppTextScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    // ★★2026-11-02 第三次修正（用户：「所有字改正常大小吧」）：
    //   0.9 那一档是小一号的字，正文 12sp 实际只有 10.8 —— 偏小、发虚。
    //   现在**字重回归 1.0**（即设计稿标注的 17/15/14/13/12/11 就是屏幕上看到的），
    //   只保留一个保护：系统字体放大封顶 1.0，避免把固定高度的行撑破。
    val scale = d.fontScale.coerceAtMost(1f).coerceAtLeast(0.85f)
    if (kotlin.math.abs(scale - d.fontScale) < 0.001f) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(density = d.density, fontScale = scale),
            content = content
        )
    }
}

@Composable
fun HikingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalIsDark provides darkTheme,
        // ★2026-11-03 兜底：不在 Surface 里的 Text（弹窗内部、自定义容器）默认也能拿到宋体。
        //   Typography 只覆盖"用 M3 样式"的那批，LocalTextStyle 才能兜住所有裸 Text。
        LocalTextStyle provides TextStyle(
            fontFamily = AppSerif,
            letterSpacing = AppLetterSpacing
        )
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography
        ) {
            AppTextScale(content)
        }
    }
}
