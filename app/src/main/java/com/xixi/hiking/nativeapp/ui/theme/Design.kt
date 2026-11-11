package com.xixi.hiking.nativeapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * ★设计语言基准（2026-11-02 建立）
 *
 * 建立原因：自查时发现全项目有 **14 种圆角**（2/3/4/5/8/9/10/11/12/13/14/16/17/18）
 * 和 **18 种字号**（含 9.5 / 10.5 / 11.5 / 12.5 / 13.5 这些半号）。
 * 每个组件各写各的，界面看着"能跑但不像一套东西"。
 *
 * 现在收敛到固定档位，新增组件请**只从这里取值**，不要再拍一个新数字。
 */

// ═══════════════════════════════════════════════════════════════
//  圆角（6 档）
// ═══════════════════════════════════════════════════════════════

/**
 * 圆角档位 —— 与页面层级一一对应：
 * - CARD(20)：大卡片、弹窗外壳
 * - SUB(16)：子卡片（统计卡、山册卡、列表容器）
 * - BUTTON(12)：所有按钮、胶囊按钮、分段控件
 * - INPUT(10)：输入框、日期格、行内小按钮、代码块
 * - PILL(6)：小标签、状态徽章、色条
 * - 圆形一律用 CircleShape，不要写 rounded(50)
 */
object Radii {
    const val CARD = 20
    const val SUB = 16
    const val BUTTON = 12
    const val INPUT = 10
    const val PILL = 6
    /** 极小的装饰性色条（4dp 线、书脊边）—— 只用于细条，不用于可点区域 */
    const val TICK = 4
}

// ═══════════════════════════════════════════════════════════════
//  字号（9 档）
// ═══════════════════════════════════════════════════════════════

/**
 * 字号档位 —— 只有这 9 个值，不要再用半号（11.5 / 12.5 之类）。
 *
 * - HERO(22)：分享卡大标题（唯一例外，导出图专用）
 * - TITLE(19)：页面主标题、"走完了"这类庆祝标题
 * - H2(16)：弹窗标题、月份标题
 * - H3(15)：区块小标题（工具行里的"记录""计划""设置"）
 * - BODY(14)：正文、按钮文字、列表主文本
 * - LABEL(13)：次要按钮、卡片标题、表单字段名
 * - CAPTION(12)：说明文字、副标题、单位
 * - TINY(11)：极小说明、图例、徽章
 * - MICRO(10)：角标、脚注（能用 TINY 就别用 MICRO）
 */
object Type {
    val HERO = 22.sp
    val TITLE = 19.sp
    val H2 = 16.sp
    val H3 = 15.sp
    val BODY = 14.sp
    val LABEL = 13.sp
    val CAPTION = 12.sp
    val TINY = 11.sp
    val MICRO = 10.sp
}

// ═══════════════════════════════════════════════════════════════
//  语义色
// ═══════════════════════════════════════════════════════════════

/**
 * 固定色 —— 这些颜色**不随主题变**（它们是"事实色"：红色就是危险，绿色就是完成）。
 * 需要在深色下调亮度的，用带 Dark 后缀的那个。
 */
object Palette {
    /** 危险 / 删除 / 错误 */
    val danger = DangerRed
    val dangerSoft = Color(0xFFEF4444)
    val dangerOnDark = Color(0xFFF87171)
    /** 已完成 / 成功 */
    val success = Color(0xFF16A34A)
    val successDeep = Color(0xFF15803D)
    val successOnDark = Color(0xFF6EE7A8)
    val successLight = Color(0xFF10B981)
    /** 警告 / 待处理 / 过期 */
    val warn = Color(0xFFF59E0B)
    val warnDeep = Color(0xFFD97706)
    /** 信息 / 明天 / 提示 */
    val info = Color(0xFF0EA5E9)
    /** 中性（灰系）—— 边框、次要图标、删除按钮底 */
    val neutral = Color(0xFF64748B)
    val neutralSoft = Color(0xFF94A3B8)
    val neutralBorder = Color(0xFFCBD5E1)
    /** 卡片描边（浅色模式）—— 白卡贴白底时唯一能看出边界的东西 */
    val borderLight = Color(0xFFE2E8F0)
    val borderSubtle = Color(0xFFEDF0F7)
    val borderSoft = Color(0xFFEFF1F6)
    /** 强调紫（分享卡、难度色禁用正向） */
    val purple = Color(0xFF7C3AED)
    val purpleDeep = Color(0xFF6D28D9)
    /** 难度/图表的辅助色 */
    val blue = Color(0xFF3B82F6)
    val teal = Color(0xFF14B8A6)
    val violet = Color(0xFFA855F7)
    val orange = Color(0xFFF97316)
}

// ═══════════════════════════════════════════════════════════════
//  弹窗尺寸（3 档，对齐 Web 版的 mdl-sm / md / lg）
// ═══════════════════════════════════════════════════════════════

/**
 * 弹窗宽度与限高。
 *
 * 之前各弹窗自己写 0.80 / 0.84 / 0.86 / 0.88 / 0.90 / 0.92 六种宽度，
 * 限高 0.76 / 0.78 / 0.80 / 0.82 四种 —— 同一个 App 里弹窗一张一个样。
 *
 * - [SM] 0.80：确认类（"删除这个计划？"这种一两句话）
 * - [MD] 0.88：一般弹窗（列表、详情）
 * - [LG] 0.92：表单与长正文（编辑记录、隐私政策）
 */
object DialogSize {
    const val SM = 0.80f
    const val MD = 0.88f
    const val LG = 0.92f
    /** 统一限高：内容再多也不超过屏幕 80%，底部永远留出可点的边距 */
    const val MAX_HEIGHT = 0.80f
}
