package com.xixi.hiking.nativeapp.ui.settings

import com.xixi.hiking.nativeapp.R
import com.xixi.hiking.nativeapp.ui.safeClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Signpost
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xixi.hiking.nativeapp.BuildConfig
import com.xixi.hiking.nativeapp.data.GallerySaver
import com.xixi.hiking.nativeapp.data.Prefs
import com.xixi.hiking.nativeapp.data.Updater
import com.xixi.hiking.nativeapp.ui.GlassButton
import com.xixi.hiking.nativeapp.ui.GlassOverlay
import com.xixi.hiking.nativeapp.ui.theme.Ink
import com.xixi.hiking.nativeapp.ui.theme.LocalIsDark
import com.xixi.hiking.nativeapp.ui.theme.Radii
import dev.chrisbanes.haze.HazeState
import com.xixi.hiking.nativeapp.ui.theme.DangerRed

/**
 * 关于页的四个弹窗：更新日志 / 隐私政策 / 免责声明 / 支持作者。
 *
 * ★★★2026-11-03 用户：「支持作者、免责声明、隐私政策、更新日志的内容和格式，
 *   对齐 web 版本，缺少的内容给我补全。」
 *
 * 之前这一版是我自己捏的：把 Web 的十几条条款压成了几段短句，
 * 格式也是「粗标题 + 一段黑字」，越权、越简洁，法务信息反而丢了。
 * 更糟的是「支持作者」里写了个占位邮箱 `niuxc@example.com（换成你自己的邮箱）`，
 * 还写着"赞赏码可放在官网" —— 等于什么都没给。
 *
 * 现按 Web 版逐条对齐：
 *   · 格式 → Web `.dmi-group`：每条一块淡灰圆角卡，左侧 15dp 靛蓝图标，
 *            标题 13sp/600，正文 12sp/1.6 行高。不再是"裸标题 + 裸正文"。
 *   · 隐私 → 11 条（补上「个人信息安全事件的通知」「未成年人保护」
 *            以及完整的联系方式/适用法律两段）
 *   · 免责 → 11 条（补上「请走正规路线」「官方渠道与内容来源」
 *            「知识产权」「条款变更与适用法律」）
 *   · 支持 → 恢复 Web 的两步流程：渠道（微信/支付宝）→ 收款码大图 + 保存到相册
 *   · 更新日志 → Web 的排版：只展示最近 3 个版本、首个带「本次更新」徽章、
 *           底部一行 Made by XiXi（原来是把 36 个版本全堆进可折叠列表）
 */

// ═══════════════════════════════════════════════════════════════
//  通用：条文卡（1:1 复刻 Web .dmi-group）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun Clause(icon: ImageVector, title: String, body: String) {
    LegalClauseCard(icon, title) {
        Text(
            body,
            // Web: .dmi-body 12px / line-height 1.6 / #1e293b（深 #a3b1c6）
            fontSize = 12.sp,
            lineHeight = 19.sp,
            color = if (LocalIsDark.current) Color(0xFFA3B1C6) else Color(0xFF1E293B)
        )
    }
}

/**
 * 条文卡（Web `.dmi-group`）——正文是可组合插槽，给「法务同意页」用
 * （那一页要在条款正文里塞两个可点击的《隐私政策》《免责声明》链接）。
 */
@Composable
fun LegalClauseCard(
    icon: ImageVector,
    title: String,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val isDark = LocalIsDark.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            // Web: rgba(148,163,184,0.10) / 深色 white 0.06
            .background(
                if (isDark) Color.White.copy(alpha = 0.06f)
                else Color(0xFF94A3B8).copy(alpha = 0.10f)
            )
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Icon(
            icon, null,
            // Web: .dmi-ic 15px #4f46e5 / 深色 #818cf8
            tint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
            modifier = Modifier.padding(top = 1.dp).size(15.dp)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                // Web: .dmi-title 13px / 600 / #0f172a（深 #e5e7eb）
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFFE5E7EB) else Color(0xFF0F172A),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            body()
        }
    }
}

@Composable
private fun DocDialog(
    hazeState: HazeState,
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    maxHeightFraction: Float = 0.80f,
    content: @Composable () -> Unit
) {
    val isDark = LocalIsDark.current
    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.92f,
        corner = 20,
        maxHeightFraction = maxHeightFraction
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Web .confirm-modal-title：图标 #4f46e5 + 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, null,
                    tint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  隐私政策（11 条 · 对齐 Web showPrivacyPolicyModal）
// ═══════════════════════════════════════════════════════════════

@Composable
fun PrivacyPolicyDialog(hazeState: HazeState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    // Web：已同意过就在最底部补一行「你已于 X 同意本政策（条款版本 Y）」
    val agreedVersion = remember {
        runCatching { Prefs.getString(ctx, Prefs.KEY_LEGAL_AGREED, "") }.getOrDefault("")
    }
    val agreedAt = remember {
        runCatching { Prefs.getString(ctx, Prefs.KEY_LEGAL_AGREED_AT, "") }.getOrDefault("")
    }

    DocDialog(hazeState, "隐私政策", Icons.Filled.Security, onDismiss, maxHeightFraction = 0.86f) {
        Clause(
            Icons.Filled.Info, "适用范围与生效",
            // ★2026-11-07 用户：「更新一下免责声明和隐私政策，匹配我现在这个版本，
            //   删掉软件名字后的原生版，写说明的时候也别提原生版，正式改名为徒步小记」
            //   ⇒ 应用名统一为「徒步小记」；不再出现"原生版/客户端"这类区分说法。
            "本政策适用于「徒步小记」应用（Android 版，以下统称本应用）。本应用由 xixi 独立开发与维护" +
                "（下称「开发者」或「我们」），属《中华人民共和国个人信息保护法》意义上的个人信息处理者，" +
                "联系方式见第十一条。生效日期：2026-10-09；最近更新：2026-11-07。本政策随版本迭代更新；" +
                "若变更涉及你的权利或个人信息处理方式的重大变化，我们会更新条款版本号并在应用内重新征求你的同意。"
        )
        Clause(
            Icons.Filled.Storage, "一、数据存储位置与期限",
            "本应用是纯本地工具：记录、计划、备注、照片全部保存在你的设备上，本应用不设账号体系，" +
                "不收集、不上传你的个人身份信息、行为数据、设备标识与位置信息，也未接入任何统计、广告或推送 SDK。" +
                "存储期限由你决定 —— 卸载应用或使用「抹掉所有足迹」即可清除本机全部数据。"
        )
        Clause(
            Icons.Filled.CloudDone, "二、云备份（可选）",
            "仅当你主动在设置中配置 WebDAV 后，数据才会同步到你本人的网盘账号。服务器地址、账号与应用密码" +
                "仅保存于本机，采用加密存储，任何界面均不明文展示；与网盘的通信全程 HTTPS，且在你的设备与" +
                "网盘服务商之间直接进行 —— 本应用没有开发者服务器，数据不经过任何中转。" +
                "备份文件中可包含一份加密后的网盘配置（便于换机一键恢复），请妥善保管备份文件，避免提供或发送给他人。"
        )
        Clause(
            Icons.Filled.Share, "三、信息共享、转让与公开披露",
            "本应用不会向任何第三方出售、出租、共享、转让或公开披露你的数据，也不存在与合作方共享数据的情形。" +
                "开发者不持有你的数据，因此任何第三方（含广告商、数据服务商）均无法通过本应用获取你的记录与照片。"
        )
        Clause(
            Icons.Filled.Wifi, "四、联网行为与第三方服务",
            "① 检查更新：应用会请求 GitHub Releases 接口查询最新版本号，仅产生常规网络请求信息（IP、User-Agent），" +
                "不含你的记录、照片或身份信息；GitHub 的服务器位于中国境外，该请求可能构成个人信息的跨境传输 —— " +
                "若你不希望发生该传输，可在完全离线（如飞行模式）下使用本应用，离线不影响记录、查看、导出等核心功能，" +
                "仅无法获知是否有新版本。② 云备份：依赖你自行选择的 WebDAV 服务商（如坚果云，服务器位于中国境内），" +
                "其数据处理行为适用该服务商的条款与政策。③ 崩溃诊断：应用异常时只在本机生成一份诊断文本" +
                "（版本号、错误内容、时间，不含你的记录与照片），不会自动上传 —— 只有你主动点「导出诊断」" +
                "并通过邮件、Issue 等方式发送给我们时，我们才会看到它。④ 官方网站：应用内" +
                "「关于应用 → 官方网站」入口会调用系统浏览器打开项目官网，仅发生常规网页访问，不携带你的任何" +
                "记录、照片或身份信息。"
        )
        Clause(
            Icons.Filled.Security, "五、数据安全措施",
            "本应用采用以下措施保护你的数据：本地数据随应用沙箱隔离；网盘凭据加密存储；Android 安装包经过签名校验，" +
                "防止被篡改或替换安装；不申请通讯录、短信、通话记录、精确位置等与功能无关的权限。" +
                "同时请理解：任何存储方式都无法保证绝对安全，请定期导出备份并自行妥善保存。"
        )
        Clause(
            Icons.Filled.ReportProblem, "六、个人信息安全事件的通知",
            "本应用的数据默认只存在于你自己的设备上，开发者并不持有你的记录与照片，因此不存在「服务端数据被批量获取」" +
                "的情形。即便如此，一旦发现你的个人信息发生或可能发生泄露、篡改或丢失（例如网盘凭据在传输环节出现风险、" +
                "安装包校验机制被绕过），我们会立即采取补救措施（如暂停相关功能、尽快发布修复版本），并通过应用内弹窗、" +
                "更新日志与官网公告等可行方式如实告知：事件基本情况、可能造成的影响、我们已采取的措施，以及你可以采取的" +
                "应对建议（如更换网盘应用密码）；同时依法向履行个人信息保护职责的部门报告。" +
                "如你发现疑似安全问题，也可通过第十一条的联系方式主动告知我们。"
        )
        Clause(
            Icons.Filled.Rule, "七、你的权利",
            "查阅与更正：全部数据在应用内可见、可改；导出与迁移：随时「导出完整备份」带走记录与照片；" +
                "删除：单条删除、批量删除、抹掉所有足迹（云端旧备份需你自行在网盘删除）；" +
                "撤回同意：你可在「设置 → 云备份」解绑网盘账号，以撤回对云备份的授权；撤回不影响撤回前基于你的同意" +
                "已进行的处理。上述权利均无需向开发者申请，你在应用内即可直接行使；如需协助，可通过第十一条的联系方式联系我们。"
        )
        Clause(
            Icons.Filled.ChildCare, "八、未成年人保护",
            "本应用不面向 14 周岁以下儿童单独提供服务。未成年人应在监护人指导下使用；户外活动请务必由成年人陪同，" +
                "并由监护人承担相应监护责任。"
        )
        Clause(
            Icons.Filled.VpnKey, "九、权限清单与用途",
            // ★2026-11-07 按 AndroidManifest 实际声明的权限重写（用户要求"匹配我现在这个版本"）。
            //   旧文写的是「精确闹钟 / 开机启动 / 安装应用」—— 代码里根本没有 AlarmManager、
            //   没有 RECEIVE_BOOT_COMPLETED，属于文案超前于实现，法务上不能这么写。
            "本应用只申请三项权限：① 网络访问 —— 检查更新与云备份；② 网络状态 —— 判断是否离线，" +
                "离线时自动跳过同步；③ 通知（Android 13 及以上）—— 计划与备份提醒。以上均可在系统设置中拒绝，" +
                "拒绝仅导致对应功能不可用，不影响记录、查看与导出。另有一项「存储写入」仅针对 Android 9 及以下，" +
                "用于把备份与分享图保存到「下载」目录。以下两项能力不需要任何授权：点按震动由系统触感接口提供；" +
                "添加照片使用系统相册选择器 —— 你只授予选中的那几张照片，本应用无法遍历你的相册。"
        )
        Clause(
            Icons.Filled.Update, "十、政策更新",
            "本政策随功能迭代更新，最新内容始终在本页展示。若更新涉及你的权利或个人信息处理方式的重大变化，" +
                "我们会更新条款版本号，并在应用内重新征求你的同意 —— 你需再次阅读并勾选同意后方可继续使用；" +
                "不同意时可停止使用并卸载本应用。"
        )
        Clause(
            Icons.Filled.Gavel, "十一、适用法律与联系方式",
            "本应用的运营者为 xixi（GitHub：NiUKinGDoM），系本政策项下的个人信息处理者。如对本政策有疑问、意见或投诉，" +
                "或需行使查阅、更正、删除、撤回同意等权利，可通过「关于应用 → GitHub」提交 Issue、" +
                "发邮件至 Xixihiking@foxmail.com，或访问项目官网 xixi-hiking-site.pages.dev" +
                "（应用内「关于应用 → 官方网站」可一键打开），我们将在合理期限内（一般不超过 15 个工作日）答复。" +
                "本政策的订立、效力、解释与争议解决均适用中华人民共和国法律；因本政策发生争议的，双方应友好协商解决，" +
                "协商不成的，可依法向有管辖权的人民法院提起诉讼。"
        )

        if (agreedVersion.isNotBlank()) {
            // Web `.legal-stamp`：一行小字说明「何时同意了什么版本的条款」
            Text(
                if (agreedAt.isNotBlank()) "你已于 ${fmtStamp(agreedAt)} 同意本政策（条款版本 $agreedVersion）"
                else "你已同意本政策（条款版本 $agreedVersion）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  免责声明（11 条 · 对齐 Web showDisclaimerModal）
// ═══════════════════════════════════════════════════════════════

@Composable
fun DisclaimerDialog(hazeState: HazeState, onDismiss: () -> Unit) {
    DocDialog(hazeState, "免责声明", Icons.Filled.Hiking, onDismiss, maxHeightFraction = 0.86f) {
        Clause(
            Icons.Filled.Hiking, "一、服务性质：记录工具，不是领队",
            "本应用是一款个人徒步记录工具，用于记录行程、心情与照片。它不提供路线规划、导航、定位救援、天气预警、" +
                "医疗建议或任何形式的户外安全指导，不能替代专业向导、领队、地图与救援服务 —— 它不是领队，也不是救生员。"
        )
        Clause(
            Icons.Filled.FactCheck, "二、记录数据仅供参考",
            "记录中的海拔、距离、用时、难度、天气等均由你自行填写，本应用不做测量与校验，可能存在误差；" +
                "里程碑、统计与山册展示均基于上述自填数据推算。全部数据仅供个人回顾，不构成专业测量结果，" +
                "不可用于测绘、赛事认证、保险理赔或任何法律用途。"
        )
        Clause(
            Icons.Filled.Signpost, "三、请走正规路线",
            "本应用不引导、也不建议前往未开发的野山、自然保护区或禁止进入的区域。出行请选择正规开放路线，" +
                "遵守当地法律法规及景区、保护区管理规定，服从现场管理与警示。"
        )
        Clause(
            Icons.Filled.WbSunny, "四、出发前做好准备",
            "请先查看天气预报与封山公告，结伴而行，带足饮水、照明与充电设备；不逞强、不夜行、不涉险，量力而行。" +
                "本应用不具备定位与求救功能 —— 遇险请立即拨打 110、119 或当地山地救援电话，不要依赖本应用中的" +
                "记录或坐标。户外安全永远由你本人第一位负责。"
        )
        Clause(
            Icons.Filled.Warning, "五、风险自担",
            "户外活动存在固有且无法完全避免的风险。你应在出行前自行评估身体状况、路线难度、天气与装备情况，" +
                "未成年人须由监护人陪同。因你的出行行为或判断所产生的一切后果，由你自行承担。"
        )
        Clause(
            Icons.Filled.Backup, "六、数据安全与备份责任",
            "你的数据默认仅存于本机。卸载应用、清除数据、设备损坏或丢失均可能导致数据丢失；云备份依赖你自行配置的" +
                "第三方网盘，其可用性、容量与数据完整性由该服务商负责。请定期导出备份并妥善保存 —— " +
                "开发者无法找回你未备份的数据。"
        )
        Clause(
            Icons.Filled.CloudOff, "七、第三方服务",
            "应用内的更新检查依赖 GitHub，云备份依赖你选择的 WebDAV 网盘。上述服务的可用性、内容变更、服务中断或" +
                "数据处理行为不由开发者控制，开发者不就此承担赔偿责任。"
        )
        Clause(
            Icons.Filled.Public, "八、官方渠道与内容来源",
            "本应用的官方网站为 xixi-hiking-site.pages.dev（应用内「关于应用 → 官方网站」可一键打开），" +
                "源码仓库为 GitHub 上的 xixi-hiking 项目。官网仅作介绍、截图与下载指引之用，其页面内容可能滞后于实际版本；" +
                "功能、界面与更新内容一律以你设备上实际运行的版本为准。请通过上述官方渠道获取安装包，" +
                "从非官方渠道下载或他人二次打包的版本，开发者无法保证其安全性与完整性，由此产生的后果由获取者自行承担。"
        )
        Clause(
            Icons.Filled.Balance, "九、责任限制",
            "在适用法律允许的最大范围内，开发者不对因使用或无法使用本应用所致的间接、附带或衍生损失" +
                "（包括但不限于数据丢失、行程延误、人身或财产损害）承担赔偿责任。本条不排除或限制依法不得排除、" +
                "限制的责任；如相关法律另有强制性规定，以该法律规定为准。"
        )
        Clause(
            Icons.Filled.Copyright, "十、知识产权",
            "本应用的界面设计、图标与程序代码归开发者所有；你在应用内创建的记录、备注与照片归你本人所有。" +
                "请勿对本应用进行反向工程、二次打包或用于商业用途。"
        )
        Clause(
            Icons.Filled.Gavel, "十一、条款变更与适用法律",
            "本声明可能随版本更新调整，更新后在本页展示；若调整涉及你的重要权益，我们会在应用内重新征求你的同意。" +
                "你继续使用本应用即视为接受更新后的条款，如不同意请停止使用并卸载。本声明适用中华人民共和国法律；" +
                "因本声明或使用本应用发生争议的，双方应友好协商解决，协商不成的，可依法向有管辖权的人民法院提起诉讼。" +
                "生效日期：2026-10-09；最近更新：2026-11-07。"
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  更新日志（★2026-11-04 回到「可展开的全部版本」那一版）
// ═══════════════════════════════════════════════════════════════

/**
 * 更新日志。
 *
 * ★★2026-11-04 用户：「更新日志没有关闭按钮，其实我更喜欢你之前做的那个，改回去吧」
 *
 *   上一版我按 Web 的排版只显示最近 3 个版本 —— 看起来是"对齐了 Web"，
 *   但代价是：老版本再也翻不到，而且我把底部的「关闭」条一起去掉了
 *   （为了不让三个按钮挤在一起），结果只能点空白或返回键退出，很难用。
 *
 *   现在回到之前那版：**全部版本都在、默认只展开最新一版、历史版本点一下展开**，
 *   底部保留「关闭」。它比 Web 多的是"能翻历史"，少的是"必须滚很久"。
 */
@Composable
fun ChangelogDialog(hazeState: HazeState, onDismiss: () -> Unit) {
    // 默认展开最新一版；其余折叠
    var expanded by remember { mutableStateOf(setOf(0)) }
    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.94f,
        corner = 24,
        maxHeightFraction = 0.86f
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    com.xixi.hiking.nativeapp.ui.DialogTitle(
                        "更新日志", icon = Icons.Filled.HistoryEdu
                    )
                }
                // 版本数收成一枚小胶囊，不再和标题挤在一行文字里
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${NativeChangelog.size} 个版本",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            )
            Spacer(Modifier.height(4.dp))

            // 高度交给外层（GlassOverlay 已有 maxHeightFraction），这里 weight(1f, fill=false)：
            // 条目少时卡片自己缩短，条目多时才滚动。
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 10.dp)
                ) {
                    // ★2026-11-05 气味扫描：列表项没有 key，展开/收起某个版本后再刷新，
                    //   "展开的那一项"会按位置错到别的版本上。key 用版本号最稳（唯一）。
                    itemsIndexed(NativeChangelog, key = { _, item -> item.first }) { idx, item ->
                        if (idx > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            )
                        }
                        val body = parseChangelogBody(item.second)
                        val open = expanded.contains(idx)
                        VersionBlock(
                            version = item.first,
                            items = if (open) body else emptyList(),
                            latest = idx == 0,
                            collapsedCount = if (open) null else body.count { !it.header },
                            onToggle = {
                                expanded = if (open) expanded - idx else expanded + idx
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 一个版本块。
 * [collapsedCount] 不为空表示**折叠态**：只显示版本号 + 「N 项」+ 箭头，点整行展开。
 */
@Composable
private fun VersionBlock(
    version: String,
    items: List<ChangelogLine>,
    latest: Boolean,
    collapsedCount: Int? = null,
    onToggle: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = if (onToggle != null) {
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .safeClickable(onClick = onToggle)
            } else Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                version,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (latest) {
                Spacer(Modifier.width(7.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "最新",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (collapsedCount != null) {
                Text(
                    "$collapsedCount 项",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    Icons.Filled.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (items.isNotEmpty()) Spacer(Modifier.height(5.dp))
        items.forEach { line ->
            if (line.header) {
                // 分组小标题（【新增】/【优化】/【修复】）：不带圆点，小一号、主色、上面留一点空
                Text(
                    line.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                    // 圆点用画的，不用「·」字符 —— 字符在不同字体里大小/基线都不一样
                    Box(
                        Modifier
                            .padding(top = 6.dp, end = 7.dp)
                            .size(3.5.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                    )
                    Text(
                        line.text,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                    )
                }
            }
        }
    }
}

/**
 * 把一段更新说明拆成「分组标题 + 条目」。
 *
 * ★2026-11-05 重写（用户：「更新说明太粗略，对齐网页端的格式」）：
 *   网页版的写法是 `【新增】` / `【优化】` / `【修复】` 分组，下面跟 `- 条目`。
 *   上一版解析器**把【】标题直接丢掉**，于是详细条目全挤成一片 bullet，
 *   看不出"这条是新增还是修复"。现在标题保留下来单独渲染成小标题。
 */
internal data class ChangelogLine(val text: String, val header: Boolean = false)

private fun parseChangelogBody(body: String): List<ChangelogLine> =
    body.split("\n")
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("Made by") }
        .flatMap { line ->
            if (line.startsWith("【")) {
                // 【新增】/【优化】/【修复】/【已知问题】
                sequenceOf(ChangelogLine(line.trim('[', ']'), header = true))
            } else {
                line.removePrefix("- ")
                    .split(Regex("(?=\\d+[.、]\\s)"))
                    .asSequence()
                    .map { seg ->
                        ChangelogLine(
                            seg.trim()
                                .removePrefix("- ")
                                .replace(Regex("^\\d+[.、]\\s*"), "")
                                .replace("**", "")
                                .trim()
                        )
                    }
            }
        }
        .filter { it.header || it.text.isNotBlank() }
        .toList()

// ═══════════════════════════════════════════════════════════════
//  支持作者（Web 两步：渠道 → 收款码 + 保存到相册）
// ═══════════════════════════════════════════════════════════════

@Composable
fun SupportDialog(hazeState: HazeState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(0) }        // 0 = 选渠道，1 = 微信码，2 = 支付宝码
    var toast by remember { mutableStateOf<String?>(null) }

    GlassOverlay(
        hazeState = hazeState,
        onDismiss = onDismiss,
        maxWidthFraction = 0.88f,
        corner = 20,
        maxHeightFraction = 0.82f,
        bottomClose = false
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE11D48), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (step == 0) "支持作者" else if (step == 1) "微信收款" else "支付宝收款",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))

            if (step == 0) {
                // ★Web 原文：完全免费、没有广告、纯自愿，不付费也拥有全部功能
                Text(
                    "完全免费，没有广告。\n喜欢就请我喝杯奶茶。",
                    fontSize = 13.sp, lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    // 微信 / 支付宝：Web 是两个语义色描边按钮（绿 #047857 / 蓝 #0057b8）
                    PayButton("微信", Color(0xFF07C160), Modifier.weight(1f)) { step = 1 }
                    PayButton("支付宝", Color(0xFF1677FF), Modifier.weight(1f)) { step = 2 }
                }
                Spacer(Modifier.height(9.dp))
                GlassButton("关闭", onClick = com.xixi.hiking.nativeapp.ui.LocalOverlayClose.current)
            } else {
                val isWx = step == 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(
                            if (isWx) R.drawable.support_qr_wechat else R.drawable.support_qr_alipay
                        ),
                        contentDescription = if (isWx) "微信收款码" else "支付宝收款码",
                        contentScale = ContentScale.Fit,
                        // 微信码 800×1089、支付宝 800×1200 ⇒ 按 300dp 高度 Fit 出来约 220dp 宽
                        // （Web 是 min(70vw,254px)，这里取同一观感）
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "保存二维码到相册后，打开${if (isWx) "微信" else "支付宝"}「扫一扫」，" +
                        "点相册选图付款",
                    fontSize = 12.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (toast != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(toast!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) {
                        GlassButton("返回", onClick = { step = 0; toast = null })
                    }
                    Box(Modifier.weight(1f)) {
                        GlassButton(
                            "保存二维码",
                            primary = true,
                            onClick = {
                                val ok = GallerySaver.saveDrawable(
                                    ctx,
                                    if (isWx) R.drawable.support_qr_wechat else R.drawable.support_qr_alipay,
                                    if (isWx) "xixi-support-wechat" else "xixi-support-alipay"
                                )
                                toast = if (ok) "已保存到系统相册" else "存失败，可截图"
                            }
                        )
                    }
                }
            }
        }
    }
}

/** ISO 时间戳 → 本地可读（yyyy-MM-dd HH:mm），对齐 Web `formatLegalStamp` */
internal fun fmtStamp(iso: String): String = runCatching {
    val d = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(iso)
        ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(iso)
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(d!!)
}.getOrDefault(iso)

/** 微信/支付宝语义色按钮（Web 是绿/蓝描边玻璃，不是主按钮） */
@Composable
private fun PayButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.BUTTON.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.50f), RoundedCornerShape(Radii.BUTTON.dp))
            .safeClickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ═══════════════════════════════════════════════════════════════
//  检查更新结果
// ═══════════════════════════════════════════════════════════════

@Composable
fun UpdateAvailableDialog(
    hazeState: HazeState,
    release: Updater.Release,
    /** 开始下载（下载、校验、拉安装器都在 ApkDownloader 里串好了） */
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    // ★★2026-11-09 从"跳浏览器下载"改成**应用内下载安装**（对齐网页版那套）。
    //   状态住在 ApkDownloader 这个单例里，所以关掉弹窗也在继续下，
    //   再打开还能看到进度 —— 放在 remember 里一关就断了。
    val st by com.xixi.hiking.nativeapp.data.ApkDownloader.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    GlassOverlay(hazeState = hazeState, onDismiss = onDismiss, maxWidthFraction = 0.92f, corner = 20) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NewReleases, null, tint = Ink, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (release.isBeta) "测试版 ${release.tag}" else "新版本 ${release.tag}",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // ★2026-11-05 预发布（测试版）必须标出来 —— 装的人有权知道
                //   自己拿到的是还没转正的包。文案遵守「说明 ≤10 字」。
                if (release.isBeta) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "测试版", fontSize = 10.sp,
                            color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "下载完成后按系统提示安装",
                fontSize = 12.sp, lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // ★★2026-11-09 更新内容优先用 **App 内置日志**（对齐 Web 版 showUpdateModal）
            //
            //   Web 版的教训写得明明白白：直接渲染 GitHub Release 的 body，
            //   用户在更新弹窗里看到的是"门禁项数、构建细节、验证结果表格" ——
            //   那是写给 Release 页面看的技术文档，不是给人看的更新说明。
            //   内置日志（NativeChangelog）只写"用户看得见的变化"，而且离线也能显示。
            //   只有内置没有这个版本（比如推了个未来版本）才回退到 release.body。
            val builtinBody = NativeChangelog
                .firstOrNull { it.first.equals(release.tag, ignoreCase = true) }
                ?.second
            val bodyText = (builtinBody ?: release.body).trim()
            if (bodyText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f))
                        .padding(10.dp)
                ) {
                    Text(
                        bodyText.take(800),
                        fontSize = 11.sp, lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 下载进度（只在下载中出现）──
            val downloading = st as? com.xixi.hiking.nativeapp.data.ApkDownloader.State.Downloading
            if (downloading != null) {
                Spacer(Modifier.height(12.dp))
                val done = downloading.done
                val total = downloading.total
                // total <= 0：服务端没给 Content-Length（chunked），只能用已下载大小说话
                val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(com.xixi.hiking.nativeapp.ui.theme.AccentIndigo)
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (total > 0) {
                        "正在下载 ${(done * 100 / total).toInt()}% · " +
                            "${"%.1f".format(done / 1048576f)}/${"%.1f".format(total / 1048576f)} MB"
                    } else {
                        "正在下载 ${"%.1f".format(done / 1048576f)} MB"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            (st as? com.xixi.hiking.nativeapp.data.ApkDownloader.State.Failed)?.let { f ->
                Spacer(Modifier.height(8.dp))
                Text(f.reason, fontSize = 11.5.sp, color = DangerRed)
                // ★2026-11-11 失败不能是死路。
                //   镜像和官方直链都挂了的时候（或被公司网络拦了），
                //   用户手里还剩一条路：拿浏览器下。以前这里只有"重试"，
                //   重试还是同一个网络、同一个结果，等于把人卡死。
                Spacer(Modifier.height(6.dp))
                Text(
                    "改用浏览器下载",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = com.xixi.hiking.nativeapp.ui.theme.AccentIndigo,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            com.xixi.hiking.nativeapp.ui.theme.AccentIndigo.copy(alpha = 0.10f)
                        )
                        .safeClickable {
                            com.xixi.hiking.nativeapp.data.Updater.openDownload(
                                ctx, release.apkUrl
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            val busy = downloading != null ||
                st is com.xixi.hiking.nativeapp.data.ApkDownloader.State.Installing
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) {
                    GlassButton("稍后再说", onClick = onDismiss)
                }
                Box(Modifier.weight(1f)) {
                    val label = when {
                        downloading != null -> "下载中…"
                        st is com.xixi.hiking.nativeapp.data.ApkDownloader.State.Installing -> "正在安装…"
                        st is com.xixi.hiking.nativeapp.data.ApkDownloader.State.Failed -> "重试"
                        else -> "立即更新"
                    }
                    GlassButton(
                        label,
                        onClick = onStart,
                        primary = !busy,
                        enabled = !busy
                    )
                }
            }
        }
    }
}
